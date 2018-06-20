#pragma once

namespace limax {
	namespace helper {

		class LmkBundleImpl : public LmkBundle{
			const int32_t MAGIC = 0x4c4d4b30;
			int32_t magic;
			Octets passphrase;
			Octets chain;
			BI n;
			BI d;
			BI p;
			BI q;
			BI exp1;
			BI exp2;
			BI coef;
			BI encrypt(BI message);
		public:
			virtual ~LmkBundleImpl();
			LmkBundleImpl(Octets lmkdata, Octets passphrase);
			virtual Octets save(Octets passphrase) override;
			virtual std::string sign(Octets message) override;
			virtual std::string x509() override;
		};

		struct ViewContextMap
		{
			struct ViewContextTypeHash
			{
				size_t operator()(ViewContext::Type t) const;
			};
			struct ViewContextCollection
			{
				std::unordered_map<ViewContext::Type, std::shared_ptr<AbstractViewContext>, ViewContextTypeHash> map;
				void put(ViewContext::Type type, std::shared_ptr<AbstractViewContext> vc);
				void onSyncViewToClients(limax::endpoint::providerendpoint::SyncViewToClients* protocol);
				void clear();
				std::shared_ptr<AbstractViewContext> getViewContext(ViewContext::Type type);
				int getSize() const;
				std::shared_ptr<AbstractViewContext> getViewContext(ViewContext::Type type) const;
			};
			hashmap<int32_t, ViewContextCollection> map;
			void put(ViewContext::Type type, int32_t pvid, std::shared_ptr<AbstractViewContext> vc);
			void onSyncViewToClients(limax::endpoint::providerendpoint::SyncViewToClients* protocol);
			void clear();
			const std::shared_ptr<ViewContext> getViewContext(int32_t pvid, ViewContext::Type type) const;
		};

	} // namespace helper { 

	namespace helper {

		class ProviderLoginDataManagerImpl : public ProviderLoginDataManager {
			hashmap<int32_t, defines::ProviderLoginData> map;
		public:
			virtual ~ProviderLoginDataManagerImpl();
			virtual void add(int32_t pvid, Octets unsafedata) override;
			virtual void add(int32_t pvid, int32_t label, Octets data) override;
			virtual void add(int32_t pvid, int32_t label, std::string data) override;
			virtual std::vector<int32_t> getProviderIds() override;
			virtual bool isSafe(int32_t pvid) override;
			virtual int32_t getLabel(int32_t pvid) override;
			virtual Octets getData(int32_t pvid) override;
			defines::ProviderLoginData get(int32_t pvid);
		};
	} // namespace helper {

	namespace helper {

		class LoginConfigImpl : public LoginConfig {
			std::string username;
			std::string token;
			std::string platflag;
			LmkBundlePtr lmkBundle;
			std::string subid;
			ProviderLoginDataManagerPtr pldm;
			LmkUpdater lmkUpdater;
		public:
			LoginConfigImpl(const std::string& _username, const std::string& _token, const std::string& _platflag, LmkBundlePtr _lmkBundle, const std::string& _subid, ProviderLoginDataManagerPtr _pldm);
			std::string getUsername();
			std::string getToken(Octets nonce);
			std::string getPlatflagRaw();
			std::string getPlatflag();
			ProviderLoginDataManagerPtr getProviderLoginDataManager();
			virtual void setLmkUpdater(LmkUpdater _lmkUpdater) override;

			LmkUpdater getLmkUpdater();
		};

	} // namespace helper {

	namespace helper {

		struct EndpointConfigData
		{
			std::string serverIp;
			int serverPort;
			int dhGroup;
			LoginConfigPtr loginConfig;
			bool ispingonly;
			bool auanyService;
			std::shared_ptr<State> state;
			std::vector< std::shared_ptr<View::ViewCreatorManager> > vcms;
			std::vector<int32_t> vpvids;
			std::shared_ptr<ScriptEngineHandle> script;
			Executor executor;
		};

