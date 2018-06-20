#include "endpointinc.h"

#include "dh.h"
#include "xmlgeninc/_xmlgen_.hpp"
#include "endpointhelper.h"
#include "variantdef.h"
#include "viewcontextimpl.h"

namespace limax {

	namespace helper {
		static Octets BI2Octets(BI bi) {
			Octets o;
			U32 size = BI_size(bi);
			o.resize(size);
			BI_to_array(bi, (U8*)o.begin(), size);
			return o;
		}

		static BI Octets2BI(Octets o) {
			return BI_from_array((U8*)o.begin(), (U32)o.size());
		}

		BI LmkBundleImpl::encrypt(BI message)
		{
			if (coef == nullptr)
				return BI_modexp(message, d, n);
			BI acc;
			acc = BI_mod(message, p);
			BI c1 = BI_modexp(acc, exp1, p);
			BI_free(acc);
			acc = BI_mod(message, q);
			BI c2 = BI_modexp(acc, exp2, q);
			BI_free(acc);
			acc = BI_sub(c1, c2);
			BI tmp = BI_mul(acc, coef);
			BI_free(acc);
			acc = BI_mod(tmp, p);
			BI_free(tmp);
			tmp = BI_mul(acc, q);
			BI_free(acc);
			acc = BI_add(tmp, c2);
			BI_free(tmp);
			BI_free(c1);
			BI_free(c2);
			return acc;
		}

		LmkBundleImpl::~LmkBundleImpl()
		{
			if (coef == nullptr) {
				BI_free(n);
				BI_free(d);
			}
			else {
				BI_free(p);
				BI_free(q);
				BI_free(exp1);
				BI_free(exp2);
				BI_free(coef);
			}
		}

		LmkBundleImpl::LmkBundleImpl(Octets lmkdata, Octets passphrase)
		{
			if ((magic = UnmarshalStream(lmkdata).unmarshal_int()) != MAGIC)
				throw MarshalException();
			Octets o;
			Decrypt decrypt(std::make_shared<SinkOctets>(o), (int8_t *)passphrase.begin(), (int32_t)passphrase.size());
			decrypt.update((int8_t *)lmkdata.begin(), 4, (int32_t)lmkdata.size() - 4);
			decrypt.flush();
			UnmarshalStream us(o);
			us.unmarshal_Octets(chain);
			Octets tmp;
			if (us.unmarshal_bool()) {
				us.unmarshal_Octets(tmp);
				n = Octets2BI(tmp);
				us.unmarshal_Octets(tmp);
				d = Octets2BI(tmp);
				p = nullptr;
				q = nullptr;
				exp1 = nullptr;
				exp2 = nullptr;
				coef = nullptr;
			}
			else{
				n = nullptr;
				d = nullptr;
				us.unmarshal_Octets(tmp);
				p = Octets2BI(tmp);
				us.unmarshal_Octets(tmp);
				q = Octets2BI(tmp);
				us.unmarshal_Octets(tmp);
				exp1 = Octets2BI(tmp);
				us.unmarshal_Octets(tmp);
				exp2 = Octets2BI(tmp);
				us.unmarshal_Octets(tmp);
				coef = Octets2BI(tmp);
			}
			this->passphrase.replace(passphrase.begin(), passphrase.size());
		}

		Octets LmkBundleImpl::save(Octets passphrase)
		{
			Octets o, r;
			MarshalStream ms(o);
			ms << chain;
			Octets tmp;
			if (coef == nullptr)
				ms << true << BI2Octets(n) << BI2Octets(d);
			else
				ms << false << BI2Octets(p) << BI2Octets(q) << BI2Octets(exp1) << BI2Octets(exp2) << BI2Octets(coef);
			MarshalStream(r) << magic;
			Encrypt encrypt(std::make_shared<SinkOctets>(r), (int8_t *)passphrase.begin(), (int32_t)passphrase.size());
			encrypt.update((int8_t *)o.begin(), 0, (int32_t)o.size());
			encrypt.flush();
			return r;
		}
		
		std::string LmkBundleImpl::sign(Octets message)
		{
			SHA256 sha;
			sha.update((int8_t*)message.begin(), 0, (int32_t)message.size());
			int8_t z = 0;
			Octets o(&z, 1);
			o.insert(o.end(), sha.digest(), 32);
			BI tmp = BI_from_array((U8*)o.begin(), (U32)o.size());
			BI data = encrypt(tmp);
			BI_free(tmp);
			o.resize(BI_size(data));
			BI_to_array(data, (U8*)o.begin(), (U32)o.size());
			BI_free(data);
			Octets os;
			MarshalStream(os) << o << passphrase;
			return "LMK0" + encodeBase64ToString(os);
		}

		std::string LmkBundleImpl::x509()
		{
			return encodeBase64ToString(chain);
		}

	} // namespace helper {

