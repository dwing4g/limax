#include "endpointinc.h"

#include "dh.h"
#include "xmlgeninc/xmlgen.h"
#include "endpointhelper.h"
#include "auanyservice.h"

namespace limax {
	namespace helper {

		Octets toNonce(const std::string& authcode)
		{
			SHA256 sha;
			sha.update((int8_t*)authcode.c_str(), 0, (int32_t)authcode.size());
			return limax::Octets(sha.digest(), 32);
		}
		std::shared_ptr<AuanyService> AuanyService::removeService(int sn)
		{
			std::shared_ptr<AuanyService> service;
			{
				std::lock_guard<std::mutex> l(lock);
				auto it = map.find(sn);
				if (it != map.end())
				{
					(service = (*it).second)->future.cancel();
					map.erase(it);
				}
			}
			return service;
		}
		AuanyService::AuanyService(limax::AuanyService::Result _onresult, long timeout) :sn(snGenerator++), result(_onresult),
			future(DelayedRunnable([this](){
			std::shared_ptr<AuanyService> service = removeService(sn);
			if (service)
				service->result(SOURCE_ENDPOINT, ENDPOINT_AUANY_SERVICE_CLIENT_TIMEOUT, "");
		}, 500, (int)(timeout / 500)))
		{
			std::lock_guard<std::mutex> l(lock);
			Engine::execute(future);
			map.insert(std::make_pair(sn, std::shared_ptr<AuanyService>(this)));
		}
		void AuanyService::onResultViewOpen(endpoint::auanyviews::ServiceResult *view)
		{
			view->registerListener([](const ViewChangedEvent& e)
			{
				limax::auanyviews::Result *r = (limax::auanyviews::Result*) e.getValue();
				std::shared_ptr<AuanyService> service = removeService(r->sn);
				if (service)
					service->result(r->errorSource, r->errorCode, r->result);
			});
		}
		void AuanyService::cleanup()
		{
			std::lock_guard<std::mutex> l(lock);
			for (auto& e : map)
			{
				e.second->future.cancel();
				if (e.second->result)
					e.second->result(SOURCE_ENDPOINT, ENDPOINT_AUANY_SERVICE_ENGINE_CLOSE, "");
			}
			map.clear();
		}

		std::atomic_int AuanyService::snGenerator;
		std::unordered_map<int, std::shared_ptr<AuanyService>> AuanyService::map;
		std::mutex AuanyService::lock;

		void cleanupAuanyService()
		{
			AuanyService::cleanup();
		}

		struct CredentialContext::SharedData
		{
			int errorSource = SOURCE_ENDPOINT;
			int errorCode = ENDPOINT_AUANY_SERVICE_CLIENT_TIMEOUT;
			std::string credential;
			bool closemanager = false;
			EndpointManager* manager = nullptr;
			Runnable action;
		};

		struct CredentialContext::Listener : public EndpointListener{

			SharedDataPtr ptr;
			Listener(const CredentialContext::SharedDataPtr& p)
				: ptr(p)
			{}
			virtual ~Listener() {}

			virtual void onManagerInitialized(EndpointManager* m, EndpointConfig*) override
			{
				ptr->manager = m;
			}
			virtual void onTransportAdded(Transport*) override
			{
				ptr->action();
			}
			virtual void onTransportRemoved(Transport*) override {}
			virtual void onAbort(Transport*) override
			{
				ptr->closemanager = true;
			}
			virtual void onManagerUninitialized(EndpointManager*) override
			{
				ptr->manager = nullptr;
			}
			virtual void onSocketConnected() override {}
			virtual void onKeyExchangeDone() override {}
			virtual void onKeepAlived(int ping) override {}
			virtual void onErrorOccured(int errorsource, int errorvalue, const std::string& info) override
			{
				ptr->errorSource = errorsource;
				ptr->errorCode = errorvalue;
				ptr->closemanager = true;
			}
			virtual void destroy() override
			{
				delete this;
			}
		};
		CredentialContext::CredentialContext(const std::string& _httpHost, int _httpPort, int _appid, long _timeout)
			: httpHost(_httpHost), httpPort(_httpPort), appid(_appid), timeout(_timeout), shareddata(new SharedData())
		{
			auto data = shareddata;
			result = [data](int s, int c, const std::string& t)
			{
				data->errorSource = s;
				data->errorCode = c;
				data->credential = t;
				data->closemanager = true;
			};
		}
		CredentialContext::~CredentialContext() {}

