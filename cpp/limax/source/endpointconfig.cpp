#include "endpointinc.h"

#include "dh.h"
#include "xmlgeninc/xmlgen.h"
#include "endpointhelper.h"

namespace limax {

	EndpointListener::EndpointListener() {}
	EndpointListener::~EndpointListener() {}

	EndpointManager::EndpointManager() {}
	EndpointManager::~EndpointManager() {}

	Transport::Transport() {}
	Transport::~Transport() {}

	EndpointConfig::EndpointConfig() {}
	EndpointConfig::~EndpointConfig() {}

	EndpointConfigBuilder::EndpointConfigBuilder() {}
	EndpointConfigBuilder::~EndpointConfigBuilder() {}

	ServiceInfo::ServiceInfo() {}
	ServiceInfo::~ServiceInfo() {}

	ProviderLoginDataManager::ProviderLoginDataManager() {}
	ProviderLoginDataManager::~ProviderLoginDataManager() {}

	LoginConfig::LoginConfig() {}
	LoginConfig::~LoginConfig() {}

	LmkBundle::LmkBundle() {}
	LmkBundle::~LmkBundle() {}

	std::shared_ptr<LmkBundle> LmkBundle::createInstance(Octets lmkdata, Octets passphrase)
	{
		return std::make_shared<helper::LmkBundleImpl>(lmkdata, passphrase);
	}

	ProviderLoginDataManagerPtr ProviderLoginDataManager::createInstance()
	{
		return std::make_shared<helper::ProviderLoginDataManagerImpl>();
	}
	LoginConfigPtr LoginConfig::plainLogin(const std::string& username, const std::string& token, const std::string& platflag, const std::string& subid, ProviderLoginDataManagerPtr pldm)
	{
		return std::make_shared<helper::LoginConfigImpl>(username, token, platflag, nullptr, subid, pldm);
	}
	LoginConfigPtr LoginConfig::plainLogin(const std::string& username, const std::string& token, const std::string& platflag, const std::string& subid)
	{
		return std::make_shared<helper::LoginConfigImpl>(username, token, platflag, nullptr, subid, nullptr);
	}
	LoginConfigPtr LoginConfig::plainLogin(const std::string& username, const std::string& token, const std::string& platflag, ProviderLoginDataManagerPtr pldm)
	{
		return std::make_shared<helper::LoginConfigImpl>(username, token, platflag, nullptr, "", pldm);
	}
	LoginConfigPtr LoginConfig::plainLogin(const std::string& username, const std::string& token, const std::string& platflag)
	{
		return std::make_shared<helper::LoginConfigImpl>(username, token, platflag, nullptr, "", nullptr);
	}
	static std::string decodeMainCredential(std::string credential) {
		std::string::size_type pos = credential.find_first_of(',');
		return pos == std::string::npos ? credential : credential.substr(0, pos);
	}
	LoginConfigPtr LoginConfig::credentialLogin(const std::string& credential, const std::string& authcode, const std::string& subid, ProviderLoginDataManagerPtr pldm)
	{
		return std::make_shared<helper::LoginConfigImpl>(decodeMainCredential(credential), authcode, "credential", nullptr, subid, pldm);
	}
	LoginConfigPtr LoginConfig::credentialLogin(const std::string& credential, const std::string& authcode, const std::string& subid)
	{
		return std::make_shared<helper::LoginConfigImpl>(decodeMainCredential(credential), authcode, "credential", nullptr, subid, nullptr);
	}
	LoginConfigPtr LoginConfig::credentialLogin(const std::string& credential, const std::string& authcode, ProviderLoginDataManagerPtr pldm)
	{
		return std::make_shared<helper::LoginConfigImpl>(decodeMainCredential(credential), authcode, "credential", nullptr, "", pldm);
	}
	LoginConfigPtr LoginConfig::credentialLogin(const std::string& credential, const std::string& authcode)
	{
		return std::make_shared<helper::LoginConfigImpl>(decodeMainCredential(credential), authcode, "credential", nullptr, "", nullptr);
	}
	LoginConfigPtr LoginConfig::lmkLogin(LmkBundlePtr lmkBundle, const std::string& subid, ProviderLoginDataManagerPtr pldm)
	{
		return std::make_shared<helper::LoginConfigImpl>("", "", "lmk", lmkBundle, subid, pldm);
	}
	LoginConfigPtr LoginConfig::lmkLogin(LmkBundlePtr lmkBundle, const std::string& subid)
	{
		return std::make_shared<helper::LoginConfigImpl>("", "", "lmk", lmkBundle, subid, nullptr);
	}
	LoginConfigPtr LoginConfig::lmkLogin(LmkBundlePtr lmkBundle, ProviderLoginDataManagerPtr pldm)
	{
		return std::make_shared<helper::LoginConfigImpl>("", "", "lmk", lmkBundle, "", pldm);
	}
	LoginConfigPtr LoginConfig::lmkLogin(LmkBundlePtr lmkBundle)
	{
		return std::make_shared<helper::LoginConfigImpl>("", "", "lmk", lmkBundle, "", nullptr);
	}

