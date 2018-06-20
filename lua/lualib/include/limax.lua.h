#pragma once 

#ifdef LIMAX_OS_WINDOWS
#	ifndef LIMAX_BUILD_AS_STATIC
#		ifdef LIMAXLUA_EXPORTS
#define LIMAX_LUA_API __declspec( dllexport )
#pragma warning( disable: 4275)
#		else
#define LIMAX_LUA_API
#		endif
#	else
#define LIMAX_LUA_API
#	endif
#else
#define LIMAX_LUA_API
#endif

namespace limax {

	enum
	{
		SOURCE_LUA = 9,

		LUA_SUCCEED = SUCCEED,
		LUA_CALL_LUA_FUNC_FAILED = 1,
		LUA_DO_STRING_FAILED,
		LUA_ILLEGAL_ARGUMENT,
		LUA_PROVIDER_IDS_EMPTY,
		LUA_BAD_VARIANT_TYPE,
		LUA_NOT_VARIANT_VIEWCONTEXT,
		LUA_LOST_VARIANT_VIEWCONTEXT,
		LUA_CODE_ERROR,
	};

	typedef std::function<TemporaryViewHandler*(const std::string&)> QueryTemporaryViewHandler;

	class LIMAX_LUA_API LuaVariantContext
	{
	public:
		LuaVariantContext();
		virtual ~LuaVariantContext();
	public:
		virtual bool createVariantTable(Variant) = 0;
		virtual TemporaryViewHandler* createTemporaryViewHandleTable(TemporaryViewHandler*) = 0;
		virtual bool createViewTable(VariantView*) = 0;
		virtual bool createVariantManagerTable(VariantManager*, QueryTemporaryViewHandler qtvh = [](const std::string&){ return nullptr; }) = 0;
		virtual bool createEndpointManagerTable(EndpointManager*, std::shared_ptr<EndpointConfig>, QueryTemporaryViewHandler qtvh = [](const std::string&){ return nullptr; }) = 0;
		virtual void doClose() = 0;
	};

	typedef std::shared_ptr<LuaVariantContext> LuaVariantContextPtr;

	class LIMAX_LUA_API LuaCreator
	{
		LuaCreator();
		~LuaCreator();
	public:
		static ScriptEngineHandlePtr createScriptEngineHandle(lua_State* L, int table, bool fastscript, std::vector<int> providers, DictionaryCachePtr cache, TunnelReceiver ontunnel ,ScriptErrorCollector sec);
		static ScriptEngineHandlePtr createScriptEngineHandle(lua_State* L, int table, bool fastscript, std::vector<int> providers, DictionaryCachePtr cache, ScriptErrorCollector sec);
		static ScriptEngineHandlePtr createScriptEngineHandle(lua_State* L, int table, bool fastscript, DictionaryCachePtr cache, ScriptErrorCollector sec);
		static ScriptEngineHandlePtr createScriptEngineHandle(lua_State* L, int table, bool fastscript, std::vector<int> providers, ScriptErrorCollector sec);
		static ScriptEngineHandlePtr createScriptEngineHandle(lua_State* L, int table, bool fastscript, ScriptErrorCollector sec);
		static LuaVariantContextPtr createLuaVariantContext(lua_State* L, int ref_callback);
	};


} //namespace limax {
