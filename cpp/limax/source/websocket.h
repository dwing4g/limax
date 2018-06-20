#pragma once

namespace limax
{
	struct WebSocketConnector : public Closeable, public TcpClient::Listener {
		int const DHGroup = 2;
		enum CloseStatus { CONNECTION_FAIL, ACTIVE_CLOSED, PASSIVE_CLOSED };
		enum ReadyState { CONNECTING, OPEN, CLOSING, CLOSED };
		TcpClient* tcpclient;
		std::shared_ptr<helper::LoginConfigImpl> loginConfig;
		ReadyState readyState;
		Octets buffer;
		OctetsUnmarshalStreamSource datasrc;
		std::weak_ptr<WebSocketConnector> self;
		std::recursive_mutex mutex;
		DelayedRunnable timer;
		ScriptEngineHandlePtr handle;
		int reportError;
		int stage;
		std::string sbhead;
		IPEndPoint peer;
		std::shared_ptr<limax::DHContext> dhcontext;
		std::shared_ptr<Codec> isec;
		std::shared_ptr<Codec> osec;
		Octets ibuf;
		Octets obuf;

		WebSocketConnector(std::shared_ptr<helper::LoginConfigImpl> _loginConfig, ScriptEngineHandlePtr _handle);
		void process(int t, const std::string& p);
		void onopen();
		void onmessage(const std::string& message);
		void onclose(CloseStatus status);
		virtual void onCreate(TcpClient *tcpclient) override;
		virtual void onOpen(const IPEndPoint& local, const IPEndPoint& peer) override;
		virtual void onAbort(const IPEndPoint& sa) override;
		virtual void onRecv(const void *data, int32_t size) override;
		virtual void onClose(int status) override;
		void send(const char *p, size_t _len);
		void send(const std::string& utf8message);
		void _close();
		virtual void close() override;
	};
	
	std::shared_ptr<Closeable> createWebSocketConnector(const std::string& ip, short port, LoginConfigPtr _loginConfig, ScriptEngineHandlePtr handle); 

}
