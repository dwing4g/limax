#include "common.h"
#include "luahelper.h"

extern int Limax(lua_State* L);
extern const char* g_limax_lua_code_string;

namespace limax {
	namespace helper {

		const char* lua_code_string_script_func_send =
			"return function( table, func)\n"
			"	return function( msg)\n"
			"		return func( table, msg)\n"
			"	end\n"
			"end"
			;

		const char* lua_code_string_unregisterlistener =
			"return function( func, view, tid)\n"
			"	return function()\n"
			"		func( view, tid)\n"
			"	end\n"
			"end";

		const char* lua_code_string_hookcallback_param =
			"return function( func, param, callback)\n"
			"	return function( ctx)\n"
			"		callback( ctx)\n"
			"		func( param, ctx)\n"
			"	end\n"
			"end";

		const char* lua_code_string_create_xpcall =
			"return function( callback)\n"
			"	if nil == callback then\n"
			"		callback = function(msg)\n"
			"			print(\"LUA ERROR: \", tostring(msg), \"\\n\")\n"
			"			print(debug.traceback())\n"
			"		end\n"
			"	end\n"
			"	return function( limax, a, s)\n"
			"		local result\n"
			"		local function runxpcall()\n"
			"			result = limax(a, s)\n"
			"		end\n"
			"		xpcall( runxpcall, callback)\n"
			"		return result\n"
			"	end\n"
			"end";

		int lua_scriptSendFunc(lua_State* L)
		{
			auto sender = (ScriptSender*)lua_touserdata(L, -2);
			std::string msg = lua_tostring(L, -1);
			if ((*sender)(msg))
				lua_pushnil(L);
			else
				lua_pushstring(L, "endpoint manager closed!");
			return 1;
		}

	} // namespace helper {

	namespace helper {

		class LuaScriptHandleImpl : public ScriptEngineHandle
		{
			std::recursive_mutex mutex;
			hashset<int32_t> pvids;
			lua_State* luaState;
			int ref_limax;
			int ref_onscript;
			int ref_errortraceback;

			ScriptSender sender;
			DictionaryCachePtr cache;
			LmkDataReceiver lmkDataReceiver;
			ScriptErrorCollector sec;
		public:
			LuaScriptHandleImpl()
				: luaState(nullptr), ref_limax(-1), ref_onscript(-1), ref_errortraceback(-1)
			{}
			virtual ~LuaScriptHandleImpl()
			{
				releaseObjectFromGlobal(luaState, ref_limax);
				releaseObjectFromGlobal(luaState, ref_onscript);
				releaseObjectFromGlobal(luaState, ref_errortraceback);
			}
		public:
			virtual const hashset<int32_t>& getProviders() override
			{
				return pvids;
			}
			virtual int action(int t, const std::string& p) override
			{
				std::lock_guard<std::recursive_mutex> l(mutex);
				lua_State* L = luaState;

				getObjectFromGlobal(L, ref_onscript);
				lua_pushinteger(L, t);
				lua_pushlstring(L, p.c_str(), p.size());
				int ec = lua_pcall(L, 2, 0, 0);
				if (LUA_OK != ec)
					lua_pop(L, 1);

				getObjectFromGlobal(L, ref_errortraceback);
				getObjectFromGlobal(L, ref_limax);
				lua_pushinteger(L, t);
				lua_pushlstring(L, p.c_str(), p.size());
				ec = lua_pcall(L, 3, 1, 0);
				if (LUA_OK != ec)
				{
					const char* msg = lua_tostring(L, -1);
					sec(SOURCE_LUA, LUA_CALL_LUA_FUNC_FAILED, msg);
					lua_pop(L, 1);
					return 3;
				}
				ec = (int)luaL_checkinteger(L, -1);
				lua_pop(L, 1);
				return ec;
			}

			virtual void registerScriptSender(ScriptSender sender) override
			{
				std::lock_guard<std::recursive_mutex> l(mutex);
				this->sender = sender;

				lua_State* L = luaState;

				int ec = luaL_dostring(L, lua_code_string_script_func_send);
				if (LUA_OK != ec)
				{
					const char* msg = lua_tostring(L, -1);
					sec(SOURCE_LUA, LUA_CALL_LUA_FUNC_FAILED, msg);
					lua_pop(L, 1);
					return;
				}

				lua_pushlightuserdata(L, &this->sender);
				lua_pushcfunction(L, helper::lua_scriptSendFunc);
				ec = lua_pcall(L, 2, 1, 0);
				if (LUA_OK != ec)
				{
					const char* msg = lua_tostring(L, -1);
					sec(SOURCE_LUA, LUA_CALL_LUA_FUNC_FAILED, msg);
					lua_pop(L, 1);
					return;
				}
				getObjectFromGlobal(L, ref_limax);
				lua_pushinteger(L, 0);
				lua_pushvalue(L, -3);
				ec = lua_pcall(L, 2, 0, 0);
				if (LUA_OK != ec)
				{
					const char* msg = lua_tostring(L, -1);
					sec(SOURCE_LUA, LUA_CALL_LUA_FUNC_FAILED, msg);
					lua_pop(L, 1);
				}
			}

			virtual void registerLmkDataReceiver(LmkDataReceiver receiver) override
			{
				lmkDataReceiver = receiver;
			}

			virtual void registerProviderLoginDataManager(std::shared_ptr<ProviderLoginDataManager> pldm) override
			{
				std::lock_guard<std::recursive_mutex> l(mutex);
				lua_State* L = luaState;
				lua_newtable(L);
				for (int32_t pvid : pldm->getProviderIds())
				{
					lua_pushinteger(L, pvid);
					lua_newtable(L);
					lua_pushstring(L, "data");
					std::string data = encodeBase64ToString(pldm->getData(pvid));
					lua_pushlstring(L, data.c_str(), data.length());
					lua_rawset(L, -3);
					if (pldm->isSafe(pvid)) {
						lua_pushstring(L, "label");
						lua_pushinteger(L, pldm->getLabel(pvid));
						lua_rawset(L, -3);
					}
					else {
						lua_pushstring(L, "base64");
						lua_pushinteger(L, 1);
						lua_rawset(L, -3);
					}
					lua_rawset(L, -3);
				}
				getObjectFromGlobal(L, ref_limax);
				lua_rotate(L, -2, 1);
				int ec = lua_pcall(L, 1, 0, 0);
				if (LUA_OK != ec)
				{
					const char* msg = lua_tostring(L, -1);
					sec(SOURCE_LUA, LUA_CALL_LUA_FUNC_FAILED, msg);
					lua_pop(L, 1);
				}
			}