	namespace helper {

		size_t ViewContextMap::ViewContextTypeHash::operator()(ViewContext::Type t) const
		{
			return (size_t)t;
		}

		void ViewContextMap::ViewContextCollection::put(ViewContext::Type type, std::shared_ptr<AbstractViewContext> vc)
		{
			map.insert(std::make_pair(type, vc));
		}
		void ViewContextMap::ViewContextCollection::onSyncViewToClients(limax::endpoint::providerendpoint::SyncViewToClients* protocol)
		{
			for (auto& it : map)
				it.second->onSyncViewToClients(protocol);
		}
		void ViewContextMap::ViewContextCollection::clear()
		{
			for (auto& it : map)
				it.second->clear();
			map.clear();
		}
		std::shared_ptr<AbstractViewContext> ViewContextMap::ViewContextCollection::getViewContext(ViewContext::Type type)
		{
			auto it = map.find(type);
			return it == map.end() ? nullptr : it->second;
		}
		int ViewContextMap::ViewContextCollection::getSize() const
		{
			return (int)map.size();
		}
		std::shared_ptr<AbstractViewContext> ViewContextMap::ViewContextCollection::getViewContext(ViewContext::Type type) const
		{
			auto it = map.find(type);
			return it == map.end() ? nullptr : it->second;
		}
		void ViewContextMap::put(ViewContext::Type type, int32_t pvid, std::shared_ptr<AbstractViewContext> vc)
		{
			ViewContextCollection& cc = map[pvid];
			cc.put(type, vc);
		}
		void ViewContextMap::onSyncViewToClients(limax::endpoint::providerendpoint::SyncViewToClients* protocol)
		{
			auto it = map.find(protocol->providerid);
			if (it != map.end())
				it->second.onSyncViewToClients(protocol);
		}
		void ViewContextMap::clear()
		{
			for (auto& it : map)
				it.second.clear();
			map.clear();
		}
		const std::shared_ptr<ViewContext> ViewContextMap::getViewContext(int32_t pvid, ViewContext::Type type) const
		{
			auto it = map.find(pvid);
			if (it == map.end())
				return nullptr;
			else
				return it->second.getViewContext(type);
		}

	} // namespace helper {

	namespace helper {

		static defines::ProviderLoginData hollowProviderLoginData = defines::ProviderLoginData(0, defines::ProviderLoginData::tUnused, 0, Octets());

		ProviderLoginDataManagerImpl::~ProviderLoginDataManagerImpl() {}

		void ProviderLoginDataManagerImpl::add(int32_t pvid, Octets unsafedata)
		{
			map.insert(std::make_pair(pvid, defines::ProviderLoginData(pvid, defines::ProviderLoginData::tUserData, 0, unsafedata)));
		}
		void ProviderLoginDataManagerImpl::add(int32_t pvid, int32_t label, Octets data)
		{
			map.insert(std::make_pair(pvid, defines::ProviderLoginData(pvid, defines::ProviderLoginData::tTunnelData, label, data)));
		}
		void ProviderLoginDataManagerImpl::add(int32_t pvid, int32_t label, std::string data)
		{
			add(pvid, label, Base64Decode::transform(Octets(data.c_str(), data.length())));
		}
		std::vector<int32_t> ProviderLoginDataManagerImpl::getProviderIds()
		{
			std::vector<int32_t> r;
			for (const auto& e : map)
				r.push_back(e.first);
			return r;
		}
		bool ProviderLoginDataManagerImpl::isSafe(int32_t pvid)
		{
			return map[pvid].type == defines::ProviderLoginData::tTunnelData;
		}
		int32_t ProviderLoginDataManagerImpl::getLabel(int32_t pvid)
		{
			return map[pvid].label;
		}
		Octets ProviderLoginDataManagerImpl::getData(int32_t pvid)
		{
			return map[pvid].data;
		}
		defines::ProviderLoginData ProviderLoginDataManagerImpl::get(int32_t pvid)
		{
			auto it = map.find(pvid);
			return it == map.end() ? hollowProviderLoginData : (*it).second;
		}

	} // namespace helper {

	namespace helper {

		LoginConfigImpl::LoginConfigImpl(const std::string& _username, const std::string& _token, const std::string& _platflag, LmkBundlePtr _lmkBundle, const std::string& _subid, ProviderLoginDataManagerPtr _pldm)
			: username(_username), token(_token), platflag(_platflag), lmkBundle(_lmkBundle), subid(_subid), pldm(_pldm)
		{
		}
		std::string LoginConfigImpl::getUsername()
		{
			return lmkBundle == nullptr ? username : lmkBundle->x509();
		}
		std::string LoginConfigImpl::getToken(Octets nonce)
		{
			return lmkBundle == nullptr ? token : lmkBundle->sign(nonce);
		}
		std::string LoginConfigImpl::getPlatflagRaw()
		{
			return platflag;
		}
		std::string LoginConfigImpl::getPlatflag()
		{
			return subid.size() == 0 ? getPlatflagRaw() : getPlatflagRaw() + ":" + subid;
		}
		ProviderLoginDataManagerPtr LoginConfigImpl::getProviderLoginDataManager()
		{
			return pldm;
		}
		void LoginConfigImpl::setLmkUpdater(LmkUpdater _lmkUpdater)
		{
			lmkUpdater = _lmkUpdater;
		}
		LmkUpdater LoginConfigImpl::getLmkUpdater()
		{
			return lmkUpdater;
		}

	} // namespace helper {

