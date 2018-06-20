#include "endpointinc.h"
#include "httpclient.h"

#ifdef LIMAX_OS_WINDOWS
#include <ws2tcpip.h>
#endif

#ifdef LIMAX_OS_UNIX_FAMILY
#include <netdb.h>
#endif

namespace limax {
	namespace http {

#ifdef LIMAX_OS_WINDOWS

		int util::stringcompare(const char* a, const char* b)
		{
			return _stricmp(a, b);
		}

		int util::stringncompare(const char* a, const char* b, size_t s)
		{
			return _strnicmp(a, b, s);
		}

#endif // #ifdef LIMAX_OS_WINDOWS

#ifdef LIMAX_OS_UNIX_FAMILY
		int util::stringcompare(const char* a, const char* b)
		{
			return strcasecmp(a, b);
		}

		int util::stringncompare(const char* a, const char* b, size_t s)
		{
			return strncasecmp(a, b, s);
		}
#endif // #ifdef LIMAX_OS_UNIX_FAMILY

	} // namespace http {

	namespace http {

		class CallBackImpl : public HttpCallback
		{
			enum : size_t
			{
				BUFFER_BLOCK_SIZE = 1024 * 1024,
			};

			int				m_httpcode = 0;
			std::string		m_httpmsg;
			bool			m_done = false;
			bool			m_headerdone = false;
			Octets			m_content;
			HttpConnection::PropertyMap&	m_prorerties;
		public:
			CallBackImpl(HttpConnection::PropertyMap& ps)
				: m_prorerties(ps)
			{}
			virtual ~CallBackImpl() {}
		private:
			void appendContent(const char * data, size_t size)
			{
				const auto newsize = m_content.size() + size;
				const auto allsize = (newsize + BUFFER_BLOCK_SIZE - 1) / BUFFER_BLOCK_SIZE * BUFFER_BLOCK_SIZE;
				m_content.reserve(allsize);
				m_content.insert(m_content.end(), data, size);
			}
		public:
			virtual bool onHttpRequestLine(char* data, size_t size) override
			{
				std::string line(data, size);
				auto pos = line.find(' ');
				if (pos == std::string::npos)
					return false;
				line = line.substr(pos + 1);
				pos = line.find(' ');
				if (pos == std::string::npos)
					return false;
				auto codes = line.substr(0, pos);
				m_httpcode = atoi(codes.c_str());
				m_httpmsg = line.substr(pos + 1);
				return true;
			}
			virtual bool onHttpHeaderField(char* k, char* v) override
			{
				m_prorerties[k].push_back(v);
				return true;
			}
			virtual bool onHttpHeadersEnd() override
			{
				m_headerdone = true;
				return true;
			}
			virtual bool onHttpRequestEnd(char* data, size_t size) override
			{
				appendContent(data, size);
				m_done = true;
				return true;
			}
			virtual size_t onHttpContent(char* data, size_t size) override
			{
				appendContent(data, size);
				return size;
			}
		public:
			inline bool isHeaderDone() const
			{
				return m_headerdone;
			}
			inline bool isDone() const
			{
				return m_done;
			}

			inline bool hashContent() const
			{
				return m_content.size() > 0;
			}
			inline Octets popContent()
			{
				Octets result;
				result.swap(m_content);
				return result;
			}
			inline int getHttpResultCode() const
			{
				return m_httpcode;
			}
			inline const std::string& getHttpMessage() const
			{
				return m_httpmsg;
			}
		};

		struct ConnectionClosed {};
		struct ConnectionAbort {};

