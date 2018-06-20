#include "common.h"

namespace limax {
	namespace helper {

		OsSystemInit& OsSystemInit::getInstance()
		{
			static OsSystemInit instance;
			return instance;
		}

		OsSystemInit::OsSystemInit()
		{
#ifdef LIMAX_VS_DEBUG_MEMORY_LEAKS_DETECT
			_CrtSetDbgFlag(_CRTDBG_ALLOC_MEM_DF | _CRTDBG_LEAK_CHECK_DF);
#endif
		}

#ifdef LIMAX_OS_UNIX_FAMILY
		void OsSystemInit::Startup() {}
		void OsSystemInit::Cleanup() {}

		typedef int SOCKET;
		inline void closesocket(int s)
		{
			::shutdown(s, SHUT_RDWR);
			::close(s);
		}

		inline int WSAGetLastError()
		{
			return errno;
		}

		enum : int
		{
			WSAEISCONN = EISCONN,
			WSAEWOULDBLOCK = EINPROGRESS,
			WSAEALREADY = EALREADY,
		};
#endif

#ifdef LIMAX_OS_WINDOWS
		void OsSystemInit::Startup()
		{
			WSADATA data;
			WSAStartup(MAKEWORD(2, 0), &data);
		}
		void OsSystemInit::Cleanup()
		{
			::WSACleanup();
		}

		typedef int32_t socklen_t;
#endif
		class AbstractTcpClient : public TcpClient
		{
		protected:
			SOCKET sock;
			IPEndPoint local, peer;
			Listener* listener;
			std::atomic<bool> closed;
			std::atomic<bool> close_notified;
		public:
			AbstractTcpClient(Listener* _listener, const IPEndPoint& _peer)
				: sock(::socket(_peer.getFamily(), SOCK_STREAM, IPPROTO_TCP)), peer(_peer), listener(_listener), closed(false), close_notified(false)
			{
				int optval = 1;
				::setsockopt(sock, SOL_SOCKET, SO_KEEPALIVE, (const char *)&optval, sizeof(optval));
				::setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, (const char *)&optval, sizeof(optval));
				listener->onCreate(this);
			}
			virtual ~AbstractTcpClient() {}
		protected:
			void _close()
			{
				close();
				if (close_notified.exchange(true))
					return;
				listener->onClose(WSAGetLastError());
			}
			void _connected()
			{
				if (closed.load())
					return;
				listener->onOpen(local, peer);
			}
			void _abort()
			{
				close();
				if (close_notified.exchange(true))
					return;
				listener->onAbort(peer);
			}
		};


		class AsyncTcpClient : public AbstractTcpClient
		{
			BlockingQueue<Octets> sq;
			JoinableRunnable r_task;
			JoinableRunnable w_task;
		public:
			virtual ~AsyncTcpClient() {}
			AsyncTcpClient(Listener* _listener, const IPEndPoint& _peer)
				: AbstractTcpClient(_listener, _peer),
				r_task([this]()
			{
				if (::connect(sock, peer, (socklen_t)peer.size()))
				{
					_abort();
					return;
				}
				socklen_t len = sizeof(local);
				::getsockname(sock, local, &len);
				_connected();
				char buff[8192];
				while (true)
				{
					int32_t nrecv = (int32_t)::recv(sock, buff, sizeof(buff), 0);
					if (nrecv <= 0)
					{
						_close();
						return;
					}
					listener->onRecv(buff, nrecv);
				}
			}),
				w_task([this]()
			{
				while (true)
				{
					Octets data = sq.get();
					int32_t l = (int32_t)data.size();
					if (l == 0)
						return;
					const char *p = (const char *)data.begin();
					while (true)
					{
						int32_t nsend = (int32_t)::send(sock, p, l, 0);
						if (nsend <= 0)
						{
							_close();
							return;
						}
						if (nsend == l)
							break;
						p += nsend;
						l -= nsend;
					}
				}
			})
			{
				Engine::execute(r_task);
				Engine::execute(w_task);
			}
		public:
			virtual void send(const void* data, int32_t size) override
			{
				send(Octets(data, size));
			}
			virtual void send(const Octets& o) override
			{
				sq.put(o);
			}
			virtual void recv() override {}
			virtual void close() override
			{
				if (closed.exchange(true))
					return;
				closesocket(sock);
				send(Octets());
			}
			virtual void destroy() override
			{
				close();
				r_task.join();
				w_task.join();
				delete this;
			}
		};