			virtual DictionaryCachePtr getDictionaryCache() override
			{
				return cache;
			}

			virtual void tunnel(int32_t providerid, int32_t label, const std::string& data) override {
				std::lock_guard<std::recursive_mutex> l(mutex);
				lua_State* L = luaState;
				getObjectFromGlobal(L, ref_limax);
				lua_pushinteger(L, providerid);
				lua_pushinteger(L, label);
				lua_pushlstring(L, data.c_str(), data.length());
				int ec = lua_pcall(L, 3, 0, 0);
				if (LUA_OK != ec)
				{
					const char* msg = lua_tostring(L, -1);
					sec(SOURCE_LUA, LUA_CALL_LUA_FUNC_FAILED, msg);
					lua_pop(L, 1);
				}
			}

			virtual void tunnel(int32_t providerid, int32_t label, const Octets& data) override {
				tunnel(providerid, label, encodeBase64ToString(data));
			}
		private:
			static int lua_codecallbackfunc(lua_State* L)
			{
				if (!lua_isuserdata(L, -2))
				{
					luaL_error(L, "arg 1 shoule be userdata");
					return 0;
				}
				auto impl = (LuaScriptHandleImpl*)lua_touserdata(L, -2);
				impl->on_lua_code_callback(L);
				return 0;
			}
			static int lua_code_onscript(lua_State* L)
			{
				//int a = luaL_checkinteger(L, -2);
				//std::string s(luaL_checkstring(L, -1));
				//printf("%d %s\n", a, s.c_str());
				return 0;
			}
			struct LuaException
			{
				int code;
				std::string msg;
				LuaException(int _code, const char* _msg)
					: code(_code), msg(_msg)
				{}
			};
			void on_lua_code_callback(lua_State* L)
			{
				lua_pushstring(L, "onscript");
				lua_gettable(L, -2);
				if (lua_isfunction(L, -1))
				{
					ref_onscript = appendObjectToGlobal(L);
				}
				else
				{
					lua_pop(L, 1);
					lua_pushcfunction(L, lua_code_onscript);
					ref_onscript = appendObjectToGlobal(L);
				}

				int ec = luaL_dostring(L, lua_code_string_create_xpcall);
				if (LUA_OK != ec)
				{
					LuaException e(LUA_DO_STRING_FAILED, lua_tostring(L, -1));
					lua_pop(L, 1);
					throw e;
				}
				lua_pushstring(L, "errortraceback");
				lua_gettable(L, -3);
				ec = lua_pcall(L, 1, 1, 0);
				if (LUA_OK != ec)
				{
					LuaException e(LUA_CALL_LUA_FUNC_FAILED, lua_tostring(L, -1));
					lua_pop(L, 4);
					throw e;
				}
				ref_errortraceback = appendObjectToGlobal(L);
			}
		public:
			bool initialize(lua_State* L, int table, bool fastscript, std::vector<int> providers, DictionaryCachePtr cache, TunnelReceiver ontunnel, ScriptErrorCollector sec)
			{
				this->sec = sec;
				if (!lua_istable(L, table))
				{
					sec(SOURCE_LUA, LUA_ILLEGAL_ARGUMENT, "input arg not a 'table'");
					return false;
				}
				lua_pushvalue(L, table);

				if (providers.empty())
				{
					lua_pushfstring(L, "pvids");
					lua_gettable(L, -2);

					if (!lua_istable(L, -1))
					{
						lua_pop(L, 2);
						sec(SOURCE_LUA, LUA_ILLEGAL_ARGUMENT, "input arg member 'pvids' not a 'table'");
						return false;
					}
					int n = (int)lua_rawlen(L, -1);
					for (int i = 1; i <= n; i++)
					{
						lua_rawgeti(L, -1, i);
						int pvid = (int)luaL_checkinteger(L, -1);
						this->pvids.insert(pvid);
						lua_pop(L, 1);
					}
					lua_pop(L, 1);
				}
				else
				{
					for (auto pvid : providers)
						this->pvids.insert(pvid);
				}

				if (this->pvids.empty())
				{
					lua_pop(L, 1);
					sec(SOURCE_LUA, LUA_PROVIDER_IDS_EMPTY, "input arg member 'pvids' is empty");
					return false;
				}

				lua_pushfstring(L, "callback");
				lua_gettable(L, -2);

				if (!lua_isfunction(L, -1))
				{
					lua_pop(L, 2);
					sec(SOURCE_LUA, LUA_ILLEGAL_ARGUMENT, "input arg member 'callback' not a 'function'");
					return false;
				}

				if (fastscript)
					lua_pushcfunction(L, Limax);
				else
				{
					int ec = luaL_dostring(L, g_limax_lua_code_string);
					if (LUA_OK != ec)
					{
						sec(SOURCE_LUA, LUA_DO_STRING_FAILED, lua_tostring(L, -1));
						lua_pop(L, 3);
						return false;
					}
				}
				int ec = luaL_dostring(L, lua_code_string_hookcallback_param);
				if (LUA_OK != ec)
				{
					sec(SOURCE_LUA, LUA_DO_STRING_FAILED, lua_tostring(L, -1));
					lua_pop(L, 4);
					return false;
				}
				lua_pushcfunction(L, lua_codecallbackfunc);
				lua_pushlightuserdata(L, this);
				lua_pushvalue(L, -5);

				try
				{
					ec = lua_pcall(L, 3, 1, 0);
					if (LUA_OK != ec)
					{
						sec(SOURCE_LUA, LUA_CALL_LUA_FUNC_FAILED, lua_tostring(L, -1));
						lua_pop(L, 4);
						return false;
					}
				}
				catch (LuaException& e)
				{
					sec(SOURCE_LUA, e.code, e.msg);
					return false;
				}
				this->cache = cache;
				if (nullptr != cache ? cache : SimpleDictionaryCache::createInstance())
				{
					lua_newtable(L);
					lua_newtable(L);
					lua_pushlightuserdata(L, new DictionaryCachePtrGCWrapper(cache));
					lua_rawsetp(L, -2, L);
					lua_pushstring(L, "__gc");
					lua_pushcfunction(L, [](lua_State *L){
						lua_getmetatable(L, 1);
						lua_rawgetp(L, -1, L);
						delete (DictionaryCachePtrGCWrapper *)lua_touserdata(L, -1);
						return 0;
					});
					lua_rawset(L, -3);
					lua_setmetatable(L, -2);
					lua_pushstring(L, "put");
					lua_pushvalue(L, -2);
					lua_pushcclosure(L, [](lua_State *L){
						lua_pushvalue(L, lua_upvalueindex(1));
						lua_getmetatable(L, -1);
						lua_rawgetp(L, -1, L);
						((DictionaryCachePtrGCWrapper *)lua_touserdata(L, -1))->cache->put(std::string(lua_tostring(L, 1)), std::string(lua_tostring(L, 2)));
						return 0;
					}, 1);
					lua_rawset(L, -3);
					lua_pushstring(L, "get");
					lua_pushvalue(L, -2);
					lua_pushcclosure(L, [](lua_State *L){
						lua_pushvalue(L, lua_upvalueindex(1));
						lua_getmetatable(L, -1);
						lua_rawgetp(L, -1, L);
						std::string value(((DictionaryCachePtrGCWrapper *)lua_touserdata(L, -1))->cache->get(lua_tostring(L, 1)));
						if (value.size() > 0)
							lua_pushstring(L, value.c_str());
						else
							lua_pushnil(L);
						return 1;
					}, 1);
					lua_rawset(L, -3);
				}
				else
					lua_pushnil(L);

				TunnelReceiverGCWrapper* func = new TunnelReceiverGCWrapper([this, ontunnel](int32_t providerid, int32_t label, const std::string& data)
				{
					if (providerid == 1)
					{
						if (lmkDataReceiver)
							lmkDataReceiver(data, [this](){tunnel(AuanyService::providerId, -1, ""); });
					}
					else if (ontunnel)
						ontunnel(providerid, label, data);
				});
				lua_newtable(L);
				lua_pushlightuserdata(L, func);
				lua_rawsetp(L, -2, L);
				lua_pushstring(L, "__gc");
				lua_pushcfunction(L, [](lua_State *L){
					lua_getmetatable(L, 1);
					lua_rawgetp(L, -1, L);
					delete (TunnelReceiverGCWrapper *)lua_touserdata(L, -1);
					return 0;
				});
				lua_rawset(L, -3);
				lua_pushlightuserdata(L, func);
				lua_pushcclosure(L, [](lua_State *L){
					((TunnelReceiverGCWrapper *)lua_touserdata(L, lua_upvalueindex(1)))->ontunnel((int32_t)lua_tointeger(L, 1), (int32_t)lua_tointeger(L, 2), std::string(lua_tostring(L, 3)));
					return 0;
				}, 1);
				lua_rotate(L, -2, 1);
				lua_setmetatable(L, -2);

				ec = lua_pcall(L, 3, 1, 0);
				if (LUA_OK != ec)
				{
					sec(SOURCE_LUA, LUA_CALL_LUA_FUNC_FAILED, lua_tostring(L, -1));
					lua_pop(L, 4);
					return false;
				}

				ref_limax = appendObjectToGlobal(L);
				lua_pop(L, 3);

				luaState = L;
				return true;
			}
		};

	} // namespace helper {
} // namespace limax {