		void CredentialContext::derive(const std::string& authcode, const limax::AuanyService::Result& r)
		{
			auto data = shareddata;
			execute([=](const std::string& code)
			{
				Runnable r = [=]()
				{
					limax::AuanyService::derive(code, authcode, timeout, result, data->manager);
				};
				return r;
			}, r);
		}
		void CredentialContext::bind(const std::string& authcode, LoginConfigPtr loginConfig, const limax::AuanyService::Result& r)
		{
			auto data = shareddata;
			execute([=](const std::string& code)
			{
				Runnable r = [=]()
				{
					limax::AuanyService::bind(code, authcode, loginConfig, timeout, result, data->manager);
				};
				return r;
			}, r);
		}
		void CredentialContext::temporary(const std::string& credential, const std::string& authcode, const std::string& authcode2, long milliseconds, int8_t usage, const std::string& subid, const limax::AuanyService::Result& r)
		{
			auto data = shareddata;
			execute([=](const std::string& code)
			{
				Runnable r = [=]()
				{
					limax::AuanyService::temporary(credential, authcode, authcode2, milliseconds, usage, subid, timeout, result, data->manager);
				};
				return r;
			}, r);
		}
		void CredentialContext::temporary(LoginConfigPtr loginConfig, int appid, const std::string& authcode, long milliseconds, int8_t usage, const std::string& subid, const limax::AuanyService::Result& r)
		{
			auto data = shareddata;
			execute([=](const std::string& code)
			{
				Runnable r = [=]()
				{
					limax::AuanyService::temporary(loginConfig, appid, authcode, milliseconds, usage, subid, timeout, result, data->manager);
				};
				return r;
			}, r);
		}
		void CredentialContext::transfer(LoginConfigPtr loginConfig, const std::string& authcode, const std::string& temp, const std::string& authtemp, const limax::AuanyService::Result& r)
		{
			auto data = shareddata;
			execute([=](const std::string& code)
			{
				Runnable r0 = [=]()
				{
					std::shared_ptr<helper::LoginConfigImpl> lc = std::dynamic_pointer_cast<helper::LoginConfigImpl>(loginConfig);
					endpoint::auanyviews::Service::getInstance(data->manager)->Transfer((new helper::AuanyService(r, timeout))->sn, lc->getUsername(), lc->getToken(limax::helper::toNonce(authcode)), lc->getPlatflagRaw(), authcode, temp, authtemp);
				};
				return r0;
			}, r);
		}

		void CredentialContext::execute(const Action& a, const limax::AuanyService::Result& r) {
			auto data = shareddata;
			try
			{
				auto starttime = std::chrono::high_resolution_clock::now();
				std::string jsonstring;
				{
					std::ostringstream oss;
					oss << "http://" << httpHost << ":" << httpPort << "/invite?native=" << appid;
					jsonstring = limax::http::httpDownload(oss.str(), (int)timeout, 4096, "", false);
				}
				auto json = JSON::parse(jsonstring);
				auto switcher = json->get("switcher");
				auto code = json->get("code")->toString();
				auto config = Endpoint::createEndpointConfigBuilder(switcher->get("host")->toString(),
					switcher->get("port")->intValue(), LoginConfig::plainLogin(code, "", "invite"))->executor(limax::runOnUiThread)->build();
				long remain = timeout - (long)std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - starttime).count();
				if (remain > 0)
				{
					data->action = a(code);
					Endpoint::start(config, new Listener(data));
					while (!data->closemanager && (remain > 0))
					{
						std::this_thread::sleep_for(std::chrono::milliseconds(1));
						remain = timeout - (long)std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - starttime).count();
					}
					if (nullptr != data->manager)
					{
						data->manager->close();
						while (nullptr != data->manager)
						{
							std::this_thread::sleep_for(std::chrono::milliseconds(1));
						}
					}
				}
			}
			catch (...)
			{
			}
			r(data->errorSource, data->errorCode, data->credential);
		}

	} // namespace helper {
} // namespace limax {

namespace limax {
	namespace endpoint {
		namespace auanyviews {
			void ServiceResult::onOpen(const std::vector<int64_t>& sessionids)
			{
				limax::helper::AuanyService::onResultViewOpen(this);
			}
			void ServiceResult::onAttach(int64_t sessionid) {}
			void ServiceResult::onDetach(int64_t sessionid, int reason)
			{
				if (reason >= 0)
				{
					//Application Reason
				}
				else
				{
					//Connection abort Reason
				}
			}
			void ServiceResult::onClose() {}
		} // namespace auanyviews {
	} // namespace endpoint {
} // namespace limax {

namespace limax {