		class SyncTcpClient : public AbstractTcpClient
		{
		public:
			virtual ~SyncTcpClient() {}
			SyncTcpClient(Listener* _listener, const IPEndPoint& _peer, int contimeout, int rwtimeout)
				: AbstractTcpClient(_listener, _peer)
			{
#ifdef LIMAX_OS_WINDOWS
				setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, (const char*)&rwtimeout, sizeof(rwtimeout));
				setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, (const char*)&rwtimeout, sizeof(rwtimeout));
#else
				{
					struct timeval timeout;
					timeout.tv_sec = rwtimeout / 1000;
					timeout.tv_usec = rwtimeout % 1000 * 1000;
					setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, (const char*)&timeout, sizeof(timeout));
					setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, (const char*)&timeout, sizeof(timeout));
				}
#endif
				if (timeoutConnect(contimeout))
				{
					socklen_t len = sizeof(local);
					::getsockname(sock, local, &len);
					_connected();
				}
				else
				{
					_abort();
				}
			}
		private:
			bool timeoutConnect(int contimeout)
			{
#ifdef LIMAX_OS_WINDOWS
				{
					u_long v = 1;
					ioctlsocket(sock, FIONBIO, &v);
				}
#else
				{
					fcntl(sock, F_SETFL, fcntl(sock, F_GETFL) | O_NONBLOCK);
				}
#endif
				auto start = std::chrono::steady_clock::now();
				while (true)
				{
					if (0 == connect(sock, peer, (socklen_t)peer.size()))
						break;
					const auto ec = WSAGetLastError();
					if (ec == WSAEISCONN)
						break;
					if (ec != WSAEWOULDBLOCK && ec != WSAEALREADY)
						return false;
					auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start);
					if (elapsed.count() >= contimeout)
						return false;
					const auto left = contimeout - elapsed.count();
					struct timeval timeout;
					fd_set myset;
					timeout.tv_sec = (decltype(timeout.tv_sec))(left / 1000);
					timeout.tv_usec = (decltype(timeout.tv_usec))(left % 1000 * 1000);
					FD_ZERO(&myset);
					FD_SET(sock, &myset);
					select((int)sock + 1, NULL, &myset, NULL, &timeout);
				}
#ifdef LIMAX_OS_WINDOWS
				{
					u_long v = 0;
					ioctlsocket(sock, FIONBIO, &v);
				}
#else
				{
					fcntl(sock, F_SETFL, fcntl(sock, F_GETFL) & ~O_NONBLOCK);
				}
#endif
				return true;
			}

		public:
			virtual void send(const void* data, int32_t size) override
			{
				if (closed.load())
					return;
				const char *p = (const char *)data;
				while (true)
				{
					int32_t nsend = (int32_t)::send(sock, p, size, 0);
					if (nsend <= 0)
					{
						_close();
						return;
					}
					if (nsend == size)
						break;
					p += nsend;
					size -= nsend;
				}
			}
			virtual void send(const Octets& o) override
			{
				send(o.begin(), (int32_t)o.size());
			}
			virtual void recv() override
			{
				if (closed.load())
					return;
				char buff[8192];
				int32_t nrecv = (int32_t)::recv(sock, buff, sizeof(buff), 0);
				if (nrecv <= 0)
				{
					_close();
					return;
				}
				listener->onRecv(buff, nrecv);
			}
			virtual void close() override
			{
				if (closed.exchange(true))
					return;
				closesocket(sock);
			}
			virtual void destroy() override
			{
				close();
				delete this;
			}
		};

	} // namespace helper {

	void TcpClient::createAsync(Listener* l, std::string ip, short port)
	{
		createAsync(l, IPEndPoint(ip, port));
	}
	void TcpClient::createAsync(Listener* l, const IPEndPoint& peer)
	{
		new helper::AsyncTcpClient(l, peer);
	}
	void TcpClient::createSync(Listener* l, std::string ip, short port, int contimeout, int rwtimeout)
	{
		createSync(l, IPEndPoint(ip, port), contimeout, rwtimeout);
	}
	void TcpClient::createSync(Listener* l, const IPEndPoint& peer, int contimeout, int rwtimeout)
	{
		new helper::SyncTcpClient(l, peer, contimeout, rwtimeout);
	}

} // namespace limax {