namespace limax {
	namespace helper {

		int makeVariantTable(lua_State* L, Variant var);

		int makeOctetsTable(lua_State* L, const Octets data)
		{
			lua_newtable(L);
			auto ptr = (unsigned char*)data.begin();
			auto count = data.size();
			for (size_t i = 1; i <= count; i++)
			{
				lua_pushinteger(L, (lua_Integer)ptr[i]);
				lua_rawseti(L, -2, i);
			}
			return LUA_SUCCEED;
		}

		template<typename Container> int makeListTable(lua_State* L, const Container& c)
		{
			lua_newtable(L);
			int i = 1;
			for (auto it = c.begin(), ite = c.end(); it != ite; ++it, i++)
			{
				auto v = *it;
				int ec = makeVariantTable(L, v);
				if (LUA_SUCCEED != ec)
				{
					lua_pop(L, 1);
					return ec;
				}
				lua_rawseti(L, -2, i);
			}
			return LUA_SUCCEED;
		}

		template<typename Container> int makeMapTable(lua_State* L, const Container& c)
		{
			lua_newtable(L);
			for (auto it = c.begin(), ite = c.end(); it != ite; ++it)
			{
				auto k = it->first;
				auto v = it->second;
				int ec = makeVariantTable(L, k);
				if (LUA_SUCCEED != ec)
				{
					lua_pop(L, 1);
					return ec;
				}
				ec = makeVariantTable(L, v);
				if (LUA_SUCCEED != ec)
				{
					lua_pop(L, 2);
					return ec;
				}
				lua_settable(L, -3);
			}
			return LUA_SUCCEED;
		}

		int makeStructTable(lua_State* L, Variant var)
		{
			auto decl = std::dynamic_pointer_cast<StructDeclaration>(var.makeDeclaration());
			auto vars = decl->getVariables();
			lua_newtable(L);
			for (auto it = vars.begin(), ite = vars.end(); it != ite; ++it)
			{
				auto vd = *it;
				makeVariantTable(L, var.getVariant(vd->getName()));
				lua_setfield(L, -2, vd->getName().c_str());
			}
			return LUA_SUCCEED;
		}

