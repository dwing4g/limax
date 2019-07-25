#include "endpointinc.h"

#include "dh.h"
#include "xmlgeninc/xmlgen.h"
#include "erroroccured.h"
#include "variantdef.h"
#include "viewcontextimpl.h"
#include "scriptexchange.h"
#include "endpointhelper.h"
#include "websocket.h"
#include "auanyservice.h"

namespace limax {

	namespace helper {
		static std::atomic<EndpointManager*> g_defaultEndpointManager;
	}

	class EndpointImpl : public EndpointManager, public Transport, public NetSession::Listener
	{
		std::shared_ptr<EndpointConfig>  m_config;
		std::shared_ptr<EndpointListener> m_listener;
		NetSession*	m_session = nullptr;
		std::recursive_mutex	m_session_mutex;
		int64_t						m_sessionid = -1;
		int64_t						m_accountFlags = 0;
		IPEndPoint					m_localaddress;
		IPEndPoint					m_peeraddress;
		void*	m_object = nullptr;
		hashmap<int32_t, int8_t>	m_pvids;
		std::shared_ptr<limax::DHContext>	dhcontext;
		Dispatcher dispatcher;
		DelayedRunnable ping_timeout_runnable;
		DelayedRunnable ping_alive_delay_runnable;
		bool ping_cancelled = false;
		volatile bool timeout = false;
		helper::ViewContextMap	m_viewContextMap;
		helper::ScriptExchange	m_scriptexchange;
		volatile enum LoginStatus
		{
			LOGINING, LOGINED_NOTIFY, LOGINED_DONE,
		} loginstatus = LoginStatus::LOGINING;
	private:
		void startPingAndKeepAlive()
		{
			sendProtocol(limax::endpoint::switcherendpoint::PingAndKeepAlive(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - std::chrono::steady_clock::time_point::min()).count()));
			dispatcher([this](){
				Engine::execute(ping_timeout_runnable = DelayedRunnable([this](){
					timeout = true;
					close();
				}, 500, 10));
			});
		}
		void startConnect()
		{
			NetSession::create(this, this, m_config->getServerIP(), m_config->getServerPort());
		}
		void executeSessionTask(std::function<void(NetSession*&)> r)
		{
			std::lock_guard<std::recursive_mutex> l(m_session_mutex);
			if (m_session)
				r(m_session);
		}
		void createSessionTask(std::function<void(NetSession*&)> r)
		{
			std::lock_guard<std::recursive_mutex> l(m_session_mutex);
			r(m_session);
		}
		void onAddSession(NetSession* _session, const IPEndPoint& local, const IPEndPoint& peer) override
		{
			createSessionTask([=](NetSession*& session){
				session = _session;
				session->setState(getEndpointStateEndpointClient(0));
				session->setInputSecurity(false);
				session->setOutputSecurity(false);
				m_localaddress = local;
				m_peeraddress = peer;
				dispatcher([this](){m_listener->onSocketConnected(); });
				if (m_config->isPingServerOnly())
				{
					startPingAndKeepAlive();
				}
				else
				{
					const int dh_group = m_config->getDHGroup();
					dhcontext = createDHContext(dh_group);
					const std::vector<unsigned char>& data = dhcontext->generateDHResponse();
					session->send(limax::endpoint::switcherendpoint::CHandShake(dh_group, Octets(&data[0], data.size())));
				}
			});
		}
		void onAbortSession(NetSession* session) override
		{
			dispatcher([this]()
			{
				m_listener->onAbort(this);
				m_listener->onManagerUninitialized(this);
			});
			dispatcher.await();
			Engine::remove(this);
		}
		void onDelSession(NetSession* session) override
		{
			executeSessionTask([](NetSession*& session){ session = nullptr; });
			dispatcher([this](){
				ping_cancelled = true;
				ping_timeout_runnable.cancel();
				ping_alive_delay_runnable.cancel();
			});
			if (timeout)
				pushErrorOccured(SOURCE_ENDPOINT, ENDPOINT_PING_TIMEOUT, "ping server time out");
			dispatcher.await();
			dispatcher([this]()
			{
				if (LoginStatus::LOGINING == loginstatus)
				{
					if (!m_config->isPingServerOnly())
						m_listener->onAbort(this);
				}
				else
				{
					if (LoginStatus::LOGINED_DONE == loginstatus)
					{
						m_listener->onTransportRemoved(this);
					}
					if (LoginStatus::LOGINED_DONE == loginstatus || LoginStatus::LOGINED_NOTIFY == loginstatus) {
						EndpointManager* tmp = this;
						helper::g_defaultEndpointManager.compare_exchange_weak(tmp, nullptr);
						m_viewContextMap.clear();
						m_scriptexchange.onUnload();
					}
				}
				m_listener->onManagerUninitialized(this);
				Engine::execute([this](){
					dispatcher.await();
					ping_timeout_runnable.join();
					ping_alive_delay_runnable.join();
					Engine::remove(this);
				});
			});
		}
		void onProtocol(Protocol* p) override
		{
			if (Trace::isInfoEnabled())
			{
				auto rt = std::chrono::high_resolution_clock::now();
				dispatcher([p, rt](){
					auto dt = std::chrono::high_resolution_clock::now() - rt;
					auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(dt).count();
					std::ostringstream oss;
					oss << "endpoint protocol process delay type = " << p->getType() << "  = " << elapsed;
					Trace::info(oss.str());
					p->process();
					p->destroy();
				});
			}
			else
			{
				dispatcher([p](){
					p->process();
					p->destroy();
				});
			}
		}
		void onCheckUnknownProtocol(NetSession* session, Protocol::Type type, int size) override
		{
			std::stringstream ss;
			ss << "type = " << type << " size = " << size;
			pushErrorOccured(SOURCE_ENDPOINT, SYSTEM_ENDPOINT_RECV_UNKNOWN_PROTOCOL, ss.str());
			throw Protocol::Exception();
		}
		void createStaticViewContexts()
		{
			auto pvid = AuanyService::providerId;
			auto vc = helper::StaticViewContextImpl::create(this, pvid, getAuanyviewsViewCreatorManager(pvid));
			m_viewContextMap.put(ViewContext::Static, pvid, vc);
			for (auto& vcm : m_config->getStaticViewCreatorManagers())
			{
				auto pvid = vcm->getProviderId();
				auto vc = helper::StaticViewContextImpl::create(this, pvid, vcm);
				m_viewContextMap.put(ViewContext::Static, pvid, vc);
			}
		}
		void createVariantViewContexts(const hashmap<int32_t, limax::defines::VariantDefines>& vdmap)
		{
			for (auto& it : vdmap)
			{
				auto pvid = it.first;
				const auto& vd = it.second;
				auto vc = helper::VariantViewContextImpl::create(this, pvid, vd);
				if (vc)
					m_viewContextMap.put(ViewContext::Variant, pvid, vc);
				else
					close();
			}
		}
		void createScriptViewContexts(const std::string& defines)
		{
			m_scriptexchange.onLoad(this, m_config->getScriptEngineHandle(), defines);
		}
	public:
		EndpointImpl(std::shared_ptr<EndpointConfig> config, const hashmap<int32_t, int8_t>& pvids, EndpointListener* listener)
			: m_config(config), m_listener(std::shared_ptr<EndpointListener>(listener, [](EndpointListener *listener){ listener->destroy(); }))
			, m_pvids(pvids), dispatcher(config->getExecutor())
		{
			Engine::add(this);
			dispatcher([this](){ m_listener->onManagerInitialized(this, m_config.get()); });
			if (std::shared_ptr<TunnelSupport> ts = std::dynamic_pointer_cast<TunnelSupport>(m_listener)){
				ts->registerTunnelSender([this](int providerid, int label, Octets data){
					sendProtocol(limax::endpoint::providerendpoint::Tunnel(providerid, 0, label, data));
				});
			}
			startConnect();
		}
		void close() override
		{
			executeSessionTask([](NetSession*& session) { session->close(); });
		}
		virtual int64_t getSessionId() const override{ return m_sessionid; }
		virtual int64_t getAccountFlags() const override{ return m_accountFlags; }
		virtual void sendProtocol(const limax::Protocol& p) override
		{
			executeSessionTask([&p](NetSession*& session) { session->send(&p); });
		}
		virtual ViewContext* getViewContext(int32_t pvid, ViewContext::Type type) const override
		{
			return m_viewContextMap.getViewContext(pvid, type).get();
		}
		void unmarshalViewException(int32_t pvid, int16_t classIndex, int32_t instanceIndex)
		{
			std::stringstream ss;
			ss << "providerId = " << pvid << " classIndex = " << classIndex << " instanceIndex = " << instanceIndex;
			fireErrorOccured(SOURCE_ENDPOINT, SYSTEM_VIEW_MARSHAL_EXCEPTION, ss.str());
			close();
		}
		void fireErrorOccured(int source, int code, const std::string& info)
		{
			m_listener->onErrorOccured(source, code, info);
		}
		void pushErrorOccured(int source, int code, const std::string& info)
		{
			dispatcher([=](){ fireErrorOccured(source, code, info); });
		}
		void onProtocolProviderLogin(limax::endpoint::switcherendpoint::ProviderLogin* p)
		{
			if (std::shared_ptr<ProviderLoginDataManager> pldm = std::dynamic_pointer_cast<helper::LoginConfigImpl>(m_config->getLoginConfig())->getProviderLoginDataManager())
				if (std::shared_ptr<helper::ProviderLoginDataManagerImpl> _pldm = std::dynamic_pointer_cast<helper::ProviderLoginDataManagerImpl>(pldm))
				{
					defines::ProviderLoginData logindata = _pldm->get(p->data.pvid);
					if (logindata.type != defines::ProviderLoginData::tUnused)
					{
						sendProtocol(limax::endpoint::switcherendpoint::ProviderLogin(logindata));
						return;
					}
				}
			sendProtocol(limax::endpoint::switcherendpoint::ProviderLogin(p->data));
		}
		void onProtocolOnlineAnnounce(limax::endpoint::switcherendpoint::OnlineAnnounce* p)
		{
			if (SOURCE_LIMAX == p->errorSource && SUCCEED == p->errorCode)
			{
				m_accountFlags = p->flags;
				m_sessionid = p->sessionid;
				createStaticViewContexts();
				createVariantViewContexts(p->variantdefines);
				createScriptViewContexts(p->scriptdefines);
				loginstatus = LoginStatus::LOGINED_NOTIFY;
				EndpointManager*tmp = nullptr;
				helper::g_defaultEndpointManager.compare_exchange_weak(tmp, this);
				if (p->lmkdata.size())
					if (LmkUpdater lmkUpdater = std::dynamic_pointer_cast<helper::LoginConfigImpl>(m_config->getLoginConfig())->getLmkUpdater())
						lmkUpdater(p->lmkdata, [this](){sendProtocol(limax::endpoint::providerendpoint::Tunnel(AuanyService::providerId, 0, -1, Octets())); });
				m_listener->onTransportAdded(this);
				loginstatus = LoginStatus::LOGINED_DONE;
				startPingAndKeepAlive();
			}
			else
			{
				fireErrorOccured(p->errorSource, p->errorCode, "switcherendpoint::OnlineAnnounce");
			}
		}
		void onProtocolSKeyExchange(limax::endpoint::switcherendpoint::SHandShake* p)
		{
			executeSessionTask([this, p](NetSession *&session) {
				const std::vector<unsigned char> material = dhcontext->computeDHKey((unsigned char*)p->dh_data.begin(), (int32_t)p->dh_data.size());
				socklen_t key_len;
				int8_t *key = (int8_t*)m_peeraddress.getAddress(key_len);
				int32_t half = (int32_t)material.size() / 2;
				{
					HmacMD5 hmac(key, 0, key_len);
					hmac.update((int8_t*)&material[0], 0, half);
					session->setOutputSecurity(p->c2sneedcompress, Octets(hmac.digest(), 16));
				}
				{
					HmacMD5 hmac(key, 0, key_len);
					hmac.update((int8_t*)&material[0], half, (int32_t)material.size() - half);
					session->setInputSecurity(p->s2cneedcompress, Octets(hmac.digest(), 16));
				}
				dispatcher([this](){m_listener->onKeyExchangeDone(); });
				dhcontext.reset();
				limax::endpoint::switcherendpoint::SessionLoginByToken protocol;
				std::shared_ptr<helper::LoginConfigImpl> loginConfig = std::dynamic_pointer_cast<helper::LoginConfigImpl>(m_config->getLoginConfig());
				protocol.username = loginConfig->getUsername();
				protocol.token = loginConfig->getToken(Octets(&material[0], material.size()));
				protocol.platflag = loginConfig->getPlatflag();
				ScriptEngineHandlePtr seh = m_config->getScriptEngineHandle();
				if (seh)
				{
					std::string join;
					for (auto s : seh->getDictionaryCache()->keys())
					{
						join.push_back(',');
						join.append(s);
					}
					if (join.size())
					{
						join[0] = ';';
						protocol.platflag.append(join);
					}
				}
				socklen_t localaddress_len;
				const void *localaddress = m_localaddress.getAddress(localaddress_len);
				protocol.report_ip.insert(protocol.report_ip.begin(), localaddress, localaddress_len);
				protocol.report_port = m_localaddress.getPort();
				protocol.pvids.insert(m_pvids.begin(), m_pvids.end());
				session->send(&protocol);
				session->setState(m_config->getEndpointState());
			});
		}
		void onProtocolPingAndKeepAlive(limax::endpoint::switcherendpoint::PingAndKeepAlive* p)
		{
			if (ping_cancelled)
				return;
			ping_timeout_runnable.cancel();
			m_listener->onKeepAlived((int)(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - std::chrono::steady_clock::time_point::min()).count() - p->timestamp));
			if (!m_config->isPingServerOnly())
				Engine::execute(ping_alive_delay_runnable = DelayedRunnable([this](){startPingAndKeepAlive(); }, 500, 100));
		}
		void onProtocolSessionKick(limax::endpoint::switcherendpoint::SessionKick* p)
		{
			fireErrorOccured(SOURCE_LIMAX, p->error, "switcherendpoint::SessionKick");
			m_scriptexchange.onClose(p->error);
		}
		void onProtocolSyncViewToClients(limax::endpoint::providerendpoint::SyncViewToClients* protocol)
		{
			m_viewContextMap.onSyncViewToClients(protocol);
			m_scriptexchange.onSyncViewToClients(protocol);
		}
		void onProtocolTunnel(limax::endpoint::providerendpoint::Tunnel* protocol)
		{
			auto providerid = protocol->providerid;
			auto label = protocol->label;
			auto data = protocol->data;
			if (std::shared_ptr<TunnelSupport> ts = std::dynamic_pointer_cast<TunnelSupport>(m_listener))
				ts->onTunnel(providerid, label, data);
			m_scriptexchange.onTunnel(providerid, label, data);
		}
		EndpointManager* getManager() override { return this; }
		const IPEndPoint& getPeerAddress() const override { return m_peeraddress; }
		const IPEndPoint& getLocalAddress() const override { return m_localaddress; }
		void* getSessionObject() override { return m_object; }
		void setSessionObject(void *obj) override { m_object = obj; }
		Transport* getTransport() override { return this; }
	};

	namespace erroroccured {

		void fireErrorOccured(EndpointManager* manager, int type, int code, const std::string& info)
		{
			if (auto impl = dynamic_cast<EndpointImpl*>(manager))
				impl->fireErrorOccured(type, code, info);
		}

	} // namespace erroroccured {

	void Endpoint::openEngine(){ Engine::open(); }

	void Endpoint::closeEngine(Runnable done){ Engine::close(done); }

	void Endpoint::start(std::shared_ptr<EndpointConfig> config, EndpointListener* handler)
	{
		Engine::execute([config, handler](){
			hashmap<int32_t, int8_t> pvids;
			if (!config->isPingServerOnly())
			{
				try
				{
					helper::makeProviderMap(pvids, config);
				}
				catch (helper::MakeProviderMapException& e)
				{
					config->getExecutor()([=](){handler->onErrorOccured(SOURCE_ENDPOINT, e.errorcode, e.message); });
					return;
				}
			}
			new EndpointImpl(config, pvids, handler);
		});
	}

	EndpointManager* Endpoint::getDefaultEndpointManager()
	{
		return helper::g_defaultEndpointManager.load();
	}

	namespace endpoint {
		namespace switcherendpoint {

			void SHandShake::process()
			{
				if (EndpointImpl* manager = dynamic_cast<EndpointImpl*>(getTransport()->getManager()))
					manager->onProtocolSKeyExchange(this);
			}

			void OnlineAnnounce::process()
			{
				if (EndpointImpl* manager = dynamic_cast<EndpointImpl*>(getTransport()->getManager()))
					manager->onProtocolOnlineAnnounce(this);
			}

			void PingAndKeepAlive::process()
			{
				if (EndpointImpl* manager = dynamic_cast<EndpointImpl*>(getTransport()->getManager()))
					manager->onProtocolPingAndKeepAlive(this);
			}

			void SessionKick::process()
			{
				if (EndpointImpl* manager = dynamic_cast<EndpointImpl*>(getTransport()->getManager()))
					manager->onProtocolSessionKick(this);
			}

			void PortForward::process() {}
			void CHandShake::process() {}
			void SessionLoginByToken::process() {}

			void ProviderLogin::process() {
				if (EndpointImpl* manager = dynamic_cast<EndpointImpl*>(getTransport()->getManager()))
					manager->onProtocolProviderLogin(this);
			}
		} // namespace switcherendpoint { 

		namespace providerendpoint {

			void SyncViewToClients::process()
			{
				auto impl = dynamic_cast<EndpointImpl*>(getTransport()->getManager());
				if (impl)
				{
					try
					{
						impl->onProtocolSyncViewToClients(this);
					}
					catch (MarshalException&)
					{
						impl->unmarshalViewException(providerid, classindex, instanceindex);
					}
				}
			}

			void Tunnel::process()
			{
				if (EndpointImpl* manager = dynamic_cast<EndpointImpl*>(getTransport()->getManager()))
					manager->onProtocolTunnel(this);
			}

			void SendControlToServer::process() {}

		} // namespace providerendpoint { 
	} // namespace endpoint {

	std::mutex Engine::mutex;
	std::mutex Engine::closeables_mutex;
	std::condition_variable_any Engine::cond;
	std::unordered_set<EndpointImpl*> Engine::set;
	std::unordered_set<std::shared_ptr<Closeable>> Engine::closeables;
	ThreadPool *Engine::pool;
	void Engine::open()
	{
		std::lock_guard<std::mutex> l(mutex);
		if (!pool)
		{
			helper::OsSystemInit::getInstance().Startup();
			pool = new ThreadPool(30000);
		}
	}

	namespace helper { void cleanupAuanyService(); }
	void Engine::close(Runnable done)
	{
		helper::cleanupAuanyService();
		std::thread([done](){
			std::lock_guard<std::mutex> l(mutex);
			if (pool)
			{
				while (!closeables.empty())
					remove(*closeables.begin());
				for (auto& e : std::vector<EndpointImpl*>(set.begin(), set.end()))
					e->close();
				while (set.size())
					cond.wait(mutex);
				delete pool;
				pool = nullptr;
				helper::OsSystemInit::getInstance().Cleanup();
			}
			if (done)
				done();
		}).detach();
	}

	void Engine::add(EndpointImpl* e)
	{
		std::lock_guard<std::mutex> l(mutex);
		set.insert(e);
	}

	void Engine::add(std::shared_ptr<Closeable> c)
	{
		std::lock_guard<std::mutex> l(closeables_mutex);
		closeables.insert(c);
	}

	void Engine::remove(EndpointImpl* e)
	{
		std::lock_guard<std::mutex> l(mutex);
		if (set.erase(e) == 0)
			return;
		if (set.empty())
			cond.notify_one();
		delete e;
	}

	void Engine::remove(std::shared_ptr<Closeable> c)
	{
		{
			std::lock_guard<std::mutex> l(closeables_mutex);
			if (!closeables.erase(c))
				return;
		}
		if (std::shared_ptr<WebSocketConnector> w = std::dynamic_pointer_cast<WebSocketConnector>(c))
			w->_close();
	}

	std::shared_ptr<Closeable> Endpoint::start(const std::string& host, short port, LoginConfigPtr loginConfig, ScriptEngineHandlePtr handle)
	{
		return createWebSocketConnector(host, port, loginConfig, handle);
	}

} // namespace limax {