		struct EndpointConfigImpl : public EndpointConfig
		{
			EndpointConfigData	data;
			EndpointConfigImpl(const EndpointConfigData&);
			virtual ~EndpointConfigImpl();
			virtual const std::string& getServerIP() const override;
			virtual int getServerPort() const override; 
			virtual int getDHGroup() const override;
			virtual LoginConfigPtr getLoginConfig() const override;
			virtual bool isPingServerOnly() const override;
			virtual bool auanyService() const override;
			virtual std::shared_ptr<State> getEndpointState() const override;
			virtual const std::vector< std::shared_ptr<View::ViewCreatorManager> >& getStaticViewCreatorManagers() const override;
			virtual const std::vector<int32_t>& getVariantProviderIds() const override;
			virtual std::shared_ptr<ScriptEngineHandle> getScriptEngineHandle() const override;
			virtual Executor getExecutor() const override;
		};

		struct EndpointConfigBuilderImpl : public EndpointConfigBuilder
		{
			EndpointConfigData data;
			std::weak_ptr<EndpointConfigBuilder> instance;
			EndpointConfigBuilderImpl(const std::string& serverip, int serverport, bool pingonly);
			virtual ~EndpointConfigBuilderImpl();
			virtual std::shared_ptr<EndpointConfigBuilder> endpointState(std::initializer_list<std::shared_ptr<State>> states) override;
			virtual std::shared_ptr<EndpointConfigBuilder> endpointState(std::vector<std::shared_ptr<State>> states) override;
			virtual std::shared_ptr<EndpointConfigBuilder> staticViewCreatorManagers(std::initializer_list<std::shared_ptr<View::ViewCreatorManager>> vcms) override;
			virtual std::shared_ptr<EndpointConfigBuilder> staticViewCreatorManagers(std::vector<std::shared_ptr<View::ViewCreatorManager>> vcms) override;
			virtual std::shared_ptr<EndpointConfigBuilder> variantProviderIds(std::initializer_list<int32_t> pvids) override;
			virtual std::shared_ptr<EndpointConfigBuilder> variantProviderIds(std::vector<int32_t> pvids) override;
			virtual std::shared_ptr<EndpointConfigBuilder> scriptEngineHandle(std::shared_ptr<ScriptEngineHandle> handle) override;
			virtual std::shared_ptr<EndpointConfigBuilder> executor(Executor executor) override;
			virtual std::shared_ptr<EndpointConfigBuilder> auanyService(bool used) override;
			virtual std::shared_ptr<EndpointConfig> build() const override;
		};

		struct ServiceInfoImpl : public ServiceInfo
		{
			std::vector<std::pair<std::string, int32_t>> switchers;
			int32_t appid;
			std::vector<int32_t> pvids;
			std::vector<int32_t> payids;
			std::vector<std::shared_ptr<JSON>> userjsons;
			std::string optional;
			bool running;
			ServiceInfoImpl(int32_t _appid, std::shared_ptr<JSON>json);
			std::pair<std::string, int32_t> randomSwitcherConfig();
			const std::vector<int32_t> getPvids() const override;
			const std::vector<int32_t> getPayids() const override;
			const std::vector<std::shared_ptr<JSON>> getUserJSONs() const override;
			bool isRunning() const override;
			const std::string getOptional() const override;
		};

		void mapPvidsAppendValue(hashmap<int32_t, int8_t>& pvids, int32_t s, int nv);
		void mapPvidsAppendValue(hashmap<int32_t, int8_t>& pvids, const hashset<int32_t>& set, int nv);
		const hashset<int32_t> makeProtocolPvidSet(std::shared_ptr<EndpointConfig> config);
		const hashset<int32_t> makeStaticPvidSet(std::shared_ptr<EndpointConfig> config);
		const hashset<int32_t> makeVariantPvidSet(std::shared_ptr<EndpointConfig> config);
		const hashset<int32_t> makeScriptPvidSet(std::shared_ptr<EndpointConfig> config);
		const hashset<int32_t> makeAuanyPvidSet();

		struct MakeProviderMapException
		{
			MakeProviderMapException(int code) : errorcode(code){}
			MakeProviderMapException(int code, const std::string& msg) : errorcode(code), message(msg){}
			int errorcode;
			std::string message;
		};

		void makeProviderMap(hashmap<int32_t, int8_t>& pvids, std::shared_ptr<EndpointConfig> config);

	} // namespace helper {

} // namespace limax {