		int makeVariantTable(lua_State* L, Variant var)
		{
			switch (var.getVariantType())
			{
			case VariantType::Null:
				lua_pushnil(L);
				return LUA_SUCCEED;
			case VariantType::Boolean:
				lua_pushboolean(L, var.getBooleanValue() ? 1 : 0);
				return LUA_SUCCEED;
			case VariantType::Byte:
				lua_pushinteger(L, var.getByteValue());
				return LUA_SUCCEED;
			case VariantType::Short:
				lua_pushinteger(L, var.getShortValue());
				return LUA_SUCCEED;
			case VariantType::Int:
				lua_pushinteger(L, var.getIntValue());
				return LUA_SUCCEED;
			case VariantType::Long:
				lua_pushinteger(L, var.getLongValue());
				return LUA_SUCCEED;
			case VariantType::Float:
				lua_pushnumber(L, var.getFloatValue());
				return LUA_SUCCEED;
			case VariantType::Double:
				lua_pushnumber(L, var.getDoubleValue());
				return LUA_SUCCEED;
			case VariantType::String:
			{
				const auto& v = var.getStringValue();
				lua_pushlstring(L, v.c_str(), v.size());
				return LUA_SUCCEED;
			}
			case VariantType::Binary:
				return makeOctetsTable(L, var.getOctetsValue());
			case VariantType::List:
				return makeListTable(L, var.getListValue());
			case VariantType::Vector:
				return makeListTable(L, var.getVectorValue());
			case VariantType::Set:
				return makeListTable(L, var.getSetValue());
			case VariantType::Map:
				return makeMapTable(L, var.getMapValue());
			case VariantType::Struct:
				return makeStructTable(L, var);
			default:
				return LUA_BAD_VARIANT_TYPE;
			}
		}

		class VaraintErrorCollector
		{
			lua_State* lua_state;
			int ref_error_notify_table;
			int ref_variant_manager;
		public:
			inline VaraintErrorCollector(lua_State* s, int e)
				: lua_state(s), ref_error_notify_table(e), ref_variant_manager(-1)
			{}
			inline ~VaraintErrorCollector() {}
		private:
			inline void fireLuaCodeError(const std::string& message) const
			{
				if (-1 == ref_variant_manager)
					return;
				GetGlobalObject _dummy_object(lua_state, ref_variant_manager);

				if (!lua_istable(lua_state, -1))
					return;

				lua_pushstring(lua_state, "onerror");
				lua_gettable(lua_state, -2);
				if (!lua_isfunction(lua_state, -1))
				{
					lua_pop(lua_state, 1);
					return;
				}

				pushParam(lua_state, message);
				int ec = lua_pcall(lua_state, 1, 0, 0);
				if (LUA_OK != ec)
					lua_pop(lua_state, 1);
			}
		public:
			inline void updateVairantManager(int m)
			{
				ref_variant_manager = m;
			}
			inline void fireError(int source, int code, const std::string& message) const
			{
				if (SOURCE_LUA == source && LUA_CODE_ERROR == code)
					fireLuaCodeError(message);
				fireErrorOccured(lua_state, ref_error_notify_table, source, code, message);
			}
		};

		typedef std::shared_ptr<VaraintErrorCollector> VaraintErrorCollectorPtr;

		class ViewListenerImpl
		{
			lua_State* L;
			int ref_view;
			int ref_listern_func;
			VaraintErrorCollectorPtr errorcollector;
		private:
			ViewListenerImpl(lua_State* _state, int _ref, int _ref_listern_func, VaraintErrorCollectorPtr _errorcollector)
				: L(_state), ref_view(_ref), ref_listern_func(_ref_listern_func), errorcollector(_errorcollector)
			{}
		public:
			~ViewListenerImpl()
			{
				releaseObjectFromGlobal(L, ref_listern_func);
			}
		private:
			void onViewChange(const VariantViewChangedEvent& e)
			{
				GetGlobalObject __dummy_object(L, ref_view);
				if (-1 == ref_listern_func)
				{
					lua_getfield(L, -1, "onchange");
					if (!lua_isfunction(L, -1))
					{
						lua_pop(L, 1);
						return;
					}
				}
				else
				{
					getObjectFromGlobal(L, ref_listern_func);
				}
				lua_newtable(L);

				lua_pushvalue(L, -3);
				lua_setfield(L, -2, "view");

				lua_pushinteger(L, e.getSessionId());
				lua_setfield(L, -2, "sessionid");

				lua_pushlstring(L, e.getFieldName().c_str(), e.getFieldName().size());
				lua_setfield(L, -2, "fieldname");

				int ec = makeVariantTable(L, e.getValue());
				if (LUA_SUCCEED != ec)
				{
					lua_pop(L, 2);
					errorcollector->fireError(limax::SOURCE_LUA, ec, "onViewChange makeVariantTable getNewValue");
					return;
				}
				lua_setfield(L, -2, "value");;

				lua_pushinteger(L, e.getType());
				lua_setfield(L, -2, "type");

				ec = lua_pcall(L, 1, 0, 0);
				if (LUA_OK != ec)
				{
					const char* msg = lua_tostring(L, -1);

					std::stringstream ss;
					ss << "call onchange error = " << ec << " msg = " << msg;
					errorcollector->fireError(limax::SOURCE_LUA, limax::LUA_CALL_LUA_FUNC_FAILED, ss.str());
					lua_pop(L, 1);
				}
			}
		public:
			static VariantViewChangedListener create(lua_State* L, int ref_view, VaraintErrorCollectorPtr errorcollector)
			{
				auto impl = std::shared_ptr<ViewListenerImpl>(new ViewListenerImpl(L, ref_view, -1, errorcollector));
				return[impl](const VariantViewChangedEvent& e) { impl->onViewChange(e); };
			}
			static VariantViewChangedListener create(lua_State* L, int ref_view, int ref_listern_func, VaraintErrorCollectorPtr errorcollector)
			{
				auto impl = std::shared_ptr<ViewListenerImpl>(new ViewListenerImpl(L, ref_view, ref_listern_func, errorcollector));
				return[impl](const VariantViewChangedEvent& e) { impl->onViewChange(e); };
			}
		};

		class LuaVariantContextImpl;
		struct ViewContextData
		{
			std::weak_ptr<LuaVariantContextImpl> weak_impl;
			VariantView* view;
			int refview;
			int64_t tid;
		};

		typedef std::shared_ptr<ViewContextData> ViewContextDataPtr;

		const char* const strSubscribeSessionIdName = "__limax_subscribe_sessionid__";