	namespace helper {

		EndpointConfigImpl::EndpointConfigImpl(const EndpointConfigData& _data)
			: data(_data)
		{}
		EndpointConfigImpl::~EndpointConfigImpl() {}
		const std::string& EndpointConfigImpl::getServerIP() const
		{
			return data.serverIp;
		}
		int EndpointConfigImpl::getServerPort() const
		{
			return data.serverPort;
		}
		int EndpointConfigImpl::getDHGroup() const
		{
			return data.dhGroup;
		}
		LoginConfigPtr EndpointConfigImpl::getLoginConfig() const
		{
			return data.loginConfig;
		}
		bool EndpointConfigImpl::isPingServerOnly() const
		{
			return data.ispingonly;
		}
		bool EndpointConfigImpl::auanyService() const
		{
			return data.auanyService;
		}
		std::shared_ptr<State> EndpointConfigImpl::getEndpointState() const
		{
			return data.state;
		}
		const std::vector< std::shared_ptr<View::ViewCreatorManager> >& EndpointConfigImpl::getStaticViewCreatorManagers() const
		{
			return data.vcms;
		}
		const std::vector<int32_t>& EndpointConfigImpl::getVariantProviderIds() const
		{
			return data.vpvids;
		}
		std::shared_ptr<ScriptEngineHandle> EndpointConfigImpl::getScriptEngineHandle() const
		{
			return data.script;
		}
		Executor EndpointConfigImpl::getExecutor() const
		{
			return data.executor;
		}

		EndpointConfigBuilderImpl::EndpointConfigBuilderImpl(const std::string& serverip, int serverport, bool pingonly)
		{
			data.ispingonly = pingonly;
			data.auanyService = true;
			data.serverIp = serverip;
			data.serverPort = serverport;
			data.dhGroup = 2;
			data.executor = [](Runnable r){ r(); };
			data.state = limax::getEndpointStateEndpointClient(0);
		}
		EndpointConfigBuilderImpl::~EndpointConfigBuilderImpl() { }
		std::shared_ptr<EndpointConfigBuilder> EndpointConfigBuilderImpl::endpointState(std::initializer_list<std::shared_ptr<State>> states)
		{
			data.state = limax::getEndpointStateEndpointClient(0);
			for (auto& i : states)
				data.state->addStub(i);
			return instance.lock();
		}
		std::shared_ptr<EndpointConfigBuilder> EndpointConfigBuilderImpl::endpointState(std::vector<std::shared_ptr<State>> states)
		{
			data.state = limax::getEndpointStateEndpointClient(0);
			for (auto& i : states)
				data.state->addStub(i);
			return instance.lock();
		}
		std::shared_ptr<EndpointConfigBuilder> EndpointConfigBuilderImpl::staticViewCreatorManagers(std::initializer_list<std::shared_ptr<View::ViewCreatorManager>> vcms)
		{
			data.vcms.clear();
			data.vcms.reserve(vcms.size());
			data.vcms.insert(data.vcms.end(), vcms.begin(), vcms.end());
			return instance.lock();
		}
		std::shared_ptr<EndpointConfigBuilder> EndpointConfigBuilderImpl::staticViewCreatorManagers(std::vector<std::shared_ptr<View::ViewCreatorManager>> vcms)
		{
			data.vcms.clear();
			data.vcms.reserve(vcms.size());
			data.vcms.insert(data.vcms.end(), vcms.begin(), vcms.end());
			return instance.lock();
		}
		std::shared_ptr<EndpointConfigBuilder> EndpointConfigBuilderImpl::variantProviderIds(std::initializer_list<int32_t> pvids)
		{
			data.vpvids.clear();
			data.vpvids.reserve(pvids.size());
			data.vpvids.insert(data.vpvids.end(), pvids.begin(), pvids.end());
			return instance.lock();
		}
		std::shared_ptr<EndpointConfigBuilder> EndpointConfigBuilderImpl::variantProviderIds(std::vector<int32_t> pvids)
		{
			data.vpvids.clear();
			data.vpvids.reserve(pvids.size());
			data.vpvids.insert(data.vpvids.end(), pvids.begin(), pvids.end());
			return instance.lock();
		}
		std::shared_ptr<EndpointConfigBuilder> EndpointConfigBuilderImpl::scriptEngineHandle(std::shared_ptr<ScriptEngineHandle> handle)
		{
			data.script = handle;
			return instance.lock();
		}
		std::shared_ptr<EndpointConfigBuilder> EndpointConfigBuilderImpl::executor(Executor executor)
		{
			data.executor = executor;
			return instance.lock();
		}
		std::shared_ptr<EndpointConfigBuilder> EndpointConfigBuilderImpl::auanyService(bool used)
		{
			data.auanyService = used;
			return instance.lock();
		}
		std::shared_ptr<EndpointConfig> EndpointConfigBuilderImpl::build() const
		{
			return std::shared_ptr<EndpointConfig>(new EndpointConfigImpl(data));
		}