	std::shared_ptr<EndpointConfigBuilder> Endpoint::createEndpointConfigBuilder(const std::string& serverip, int serverport, LoginConfigPtr loginConfig)
	{
		auto cb = std::shared_ptr<helper::EndpointConfigBuilderImpl>(new helper::EndpointConfigBuilderImpl(serverip, serverport, false));
		cb->instance = cb;
		cb->data.loginConfig = loginConfig;
		return cb;
	}
	std::shared_ptr<EndpointConfigBuilder> Endpoint::createEndpointConfigBuilder(const std::shared_ptr<ServiceInfo>& service, LoginConfigPtr loginConfig)
	{
		const std::pair<std::string, int32_t> switcher = std::dynamic_pointer_cast<helper::ServiceInfoImpl>(service)->randomSwitcherConfig();
		return createEndpointConfigBuilder(switcher.first, switcher.second, loginConfig);
	}
	std::shared_ptr<EndpointConfigBuilder> Endpoint::createPingOnlyConfigBuilder(const std::string& serverip, int serverport)
	{
		auto cb = std::shared_ptr<helper::EndpointConfigBuilderImpl>(new helper::EndpointConfigBuilderImpl(serverip, serverport, true));
		cb->instance = cb;
		cb->data.ispingonly = true;
		return cb;
	}
	std::vector<std::shared_ptr<ServiceInfo>> Endpoint::loadServiceInfos(const std::string& httpHost, int httpPort, int appid, const std::string& additionalQuery, long timeout, int maxsize, const std::string& cacheDir, bool staleEnable)
	{
		std::string jsonstring;
		{
			std::ostringstream oss;
			oss << "http://" << httpHost << ":" << httpPort << "/app?native=" << appid;
			if (additionalQuery.size() > 0) {
				if (additionalQuery.at(0) != '&')
					oss << '&';
				oss << additionalQuery;
			}
			jsonstring = limax::http::httpDownload(oss.str(), (int)timeout, maxsize, cacheDir, staleEnable);
		}
		std::vector<std::shared_ptr<ServiceInfo>> r;
		for (auto json : JSON::parse(jsonstring)->get("services")->toArray())
			r.push_back(std::make_shared<helper::ServiceInfoImpl>(appid, json));
		return r;
	}
	std::vector<std::shared_ptr<ServiceInfo>> Endpoint::loadServiceInfos(const std::string& httpHost, int httpPort, int appid, long timeout, int maxsize, const std::string& cacheDir, bool staleEnable)
	{
		return loadServiceInfos(httpHost, httpPort, appid, "", timeout, maxsize, cacheDir, staleEnable);
	}

} // namespace limax {