		int lua_viewSubscribeIndexFunc(lua_State* L)
		{
			if (!lua_istable(L, -2))
			{
				luaL_error(L, "arg 1 shoule be table");
				return 0;
			}
			lua_getfield(L, -2, getLimaxUserDataName());
			if (!lua_isuserdata(L, -1))
			{
				luaL_error(L, "arg 1 lost userdata");
				return 0;
			}
			auto viewdata = (ViewContextData*)lua_touserdata(L, -1);
			if (nullptr == viewdata)
			{
				luaL_error(L, "nullptr == ViewContextData");
				return 0;
			}
			lua_pop(L, 1);

			lua_getfield(L, -2, strSubscribeSessionIdName);
			int64_t sessionid = luaL_checkinteger(L, -1);
			lua_pop(L, 1);

			auto varname = lua_check_string(L, -1);

			viewdata->view->visitField(varname, [L, sessionid](const limax::Variant& var)
			{
				auto it = var.getMapValue().find(limax::Variant::create(sessionid));
				if (it == var.getMapValue().end())
					lua_pushnil(L);
				else
					makeVariantTable(L, it->second);
			});
			return 1;
		}

		int lua_viewIndexFunc(lua_State* L)
		{
			if (!lua_istable(L, -2))
			{
				luaL_error(L, "arg 1 shoule be view ( not table)");
				return 0;
			}
			lua_getfield(L, -2, getLimaxUserDataName());
			if (!lua_isuserdata(L, -1))
			{
				luaL_error(L, "arg 1 shoule be view ( lost userdata)");
				return 0;
			}
			auto viewdata = (ViewContextData*)lua_touserdata(L, -1);
			if (nullptr == viewdata)
			{
				luaL_error(L, "nullptr == ViewContextData");
				return 0;
			}
			lua_pop(L, 1);

			if (lua_isinteger(L, -1))
			{
				lua_newtable(L);
				lua_pushlightuserdata(L, viewdata);
				lua_setfield(L, -2, getLimaxUserDataName());

				lua_newtable(L);
				lua_pushcfunction(L, lua_viewSubscribeIndexFunc);
				lua_setfield(L, -2, "__index");
				lua_setmetatable(L, -2);

				lua_pushvalue(L, -2);
				lua_setfield(L, -2, strSubscribeSessionIdName);
			}
			else if (lua_isstring(L, -1))
			{
				auto varname = lua_check_string(L, -1);
				viewdata->view->visitField(varname, [L](const limax::Variant& var) { makeVariantTable(L, var); });
			}
			else
			{
				luaL_error(L, "arg 2 shoule be varname or sessionid");
			}
			return 1;
		}

		int lua_viewSendMessage(lua_State* L)
		{
			if (!lua_istable(L, 1))
			{
#ifdef  LIMAX_DEBUG
				luaL_error(L, "arg 1 shoule be view ( not table)");
#endif //  LIMAX_DEBUG
				return 0;
			}
			lua_getfield(L, 1, getLimaxUserDataName());
			if (!lua_isuserdata(L, -1))
			{
#ifdef  LIMAX_DEBUG
				luaL_error(L, "arg 1 shoule be view ( lost userdata)");
#endif //  LIMAX_DEBUG
				return 0;
			}
			stackdump(L);
			auto viewdata = (ViewContextData*)lua_touserdata(L, -1);
			if (nullptr == viewdata)
			{
#ifdef  LIMAX_DEBUG
				luaL_error(L, "nullptr == ViewContextData");
#endif //  LIMAX_DEBUG
				return 0;
			}
			lua_pop(L, 1);

			auto msg = lua_check_string(L, 2);
			viewdata->view->sendMessage(msg);
			return 0;
		}

		int lua_viewMetaToString(lua_State* L)
		{
			if (!lua_istable(L, 1))
			{
				luaL_error(L, "arg 1 shoule be view ( not table)");
				return 0;
			}
			lua_getfield(L, 1, getLimaxUserDataName());
			if (!lua_isuserdata(L, -1))
			{
				luaL_error(L, "arg 1 shoule be view ( lost userdata)");
				return 0;
			}
			auto viewdata = (ViewContextData*)lua_touserdata(L, -1);
			if (nullptr == viewdata)
			{
				luaL_error(L, "nullptr == ViewContextData");
				return 0;
			}
			lua_pop(L, 1);
			const auto& viewname = viewdata->view->getViewName();
			lua_pushlstring(L, viewname.c_str(), viewname.size());
			return 1;
		}

		int lua_viewRegisterChangeListener(lua_State* L);

		class LuaVariantContextImpl : public LuaVariantContext
		{
			friend class limax::LuaCreator;

			hashmap<int64_t, ViewContextDataPtr> views;
			hashmap<int64_t, Runnable> listenerholds;
			limax::Resource resources = limax::Resource::createRoot();
			std::weak_ptr<LuaVariantContextImpl> instance;
			VaraintErrorCollectorPtr errorcollector;
			lua_State* lua_state;
			int ref_variant_manager = -1;
			int ref_view_meta_table = -1;
			int64_t tidgenerator = 0;
		public:
			LuaVariantContextImpl(lua_State* L, int ref_notify)
				: lua_state(L)
			{
				errorcollector = std::shared_ptr<VaraintErrorCollector>(new VaraintErrorCollector(L, ref_notify));
			}
			virtual ~LuaVariantContextImpl()
			{
				resources.close();
				for (auto it = views.begin(), ite = views.end(); it != ite; ++it)
				{
					auto data = it->second;
					releaseObjectFromGlobal(lua_state, data->refview);
				}
				views.clear();
				releaseObjectFromGlobal(lua_state, ref_view_meta_table);
				releaseObjectFromGlobal(lua_state, ref_variant_manager);
			}
		public:
			inline void removeView(ViewContextData* data)
			{
				views.erase(data->tid);
				releaseObjectFromGlobal(lua_state, data->refview);
			}
			inline int64_t addView(ViewContextDataPtr data)
			{
				auto tid = tidgenerator++;
				views.insert(std::make_pair(tid, data));
				return tid;
			}

			inline void removeChangeHandle(int64_t tid)
			{
				auto it = listenerholds.find(tid);
				if (it != listenerholds.end())
				{
					it->second();
					listenerholds.erase(it);
				}
			}
			inline int64_t addChangeHandle(Runnable r)
			{
				auto tid = tidgenerator++;
				listenerholds.insert(std::make_pair(tid, r));
				return tid;
			}