		class Connection : public TcpClient::Listener
		{
			TcpClient*	tcpclient;
			HttpBuffer* buffer;
			IPEndPoint	peer;
			int contimeout;
			int rwtimeout;
		private:
			Connection(const IPEndPoint& addr, int _contimeout, int _rwtimeout)
				: tcpclient(nullptr), peer(addr), contimeout(_contimeout), rwtimeout(_rwtimeout)
			{
			}
		public:
			virtual ~Connection()
			{
				if (tcpclient)
					tcpclient->destroy();
			}
		protected:
			virtual void onCreate(TcpClient* client) override
			{
				tcpclient = client;
			}
			virtual void onOpen(const IPEndPoint& local, const IPEndPoint& peer) override {}
			virtual void onAbort(const IPEndPoint& sa) override
			{
				tcpclient = nullptr;
				throw ConnectionAbort();
			}
			virtual void onRecv(const void *data, int32_t size) override
			{
				buffer->m_data.insert(buffer->m_data.end(), data, size);
			}
			virtual void onClose(int status) override
			{
				throw ConnectionClosed();
			}
		public:
			inline void reconnect()
			{
				tcpclient->destroy();
				TcpClient::createSync(this, peer, contimeout, rwtimeout);
			}
			inline void send(const char* d, size_t s)
			{
				tcpclient->send(d, (int32_t)s);
			}
			inline void recv(HttpBuffer& buffer)
			{
				this->buffer = &buffer;
				tcpclient->recv();
				this->buffer = nullptr;
			}
		public:
			inline static std::shared_ptr<Connection> create(const char* svr, const char* port, int contimeout, int rwtimeout)
			{
				struct addrinfo aiHints;
				memset(&aiHints, 0, sizeof(aiHints));
				aiHints.ai_family = AF_UNSPEC;
				aiHints.ai_socktype = SOCK_STREAM;
				aiHints.ai_protocol = IPPROTO_TCP;

				struct addrinfo *aiList = NULL;
				int errorcode = ::getaddrinfo(svr, port, &aiHints, &aiList);
				if (0 != errorcode)
				{
					if (Trace::isWarnEnabled())
					{
						std::ostringstream oss;
						oss << "api getaddrinfo return = " << errorcode;
						Trace::warn(oss.str());
					}
					return nullptr;
				}
				for (struct addrinfo* info = aiList; info != NULL; info = info->ai_next)
				{
					if ((info->ai_family == AF_INET && info->ai_addrlen != sizeof(struct sockaddr_in))
						|| (info->ai_family == AF_INET6 && info->ai_addrlen != sizeof(struct sockaddr_in6)))
						continue;
					try
					{
						IPEndPoint peer;
						memcpy(peer, info->ai_addr, info->ai_addrlen);
						std::shared_ptr<Connection> conn(new Connection(peer, contimeout, rwtimeout));
						TcpClient::createSync(TcpClient::createWeakListener(conn), peer, contimeout, rwtimeout);
						freeaddrinfo(aiList);
						return conn;
					}
					catch (ConnectionAbort&)
					{
					}
				}
				freeaddrinfo(aiList);
				return nullptr;
			}
		};
        
        struct ConnectionPool
        {
            ConnectionPool() {}
            virtual ~ConnectionPool() {}
            virtual const std::string& getServer() const = 0;
            virtual std::shared_ptr<Connection> poll() = 0;
            virtual void offer(const std::shared_ptr<Connection>&) = 0;
        };