	void AuanyService::derive(const std::string& httpHost, int httpPort, int appid, const std::string& authcode, long timeout, Result onresult)
	{
		helper::CredentialContext(httpHost, httpPort, appid, timeout).derive(authcode, onresult);
	}
	void AuanyService::derive(const std::string& credential, const std::string& authcode, long timeout, Result onresult, EndpointManager* manager)
	{
		endpoint::auanyviews::Service::getInstance(manager)->Derive((new helper::AuanyService(onresult, timeout))->sn, credential, authcode);
	}
	void AuanyService::derive(const std::string& credential, const std::string& authcode, long timeout, Result onresult)
	{
		derive(credential, authcode, timeout, onresult, Endpoint::getDefaultEndpointManager());
	}
	void AuanyService::bind(const std::string& httpHost, int httpPort, int appid, const std::string& authcode, LoginConfigPtr loginConfig, long timeout, Result onresult)
	{
		helper::CredentialContext(httpHost, httpPort, appid, timeout).bind(authcode, loginConfig, onresult);
	}
	void AuanyService::bind(const std::string& credential, const std::string& authcode, LoginConfigPtr loginConfig, long timeout, Result onresult, EndpointManager* manager)
	{
		std::shared_ptr<helper::LoginConfigImpl> lc = std::dynamic_pointer_cast<helper::LoginConfigImpl>(loginConfig);
		endpoint::auanyviews::Service::getInstance(manager)->Bind((new helper::AuanyService(onresult, timeout))->sn, credential, authcode, lc->getUsername(), lc->getToken(limax::helper::toNonce(authcode)), lc->getPlatflagRaw());
	}
	void AuanyService::bind(const std::string& credential, const std::string& authcode, LoginConfigPtr loginConfig, long timeout, Result onresult)
	{
		bind(credential, authcode, loginConfig, timeout, onresult, Endpoint::getDefaultEndpointManager());
	}
	void AuanyService::temporary(const std::string& httpHost, int httpPort, int appid, const std::string& credential, const std::string& authcode, const std::string& authcode2, long milliseconds, int8_t usage, const std::string& subid, long timeout, Result onresult)
	{
		helper::CredentialContext(httpHost, httpPort, appid, timeout).temporary(credential, authcode, authcode2, milliseconds, usage, subid, onresult);
	}
	void AuanyService::temporary(const std::string& credential, const std::string& authcode, const std::string& authcode2, long milliseconds, int8_t usage, const std::string& subid, long timeout, Result onresult, EndpointManager* manager)
	{
		endpoint::auanyviews::Service::getInstance(manager)->TemporaryFromCredential((new helper::AuanyService(onresult, timeout))->sn, credential, authcode, authcode2, milliseconds, usage, subid);
	}
	void AuanyService::temporary(const std::string& credential, const std::string& authcode, const std::string& authcode2, long milliseconds, int8_t usage, const std::string& subid, long timeout, Result onresult)
	{
		temporary(credential, authcode, authcode2, milliseconds, usage, subid, timeout, onresult, Endpoint::getDefaultEndpointManager());
	}
	void AuanyService::temporary(const std::string& httpHost, int httpPort, int appid, LoginConfigPtr loginConfig, const std::string& authcode, long milliseconds, int8_t usage, const std::string& subid, long timeout, Result onresult)
	{
		helper::CredentialContext(httpHost, httpPort, appid, timeout).temporary(loginConfig, appid, authcode, milliseconds, usage, subid, onresult);
	}
	void AuanyService::temporary(LoginConfigPtr loginConfig, int appid, const std::string& authcode, long milliseconds, int8_t usage, const std::string& subid, long timeout, Result onresult, EndpointManager* manager)
	{
		std::shared_ptr<helper::LoginConfigImpl> lc = std::dynamic_pointer_cast<helper::LoginConfigImpl>(loginConfig);
		endpoint::auanyviews::Service::getInstance(manager)->TemporaryFromLogin((new helper::AuanyService(onresult, timeout))->sn, lc->getUsername(), lc->getToken(limax::helper::toNonce(authcode)), lc->getPlatflagRaw(), appid, authcode, milliseconds, usage, subid);
	}
	void AuanyService::temporary(LoginConfigPtr loginConfig, int appid, const std::string& authcode, long milliseconds, int8_t usage, const std::string& subid, long timeout, Result onresult)
	{
		temporary(loginConfig, appid, authcode, milliseconds, usage, subid, timeout, onresult, Endpoint::getDefaultEndpointManager());
	}
	void AuanyService::transfer(const std::string& httpHost, int httpPort, int appid, LoginConfigPtr loginConfig, const std::string& authcode, const std::string& temp, const std::string& authtemp, long timeout, Result onresult)
	{
		helper::CredentialContext(httpHost, httpPort, appid, timeout).transfer(loginConfig, authcode, temp, authtemp, onresult);
	}
	void AuanyService::pay(int gateway, int payid, int product, int price, int quantity, const std::string& receipt, long timeout, Result onresult, EndpointManager *manager)
	{
		endpoint::auanyviews::Service::getInstance(manager)->Pay((new helper::AuanyService(onresult, timeout))->sn, gateway, payid, product, price, quantity, receipt);
	}
	void AuanyService::pay(int gateway, int payid, int product, int price, int quantity, const std::string& receipt, long timeout, Result onresult)
	{
		pay(gateway, payid, product, price, quantity, receipt, timeout, onresult, Endpoint::getDefaultEndpointManager());
	}
} // namespace limax {