			inline VaraintErrorCollectorPtr getVaraintErrorCollectorPtr() const
			{
				return errorcollector;
			}
		private:
			static std::list<std::string> splitName(const std::string& name)
			{
				std::list<std::string> ss;
				size_t lastpos = 0;
				while (true)
				{
					size_t pos = name.find('.', lastpos);
					if (std::string::npos == pos)
					{
						ss.push_back(name.substr(lastpos));
						break;
					}
					else
					{
						ss.push_back(name.substr(lastpos, pos - lastpos));
						lastpos = pos + 1;
					}
				}
				return ss;
			}

			static void setNameTable(lua_State* L, std::list<std::string>& names, int view)
			{
				if (names.empty())
					return;
				std::string name = names.front();
				names.pop_front();

				if (names.empty())
				{
					lua_pushvalue(L, view);
					lua_setfield(L, -2, name.c_str());
				}
				else
				{
					lua_getfield(L, -1, name.c_str());
					if (lua_isnil(L, -1))
					{
						lua_pop(L, 1);
						lua_newtable(L);
						lua_pushvalue(L, -1);
						lua_setfield(L, -3, name.c_str());
					}
					setNameTable(L, names, view);
					lua_pop(L, 1);
				}
			}

			static void setNameTable(lua_State* L, const std::string& name)
			{
				auto ns = splitName(name);
				int view = lua_gettop(L);
				lua_pushvalue(L, -2);
				setNameTable(L, ns, view);
				lua_pop(L, 2);
			}

			void getViewMetaTable(lua_State* L)
			{
				if (-1 == ref_view_meta_table)
				{
					lua_newtable(L);
					lua_pushcfunction(L, lua_viewMetaToString);
					lua_setfield(L, -2, "__tostring");

					lua_pushcfunction(L, helper::lua_viewIndexFunc);
					lua_setfield(L, -2, "__index");

					lua_pushvalue(L, -1);
					ref_view_meta_table = appendObjectToGlobal(L);
				}
				else
				{
					getObjectFromGlobal(L, ref_view_meta_table);
				}
			}
		public:
			virtual bool createVariantTable(Variant var) override
			{
				int ec = makeVariantTable(lua_state, var);
				if (LUA_SUCCEED != ec)
				{
					errorcollector->fireError(SOURCE_LUA, ec, "makeVariantTable");
					return false;
				}
				return true;
			}
			virtual TemporaryViewHandler* createTemporaryViewHandleTable(TemporaryViewHandler*) override;

			virtual bool createViewTable(VariantView* view) override
			{
				lua_State* L = lua_state;

				auto data = ViewContextDataPtr(new ViewContextData());
				data->weak_impl = instance;
				data->view = view;
				data->tid = addView(data);

				lua_newtable(L);
				lua_pushlightuserdata(L, data.get());
				lua_setfield(L, -2, getLimaxUserDataName());

				getViewMetaTable(L);
				lua_setmetatable(L, -2);

#ifdef LIMAX_DEBUG
				lua_pushlstring(L, view->getViewName().c_str(), view->getViewName().size());
				lua_setfield(L, -2, "__view_name__");
#endif

				lua_pushvalue(L, -1);
				int ref = appendObjectToGlobal(L);
				data->refview = ref;
				limax::Resource::create(resources, view->registerListener(ViewListenerImpl::create(L, ref, errorcollector)));
				return true;
			}

			virtual bool createVariantManagerTable(VariantManager* variantmamanger, QueryTemporaryViewHandler qtvh) override
			{
				lua_State* L = lua_state;
				lua_newtable(L);

				auto gsviewnames = variantmamanger->getSessionOrGlobalViewNames();
				for (auto it = gsviewnames.begin(), ite = gsviewnames.end(); it != ite; ++it)
				{
					const auto& name = *it;
					auto view = variantmamanger->getSessionOrGlobalView(name);
					if (!createViewTable(view))
					{
						lua_pop(L, 1);
						return false;
					}
					setNameTable(L, name);
				}

				auto tempviewnames = variantmamanger->getTemporaryViewNames();
				for (auto it = tempviewnames.begin(), ite = tempviewnames.end(); it != ite; ++it)
				{
					const auto& name = *it;
					auto handle = createTemporaryViewHandleTable(qtvh(name));
					setNameTable(L, name);
					variantmamanger->setTemporaryViewHandler(name, handle);
				}
				return true;
			}

			virtual bool createEndpointManagerTable(EndpointManager* manager, std::shared_ptr<EndpointConfig> config, QueryTemporaryViewHandler qtvh) override
			{
				lua_State* L = lua_state;
				lua_newtable(L);

				const auto& pvids = config->getVariantProviderIds();
				for (auto it = pvids.begin(), ite = pvids.end(); it != ite; ++it)
				{
					auto pvid = *it;
					if (!createVariantManagerTable(VariantManager::getInstance(manager, pvid), qtvh))
					{
						lua_pop(L, 2);
						return false;
					}
					lua_rawseti(L, -2, pvid);
				}

				lua_pushcfunction(L, lua_viewSendMessage);
				lua_setfield(L, -2, "send");

				lua_pushcfunction(L, lua_viewRegisterChangeListener);
				lua_setfield(L, -2, "register");

				lua_pushvalue(L, -1);
				ref_variant_manager = appendObjectToGlobal(L);
				errorcollector->updateVairantManager(ref_variant_manager);

				return true;
			}

			virtual void doClose() override
			{
				lua_State* L = lua_state;
				for (auto it = views.begin(), ite = views.end(); it != ite; ++it)
				{
					auto data = it->second;
					GetGlobalObject getobj(L, data->refview);
					lua_pushnil(L);
					lua_setfield(L, -2, getLimaxUserDataName());
				}
			}
		};

