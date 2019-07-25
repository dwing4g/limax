#pragma once

namespace limax {

	typedef std::function<void(int, int, const std::string&)> ScriptErrorCollector;
	typedef std::function<bool(const std::string&)> ScriptSender;
	typedef std::function<void(const std::string&,Runnable)> LmkDataReceiver;

	class LIMAX_DLL_EXPORT_API DictionaryCache
	{
	public:
		DictionaryCache();
		virtual ~DictionaryCache();
	public:
		virtual void put(std::string key, std::string value) = 0;
		virtual std::string get(std::string key) = 0;
		virtual std::vector<std::string> keys() = 0;
	};

	typedef std::shared_ptr<DictionaryCache> DictionaryCachePtr;

	class LIMAX_DLL_EXPORT_API SimpleDictionaryCache : public DictionaryCache
	{
		std::mutex mutex;
		hashmap<std::string, std::string> map;
	public:
		SimpleDictionaryCache();
		virtual ~SimpleDictionaryCache();	
	public:
		virtual void put(std::string key, std::string value) override {
			std::lock_guard<std::mutex> l(mutex);
			map.insert(std::make_pair(key, value));
		}
		virtual std::string get(std::string key) override {
			std::lock_guard<std::mutex> l(mutex);
			auto it = map.find(key);
			return it == map.end() ? "" : (*it).second;
		}
		virtual std::vector<std::string> keys() override {
			std::lock_guard<std::mutex> l(mutex);
			std::vector<std::string> keys;
			for (const auto& c : map)
				keys.push_back(c.first);
			return keys;
		}

		static DictionaryCachePtr createInstance() {
			return std::make_shared<SimpleDictionaryCache>();
		}
	};

	struct LIMAX_DLL_EXPORT_API ProviderLoginDataManager;
	class LIMAX_DLL_EXPORT_API ScriptEngineHandle
	{
	public:
		ScriptEngineHandle();
		virtual ~ScriptEngineHandle();
	public:
		virtual const hashset<int32_t>& getProviders() = 0;
		virtual int action(int, const std::string&) = 0;
		virtual void registerScriptSender(ScriptSender) = 0;
		virtual void registerLmkDataReceiver(LmkDataReceiver) = 0;
		virtual void registerProviderLoginDataManager(std::shared_ptr<ProviderLoginDataManager>) = 0;
		virtual DictionaryCachePtr getDictionaryCache() = 0;
		virtual void tunnel(int32_t providerid, int32_t label, const std::string& data) = 0;
		virtual void tunnel(int32_t providerid, int32_t label, const Octets& data) = 0;
	};
	typedef std::shared_ptr<ScriptEngineHandle> ScriptEngineHandlePtr;

	typedef std::function<void(int32_t, int32_t, const std::string&)> TunnelReceiver;
} // namespace limax {