        class ConnectionPoolCached : public ConnectionPool
		{
			std::string server;
			std::string port;
			int contimeout;
			int rwtimeout;
			std::mutex locker;
			std::list< std::shared_ptr<Connection> > cons;
		public:
			inline ConnectionPoolCached(const std::string& _server, const std::string& _port, int _contimeout, int _rwtimeout)
				: server(_server), port(_port), contimeout(_contimeout), rwtimeout(_rwtimeout)
			{
				helper::OsSystemInit::getInstance().Startup();
			}
			virtual ~ConnectionPoolCached()
			{
				{
					std::lock_guard<std::mutex> _locker_(locker);
					cons.clear();
				}
				helper::OsSystemInit::getInstance().Cleanup();
			}
		public:
			virtual const std::string& getServer() const override
			{
				return server;
			}
			virtual std::shared_ptr<Connection> poll() override
			{
				{
					std::lock_guard<std::mutex> _locker_(locker);
					if (!cons.empty())
					{
						auto con = cons.back();
						cons.pop_back();
						return con;
					}
				}
				return Connection::create(server.c_str(), port.c_str(), contimeout, rwtimeout);
			}
/*			inline std::shared_ptr<Connection> poll(std::shared_ptr<Connection> con)
			{
				if (con)
				{
					try
					{
						con->reconnect();
						return con;
					}
					catch (ConnectionAbort&)
					{
					}
				}
				return poll();
			}
 */
			virtual void offer(const std::shared_ptr<Connection>& con) override
			{
				if (con)
				{
					std::lock_guard<std::mutex> _locker_(locker);
					cons.push_back(con);
				}
			}
		};
        