		class TemporaryViewHandleImpl : public TemporaryViewHandler
		{
			TemporaryViewHandler* inner;
			lua_State* L;
			int ref_view_root;
			VaraintErrorCollectorPtr errorcollector;
			std::weak_ptr<LuaVariantContextImpl> weak_impl;
		private:
			TemporaryViewHandleImpl(lua_State* _state, int _ref, VaraintErrorCollectorPtr _errorcollector, TemporaryViewHandler* _inner, std::shared_ptr<LuaVariantContextImpl> _impl)
				: inner(_inner), L(_state), ref_view_root(_ref), errorcollector(_errorcollector), weak_impl(_impl)
			{}
		public:
			virtual ~TemporaryViewHandleImpl()
			{
				releaseObjectFromGlobal(L, ref_view_root);
				if (inner)
					inner->destroy();
			}
		private:
			void fireOnClose(VariantView* view)
			{
				lua_pushstring(L, "onclose");
				lua_gettable(L, -2);
				if (!lua_isfunction(L, -1))
				{
					std::stringstream ss;
					ss << "viewname = " << view->getViewName() << " onclose is nil ";
					errorcollector->fireError(limax::SOURCE_LUA, limax::LUA_CODE_ERROR, ss.str());

					lua_pop(L, 1);
					return;
				}
				lua_pushvalue(L, -2);
				lua_pushinteger(L, view->getInstanceIndex());

				int ec = lua_pcall(L, 2, 0, 0);
				if (LUA_OK != ec)
				{
					const char* msg = lua_tostring(L, -1);

					std::stringstream ss;
					ss << "call onClose viewname = " << view->getViewName() << " instance = " << view->getInstanceIndex() << " error = " << ec << " msg = " << msg;
					errorcollector->fireError(limax::SOURCE_LUA, limax::LUA_CALL_LUA_FUNC_FAILED, ss.str());
					lua_pop(L, 1);
				}
			}
		public:
			virtual void onOpen(VariantView* view, const std::vector<int64_t>& sessionids) override
			{
				if (inner)
					inner->onOpen(view, sessionids);

				auto impl = weak_impl.lock();
				if (!impl)
					return;

				GetGlobalObject _dummy_object(L, ref_view_root);

				impl->createViewTable(view);
				lua_rawseti(L, -2, view->getInstanceIndex());

				lua_pushstring(L, "onopen");
				lua_gettable(L, -2);
				if (!lua_isfunction(L, -1))
				{
					std::stringstream ss;
					ss << "viewname = " << view->getViewName() << " onopen is nil ";
					errorcollector->fireError(limax::SOURCE_LUA, limax::LUA_CODE_ERROR, ss.str());

					lua_pop(L, 1);
					return;
				}
				lua_pushvalue(L, -2);
				lua_pushinteger(L, view->getInstanceIndex());

				lua_newtable(L);
				auto count = sessionids.size();
				for (size_t i = 0; i < count; i++)
				{
					auto sid = sessionids.at(i);
					lua_pushinteger(L, sid);
					lua_rawseti(L, -2, (lua_Integer)(i + 1));
				}

				int ec = lua_pcall(L, 3, 0, 0);
				if (LUA_OK != ec)
				{
					const char* msg = lua_tostring(L, -1);

					std::stringstream ss;
					ss << "call onopen viewname = " << view->getViewName() << " instance = " << view->getInstanceIndex() << " error = " << ec << " msg = " << msg;
					errorcollector->fireError(limax::SOURCE_LUA, limax::LUA_CALL_LUA_FUNC_FAILED, ss.str());
					lua_pop(L, 1);
				}
			}
			virtual void onClose(VariantView* view) override
			{
				if (inner)
					inner->onClose(view);

				auto impl = weak_impl.lock();
				if (!impl)
					return;

				GetGlobalObject _dummy_object(L, ref_view_root);

				fireOnClose(view);

				lua_rawgeti(L, -1, view->getInstanceIndex());
				lua_getfield(L, -1, getLimaxUserDataName());
				auto viewdata = (ViewContextData*)lua_touserdata(L, -1);
				if (nullptr == viewdata)
					return;
				lua_pop(L, 1);
				lua_pushnil(L);
				lua_setfield(L, -2, getLimaxUserDataName());
				lua_pop(L, 1);

				lua_pushnil(L);
				lua_rawseti(L, -2, view->getInstanceIndex());

				impl->removeView(viewdata);
			}
			virtual void onAttach(VariantView* view, int64_t sessionid) override
			{
				if (inner)
					inner->onAttach(view, sessionid);

				GetGlobalObject _dummy_object(L, ref_view_root);

				lua_pushstring(L, "onattach");
				lua_gettable(L, -2);
				if (!lua_isfunction(L, -1))
				{
					std::stringstream ss;
					ss << "viewname = " << view->getViewName() << " onattach is nil ";
					errorcollector->fireError(limax::SOURCE_LUA, limax::LUA_CODE_ERROR, ss.str());

					lua_pop(L, 1);
					return;
				}
				lua_pushvalue(L, -2);
				lua_pushinteger(L, view->getInstanceIndex());
				lua_pushinteger(L, sessionid);

				int ec = lua_pcall(L, 3, 0, 0);
				if (LUA_OK != ec)
				{
					const char* msg = lua_tostring(L, -1);
					std::stringstream ss;
					ss << "call onAttach viewname = " << view->getViewName() << " instance = " << view->getInstanceIndex() << " error = " << ec << " msg = " << msg;
					errorcollector->fireError(limax::SOURCE_LUA, limax::LUA_CALL_LUA_FUNC_FAILED, ss.str());
					lua_pop(L, 1);
				}
			}
			virtual void onDetach(VariantView* view, int64_t sessionid, int reason) override
			{
				if (inner)
					inner->onDetach(view, sessionid, reason);

				GetGlobalObject _dummy_object(L, ref_view_root);

				lua_pushstring(L, "ondetach");
				lua_gettable(L, -2);
				if (!lua_isfunction(L, -1))
				{
					std::stringstream ss;
					ss << "viewname = " << view->getViewName() << " ondetach is nil ";
					errorcollector->fireError(limax::SOURCE_LUA, limax::LUA_CODE_ERROR, ss.str());
					lua_pop(L, 1);
					return;
				}

				lua_pushvalue(L, -2);
				lua_pushinteger(L, view->getInstanceIndex());
				lua_pushinteger(L, sessionid);
				lua_pushinteger(L, reason);

				int ec = lua_pcall(L, 4, 0, 0);
				if (LUA_OK != ec)
				{
					const char* msg = lua_tostring(L, -1);
					std::stringstream ss;
					ss << "call onDetach viewname = " << view->getViewName() << " instance = " << view->getInstanceIndex() << " error = " << ec << " msg = " << msg;
					errorcollector->fireError(limax::SOURCE_LUA, limax::LUA_CALL_LUA_FUNC_FAILED, ss.str());
					lua_pop(L, 1);
				}
			}

