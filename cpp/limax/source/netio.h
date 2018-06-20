#pragma once

namespace limax {
	namespace helper {

		class OsSystemInit
		{
			OsSystemInit();
		public:
			void Startup();
			void Cleanup();
		public:
			static OsSystemInit& getInstance();
		};

	} // namespace helper {

	class TcpClient
	{
	public:
		struct Listener
		{
			virtual ~Listener() { }
			virtual void onCreate(TcpClient *) = 0;
			virtual void onOpen(const IPEndPoint& local, const IPEndPoint& peer) = 0;
			virtual void onAbort(const IPEndPoint& sa) = 0;
			virtual void onRecv(const void *data, int32_t size) = 0;
			virtual void onClose(int status) = 0;
		};
	private:
		struct WeakListener : public Listener
		{
			std::weak_ptr<Listener> forward;
			WeakListener(std::shared_ptr<Listener> _forward) : forward(_forward){}
			virtual void onCreate(TcpClient *c) override
			{
				std::shared_ptr<Listener> p(forward);
				if (p)
					p->onCreate(c);
			}
			virtual void onOpen(const IPEndPoint& local, const IPEndPoint& peer) override
			{
				std::shared_ptr<Listener> p(forward);
				if (p)
					p->onOpen(local, peer);
			}
			virtual void onAbort(const IPEndPoint& sa) override
			{
				std::shared_ptr<Listener> p(forward);
				if (p)
					p->onAbort(sa);
				delete this;
			}
			virtual void onRecv(const void *data, int32_t size) override
			{
				std::shared_ptr<Listener> p(forward);
				if (p)
					p->onRecv(data, size);
			}
			virtual void onClose(int status) override
			{
				std::shared_ptr<Listener> p(forward);
				if (p)
					p->onClose(status);
				delete this;
			}
		};
	public:
		TcpClient() {}
		virtual ~TcpClient() {}
		virtual void send(const void *, int32_t) = 0;
		virtual void send(const Octets&) = 0;
		virtual void recv() = 0;
		virtual void close() = 0;
		virtual void destroy() = 0;
		static void createAsync(Listener*, std::string, short);
		static void createAsync(Listener*, const IPEndPoint&);
		static void createSync(Listener*, std::string, short, int contimeout, int rwtimeout);
		static void createSync(Listener*, const IPEndPoint&, int contimeout, int rwtimeout);
		static Listener* createWeakListener(std::shared_ptr<Listener> listener)	{ return new WeakListener(listener); }
	};

} // namespace limax {