        class ConnectionPoolDirect : public ConnectionPool
        {
            std::string server;
            std::string port;
            int contimeout;
            int rwtimeout;
        public:
            inline ConnectionPoolDirect(const std::string& _server, const std::string& _port, int _contimeout, int _rwtimeout)
                : server(_server), port(_port), contimeout(_contimeout), rwtimeout(_rwtimeout)
            {
                helper::OsSystemInit::getInstance().Startup();
            }
            virtual ~ConnectionPoolDirect()
            {
                helper::OsSystemInit::getInstance().Cleanup();
            }
        public:
            virtual const std::string& getServer() const override
            {
                return server;
            }
            virtual std::shared_ptr<Connection> poll() override
            {
                return Connection::create(server.c_str(), port.c_str(), contimeout, rwtimeout);
            }
            virtual void offer(const std::shared_ptr<Connection>&) override {}
        };
        
        
        class HttpConnectionImpl : public HttpConnection
        {
            class IOData
			{
				CallBackImpl callback;
				HttpParser	parser;
				HttpBuffer	buffer;
				std::shared_ptr<Connection> con;
				bool		disconnected = false;
			public:
				IOData(PropertyMap& map, std::shared_ptr<Connection> _con)
					:callback(map), con(_con)
				{}
				~IOData() {}
			public:
				inline void input()
				{
					try
					{
						con->recv(buffer);
						parser.parse(buffer, callback);
					}
					catch (ConnectionClosed&)
					{
						disconnected = true;
					}
				}
				inline bool isHeaderDone() const
				{
					return callback.isHeaderDone();
				}
				inline bool isDone() const
				{
					return callback.isDone();
				}
				inline bool isDisconnected() const
				{
					return disconnected;
				}
				inline bool hasContent() const
				{
					return callback.hashContent();
				}
				inline Octets popContent()
				{
					return callback.popContent();
				}
				inline std::shared_ptr<Connection>& getConnection()
				{
					return con;
				}
				inline int getResponseCode() const
				{
					return callback.getHttpResultCode();
				}
				inline const std::string& getResponseMessage() const
				{
					return callback.getHttpMessage();
				}
			};
			std::string m_fileurl;
			std::shared_ptr<ConnectionPool> cpool;
			std::string method = "GET";
			Octets outputdata;
			PropertyMap requestproperties;
			PropertyMap responseproperties;
			int responsecode = 0;
			std::string responsemsg;
			static const std::string emptystring;
			GetContentFuncType getcontent;
		public:
			inline HttpConnectionImpl(std::shared_ptr<ConnectionPool> cp, const std::string& url)
				: m_fileurl(url), cpool(cp), getcontent([](){ return Octets(); })
			{
				requestproperties["Host"].push_back(cpool->getServer());
				requestproperties["Agent"].push_back("limax.org/httpclient");
				requestproperties["Connection"].push_back("Keep-Alive");
			}
			inline ~HttpConnectionImpl() {}
		public:
			virtual void setRequestMethod(const std::string& m) override
			{
				method = m;
			}
			virtual const std::string& getRequestMethod() const override
			{
				return method;
			}
			virtual void addRequestProperty(const std::string& k, const std::string& v) override
			{
				requestproperties[k].push_back(v);
			}
			virtual void setRequestProperty(const std::string& k, const std::string& v) override
			{
				auto& ps = requestproperties[k];
				ps.clear();
				ps.push_back(v);
			}
			virtual void setOutputData(const void* data, size_t size) override
			{
				outputdata.replace(data, size);
			}
			virtual const std::string& getRequestProperty(const std::string& k) const override
			{
				auto it = requestproperties.find(k);
				if (it == requestproperties.end())
					return emptystring;
				const auto& vs = it->second;
				if (vs.empty())
					return emptystring;
				return vs.front();
			}
			virtual const PropertyMap& getRequestProperties() const override
			{
				return requestproperties;
			}
			virtual bool doRequest() override
			{
				std::string	request;
				std::shared_ptr<Connection> con;
				while (true)
				{
					try
					{
						con = cpool->poll();
						if (!con)
							return false;
						if (request.empty())
							makeRequestString(request);
						con->send(request.c_str(), request.size());
						break;
					}
					catch (ConnectionClosed&)
					{
						continue;
					}
				}

				auto data = std::make_shared<IOData>(responseproperties, con);
				while (!data->isDisconnected())
				{
					data->input();
					if (data->isHeaderDone())
					{
						responsecode = data->getResponseCode();
						responsemsg = data->getResponseMessage();
						break;
					}
				}
				if (data->isDisconnected())
				{
					if (!data->isHeaderDone())
						return false;
					getcontent = [data]()
					{
						return data->popContent();
					};
					return true;
				}
				else
				{
					auto& cp = cpool;
					getcontent = [data, cp]()
					{
						if (data->hasContent())
							return data->popContent();
						if (data->isDisconnected())
							return Octets();
						if (data->isDone())
						{
							cp->offer(data->getConnection());
							return Octets();
						}
						data->input();
						return data->popContent();
					};
				}
				return true;
			}
			virtual int getResponseCode() const override
			{
				return responsecode;
			}
			virtual const std::string& getResponseMessage() const override
			{
				return responsemsg;
			}
			virtual GetContentFuncType getContent() const override
			{
				return getcontent;
			}
			virtual size_t getContentLength() const override
			{
				return std::stoul(getHeaderField("Content-Length"));
			}
			virtual const std::string& getContentType() const override
			{
				return getHeaderField("Content-Type");
			}
			virtual	const std::string& getHeaderField(const std::string& k) const override
			{
				auto it = responseproperties.find(k);
				if (it == responseproperties.end())
					return emptystring;
				const auto& vs = it->second;
				if (vs.empty())
					return emptystring;
				return vs.front();
			}
			virtual const PropertyMap& getHeaderFields() const override
			{
				return responseproperties;
			}
		private:
			void makeRequestString(std::string& request) const
			{
				std::stringstream ss;
				ss << method << " " << m_fileurl << " HTTP/1.1\r\n";
				for (const auto& itrp : requestproperties)
				{
					const auto& k = itrp.first;
					for (const auto& v : itrp.second)
						ss << k << ": " << v << "\r\n";
				}
                if (outputdata.size() > 0)
                {
                    ss << "Content-Type: text/plain\r\n";
                    ss << "Content-Length: " << outputdata.size() << "\r\n";
                }
				ss << "\r\n";
				if (outputdata.size() > 0)
				{
					ss << std::string((const char*)outputdata.begin(), outputdata.size());
					ss << "\r\n";
				}
				request = ss.str();
			}
		};

		const std::string HttpConnectionImpl::emptystring;

	} // namespace http {

	namespace http {