		ServiceInfoImpl::ServiceInfoImpl(int32_t _appid, std::shared_ptr<JSON>json) : appid(_appid)
		{
			for (auto switcher : json->get("switchers")->toArray())
				switchers.push_back(std::make_pair(switcher->get("host")->toString(), switcher->get("port")->intValue()));
			for (auto i : json->get("pvids")->toArray())
				pvids.push_back(i->intValue());
			for (auto i : json->get("payids")->toArray())
				payids.push_back(i->intValue());
			for (auto i : json->get("userjsons")->toArray())
				userjsons.push_back(JSON::parse(i->toString()));
			running = json->get("running")->booleanValue();
			optional = json->get("optional")->toString();
		}
		std::pair<std::string, int32_t> ServiceInfoImpl::randomSwitcherConfig()
		{
			std::random_shuffle(switchers.begin(), switchers.end());
			return switchers[0];
		}
		const std::vector<int32_t> ServiceInfoImpl::getPvids() const
		{
			return pvids;
		}
		const std::vector<int32_t> ServiceInfoImpl::getPayids() const
		{
			return payids;
		}
		const std::vector<std::shared_ptr<JSON>> ServiceInfoImpl::getUserJSONs() const
		{
			return userjsons;
		}
		bool ServiceInfoImpl::isRunning() const
		{
			return running;
		}
		const std::string ServiceInfoImpl::getOptional() const
		{
			return optional;
		}

		void mapPvidsAppendValue(hashmap<int32_t, int8_t>& pvids, int32_t s, int nv)
		{
			pvids[s] = (int8_t)(nv | pvids[s]);
		}
		void mapPvidsAppendValue(hashmap<int32_t, int8_t>& pvids, const hashset<int32_t>& set, int nv)
		{
			for (auto& it : set)
				mapPvidsAppendValue(pvids, it, nv);
		}
		const hashset<int32_t> makeProtocolPvidSet(std::shared_ptr<EndpointConfig> config)
		{
			return config->getEndpointState()->getProviderIds();
		}

		const hashset<int32_t> makeStaticPvidSet(std::shared_ptr<EndpointConfig> config)
		{
			hashset<int32_t> pvidset;
			const auto& vcms = config->getStaticViewCreatorManagers();
			for (auto& vcm : vcms)
				pvidset.insert(vcm->getProviderId());
			return pvidset;
		}

		const hashset<int32_t> makeVariantPvidSet(std::shared_ptr<EndpointConfig> config)
		{
			hashset<int32_t> pvidset;
			const auto& pvids = config->getVariantProviderIds();
			pvidset.insert(pvids.begin(), pvids.end());
			return pvidset;
		}

		const hashset<int32_t> makeScriptPvidSet(std::shared_ptr<EndpointConfig> config)
		{
			hashset<int32_t> pvidset;
			if (auto handle = config->getScriptEngineHandle())
				pvidset.insert(handle->getProviders().begin(), handle->getProviders().end());
			return pvidset;
		}

		const hashset<int32_t> makeAuanyPvidSet()
		{
			hashset<int32_t> pvidset;
			pvidset.insert(limax::AuanyService::providerId);
			return pvidset;
		}

		void makeProviderMap(hashmap<int32_t, int8_t>& pvids, std::shared_ptr<EndpointConfig> config)
		{
			mapPvidsAppendValue(pvids, makeProtocolPvidSet(config), limax::defines::SessionType::ST_PROTOCOL);
			mapPvidsAppendValue(pvids, makeStaticPvidSet(config), limax::defines::SessionType::ST_STATIC);
			mapPvidsAppendValue(pvids, makeVariantPvidSet(config), limax::defines::SessionType::ST_VARIANT);
			mapPvidsAppendValue(pvids, makeScriptPvidSet(config), limax::defines::SessionType::ST_SCRIPT);
			if (config->auanyService())
				mapPvidsAppendValue(pvids, makeAuanyPvidSet(), limax::defines::SessionType::ST_STATIC);
		}

	} // namespace helper {

} // namespace limax {

