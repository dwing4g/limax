#pragma once

namespace limax {
	struct LIMAX_DLL_EXPORT_API Endpoint
	{
		static void openEngine();
		static void closeEngine(Runnable);
		static std::shared_ptr<EndpointConfigBuilder> createEndpointConfigBuilder(const std::string& serverip, int serverport, LoginConfigPtr loginConfig);
		static std::shared_ptr<EndpointConfigBuilder> createEndpointConfigBuilder(const std::shared_ptr<ServiceInfo>& service, LoginConfigPtr loginConfig);
		static std::shared_ptr<EndpointConfigBuilder> createPingOnlyConfigBuilder(const std::string& serverip, int serverport);
		static std::vector<std::shared_ptr<ServiceInfo>> loadServiceInfos(const std::string& httpHost, int httpPort, int appid, const std::string& additionalQuery, long timeout, int maxsize, const std::string& cacheDir, bool staleEnable);
		static std::vector<std::shared_ptr<ServiceInfo>> loadServiceInfos(const std::string& httpHost, int httpPort, int appid, long timeout, int maxsize, const std::string& cacheDir, bool staleEnable);
		static void start(std::shared_ptr<EndpointConfig>, EndpointListener* handler);
		static std::shared_ptr<Closeable> start(const std::string& ip, short port, LoginConfigPtr loginConfig, ScriptEngineHandlePtr handle);
		static EndpointManager* getDefaultEndpointManager();
	};
	struct LIMAX_DLL_EXPORT_API AuanyService
	{
		enum : int32_t { providerId = 1 };
		typedef std::function<void(int, int, const std::string&)> Result;
		static void derive(const std::string& httpHost, int httpPort, int appid, const std::string& authcode, long timeout, Result onresult);
		static void derive(const std::string& credential, const std::string& authcode, long timeout, Result onresult, EndpointManager* manager);
		static void derive(const std::string& credential, const std::string& authcode, long timeout, Result onresult);
		static void bind(const std::string& httpHost, int httpPort, int appid, const std::string& authcode, LoginConfigPtr loginConfig, long timeout, Result onresult);
		static void bind(const std::string& credential, const std::string& authcode, LoginConfigPtr loginConfig, long timeout, Result onresult, EndpointManager* manager);
		static void bind(const std::string& credential, const std::string& authcode, LoginConfigPtr loginConfig, long timeout, Result onresult);
		static void temporary(const std::string& httpHost, int httpPort, int appid, const std::string& credential, const std::string& authcode, const std::string& authcode2, long milliseconds, int8_t usage, const std::string& subid, long timeout, Result onresult);
		static void temporary(const std::string& credential, const std::string& authcode, const std::string& authcode2, long milliseconds, int8_t usage, const std::string& subid, long timeout, Result onresult, EndpointManager* manager);
		static void temporary(const std::string& credential, const std::string& authcode, const std::string& authcode2, long milliseconds, int8_t usage, const std::string& subid, long timeout, Result onresult);
		static void temporary(const std::string& httpHost, int httpPort, int appid, LoginConfigPtr loginConfig, const std::string& authcode, long milliseconds, int8_t usage, const std::string& subid, long timeout, Result onresult);
		static void temporary(LoginConfigPtr loginConfig, int appid, const std::string& authcode, long milliseconds, int8_t usage, const std::string& subid, long timeout, Result onresult, EndpointManager* manager);
		static void temporary(LoginConfigPtr loginConfig, int appid, const std::string& authcode, long milliseconds, int8_t usage, const std::string& subid, long timeout, Result onresult);
		static void transfer(const std::string& httpHost, int httpPort, int appid, LoginConfigPtr loginConfig, const std::string& authcode, const std::string& temp, const std::string& authtemp, long timeout, Result onresult);
		static void pay(int gateway, int payid, int product, int price, int count, const std::string& invoice, long timeout, Result onresult, EndpointManager *manager);
		static void pay(int gateway, int payid, int product, int price, int count, const std::string& invoice, long timeout, Result onresult);
	};
} // namespace limax {