			virtual void destroy() override
			{
				delete this;
			}
		public:
			static TemporaryViewHandler* create(lua_State* L, int ref_view, VaraintErrorCollectorPtr errorcollector, TemporaryViewHandler* inner, std::shared_ptr<LuaVariantContextImpl> impl)
			{
				return new TemporaryViewHandleImpl(L, ref_view, errorcollector, inner, impl);
			}
		};

		TemporaryViewHandler* LuaVariantContextImpl::createTemporaryViewHandleTable(TemporaryViewHandler* inner)
		{
			lua_State* L = lua_state;
			auto impl = instance.lock();
			if (!impl)
			{
				errorcollector->fireError(limax::SOURCE_LUA, limax::LUA_ILLEGAL_ARGUMENT, "createTemporaryViewHandleTable");
				return nullptr;
			}

			lua_newtable(L);
			lua_pushvalue(L, -1);
			int ref = appendObjectToGlobal(L);
			return TemporaryViewHandleImpl::create(L, ref, errorcollector, inner, impl);
		}

		int lua_viewUnregisterChangeListener(lua_State* L)
		{
			lua_getfield(L, 1, getLimaxUserDataName());
			auto viewdata = (ViewContextData*)lua_touserdata(L, -1);
			if (nullptr == viewdata)
			{
				luaL_error(L, "nullptr == ViewContextData");
				return 0;
			}
			lua_pop(L, 1);
			int64_t tid = lua_tointeger(L, 2);
			auto impl = viewdata->weak_impl.lock();
			if (!impl)
			{
				luaL_error(L, "endpoint manager closed");
				return 0;
			}
			impl->removeChangeHandle(tid);
			return 0;
		}

		int lua_viewRegisterChangeListener(lua_State* L)
		{
			if (!lua_istable(L, 1))
			{
				luaL_error(L, "arg 1 should be view ( not table)");
				return 0;
			}
			lua_getfield(L, 1, getLimaxUserDataName());
			if (!lua_isuserdata(L, -1))
			{
				luaL_error(L, "arg 1 shoule be view ( lost userdata)");
				return 0;
			}
			auto viewdata = (ViewContextData*)lua_touserdata(L, -1);
			if (nullptr == viewdata)
			{
				luaL_error(L, "nullptr == ViewContextData");
				return 0;
			}
			lua_pop(L, 1);
			auto varname = lua_check_string(L, 2);
			if (!lua_isfunction(L, 3))
			{
				luaL_error(L, "arg 3 should be callback function");
				return 0;
			}

			auto impl = viewdata->weak_impl.lock();
			if (!impl)
			{
				luaL_error(L, "endpoint manager closed");
				return 0;
			}

			lua_pushvalue(L, 3);
			int ref_listern_func = appendObjectToGlobal(L);

			auto handle = viewdata->view->registerListener(varname, ViewListenerImpl::create(L, viewdata->refview, ref_listern_func, impl->getVaraintErrorCollectorPtr()));
			int64_t tid = impl->addChangeHandle(handle);
			int ec = luaL_dostring(L, lua_code_string_unregisterlistener);
			if (LUA_OK != ec)
			{
				const char* msg = lua_tostring(L, -1);
				luaL_error(L, "%s", msg);
			}
			lua_pushcfunction(L, lua_viewUnregisterChangeListener);
			lua_pushvalue(L, 1);
			lua_pushinteger(L, tid);
			ec = lua_pcall(L, 3, 1, 0);
			if (LUA_OK != ec)
			{
				const char* msg = lua_tostring(L, -1);
				luaL_error(L, "%s", msg);
			}
			return 1;
		}
	} // namespace helper {
} // namespace limax {

namespace limax {

	LuaVariantContext::LuaVariantContext() {}
	LuaVariantContext::~LuaVariantContext() {}

	LuaCreator::LuaCreator() {}
	LuaCreator::~LuaCreator() {}

	ScriptEngineHandlePtr LuaCreator::createScriptEngineHandle(lua_State* L, int table, bool fastscript, std::vector<int> providers, DictionaryCachePtr cache, TunnelReceiver ontunnel, ScriptErrorCollector sec)
	{
		auto handle = std::shared_ptr<helper::LuaScriptHandleImpl>(new helper::LuaScriptHandleImpl());
		return handle->initialize(L, table, fastscript, providers, cache, ontunnel, sec) ? handle : nullptr;
	}

	ScriptEngineHandlePtr LuaCreator::createScriptEngineHandle(lua_State* L, int table, bool fastscript, std::vector<int> providers, DictionaryCachePtr cache, ScriptErrorCollector sec)
	{
		return createScriptEngineHandle(L, table, fastscript, providers, cache, nullptr, sec);
	}

	ScriptEngineHandlePtr LuaCreator::createScriptEngineHandle(lua_State* L, int table, bool fastscript, DictionaryCachePtr cache, ScriptErrorCollector sec)
	{
		return createScriptEngineHandle(L, table, fastscript, std::vector<int>(), cache, nullptr, sec);
	}

	ScriptEngineHandlePtr LuaCreator::createScriptEngineHandle(lua_State* L, int table, bool fastscript, std::vector<int> providers, ScriptErrorCollector sec)
	{
		return createScriptEngineHandle(L, table, fastscript, providers, nullptr, nullptr, sec);
	}

	ScriptEngineHandlePtr LuaCreator::createScriptEngineHandle(lua_State* L, int table, bool fastscript, ScriptErrorCollector sec)
	{
		return createScriptEngineHandle(L, table, fastscript, std::vector<int>(), nullptr, nullptr, sec);
	}

	LuaVariantContextPtr LuaCreator::createLuaVariantContext(lua_State* L, int ref_callback)
	{
		auto ptr = std::shared_ptr<helper::LuaVariantContextImpl>(new helper::LuaVariantContextImpl(L, ref_callback));
		ptr->instance = ptr;
		return ptr;
	}

} // namespace limax {
