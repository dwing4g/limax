#pragma once

namespace limax {
	class SinkTcpClient : public Codec
	{
		Octets buffer;
		TcpClient* tcpclient;
	public:
		SinkTcpClient(TcpClient* tcpclient);
		virtual void update(int8_t c) override;
		virtual void update(int8_t data[], int32_t off, int32_t len) override;
		virtual void flush() override;
	};

	class NetSession : public TcpClient::Listener
	{
	public:
		struct Listener
		{
			virtual ~Listener() { }
			virtual void onAddSession(NetSession* session, const IPEndPoint& local, const IPEndPoint& peer) = 0;
			virtual void onAbortSession(NetSession* session) = 0;
			virtual void onDelSession(NetSession* session) = 0;
			virtual void onProtocol(Protocol* p) = 0;
			virtual void onCheckUnknownProtocol(NetSession* session, Protocol::Type type, int size) = 0;
		};
	private:
		Listener* listener;
		Transport* transport;
		std::mutex	mutex;
		TcpClient* tcpclient;
		Octets inbuffer;
		std::shared_ptr<Codec>	is_codec;
		std::shared_ptr<Codec>	os_codec;
		OctetsUnmarshalStreamSource source;
		std::shared_ptr<State> state;
		NetSession(Listener *, Transport *);
		void destroy();
		Protocol* decodeProtocol();
	public:
		static void create(Listener* l, Transport* t, std::string ip, short port);
		void send(const Protocol* protocol);
		void send(const Protocol& protocol);
		void setInputSecurity(bool compress, const Octets& key = Octets());
		void setOutputSecurity(bool compress, const Octets& key = Octets());
		void setState(std::shared_ptr<State> state);
		void close();

		virtual void onCreate(TcpClient *tcpclient) override;
		virtual void onOpen(const IPEndPoint& local, const IPEndPoint& peer) override;
		virtual void onAbort(const IPEndPoint& sa) override;
		virtual void onRecv(const void *data, int32_t size) override;
		virtual void onClose(int status) override;
	};
}  // namespace limax {