		bool ParseUrl(const std::string& src, std::string& server, std::string& port, std::string& path)
		{
			const char* serversplit = "://";
			const std::string::size_type sizeserversplit = 3;

			const std::string::size_type serverstartpos = src.find(serversplit);
			if (std::string::npos == serverstartpos)
				return false;
			const std::string servertype = src.substr(0, serverstartpos);
			if (0 == util::stringcompare(servertype.c_str(), "http"))
				port = "80";
			else
				return false;

			const std::string::size_type serverendpos = src.find('/', serverstartpos + 3);
			if (std::string::npos == serverendpos)
				path = "/";
			else
				path = src.substr(serverendpos);
			server = src.substr(sizeserversplit + serverstartpos, serverendpos - serverstartpos - sizeserversplit);

			const std::string::size_type ppos = server.find(':');
			if (std::string::npos != ppos)
			{
				port = server.substr(ppos + 1);
				server = server.substr(0, ppos);
			}
			return true;
		}

        class ConnectionPoolMap
		{
			struct Key
			{
				std::string server;
				std::string port;
				bool operator<(const Key& dst) const
				{
					int v = server.compare(dst.server);
					if (0 == v)
						return port < dst.port;
					else
						return v < 0;
				}
			};
            std::function<std::shared_ptr<ConnectionPool>(const std::string&,const std::string&,int,int)> createpool;
			std::mutex locker;
			std::map<Key, std::shared_ptr<ConnectionPool> > map;
		public:
			ConnectionPoolMap(bool cached)
            {
                if(cached)
                    createpool =[](const std::string& server, const std::string& port, int contimeout, int rwtimeout){ return std::make_shared<ConnectionPoolCached>(server, port, contimeout, rwtimeout); };
                else
                    createpool =[](const std::string& server, const std::string& port, int contimeout, int rwtimeout){ return std::make_shared<ConnectionPoolDirect>(server, port, contimeout, rwtimeout); };
            }
            ~ConnectionPoolMap()
			{
				std::lock_guard<std::mutex> _locker_(locker);
				map.clear();
			}
		public:
			std::shared_ptr<ConnectionPool> getPool(const std::string& server, const std::string& port, int contimeout, int rwtimeout)
			{
				Key k;
				k.server = server;
				k.port = port;
				std::lock_guard<std::mutex> _locker_(locker);
				auto it = map.find(k);
				if (it != map.end())
					return it->second;
                std::shared_ptr<ConnectionPool> pool = createpool(server, port, contimeout, rwtimeout);
				map.insert(std::make_pair(k, pool));
				return pool;
			}
		};
       
        ConnectionFactoryImpl::ConnectionFactoryImpl(int _contimeout, int _rwtimeout, bool _cached)
			: map(new ConnectionPoolMap(_cached)), contimeout(_contimeout), rwtimeout(_rwtimeout)
		{}
		ConnectionFactoryImpl::~ConnectionFactoryImpl()
		{
			delete map;
		}
		std::shared_ptr<HttpConnection> ConnectionFactoryImpl::create(const std::string& url)
		{
			std::string server;
			std::string port;
			std::string path;
			if (!ParseUrl(url, server, port, path))
				return nullptr;
			auto pool = map->getPool(server, port, contimeout, rwtimeout);
			return std::make_shared<HttpConnectionImpl>(pool, path);
		}

		ProxyConnectionFactoryImpl::ProxyConnectionFactoryImpl(const std::string& s, short p, int contimeout, int rwtimeout, bool cached)
		{
            if(cached)
                cpool = std::make_shared<ConnectionPoolCached>(s, std::to_string(p), contimeout, rwtimeout);
            else
                cpool = std::make_shared<ConnectionPoolDirect>(s, std::to_string(p), contimeout, rwtimeout);
		}
		ProxyConnectionFactoryImpl::~ProxyConnectionFactoryImpl() {}
		std::shared_ptr<HttpConnection> ProxyConnectionFactoryImpl::create(const std::string& url)
		{
			return std::make_shared<HttpConnectionImpl>(cpool, url);
		}

	} // namespace http {
} // namespace limax {
