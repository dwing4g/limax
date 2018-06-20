#pragma once

inline const char* getLimaxUserDataName()
{
	return "__limax_user_data__";
}

#define stackdump( L) stackdump_line( L, __FILE__, __LINE__)
#define vardump( L, arg) vardump_line( L, (arg), __FILE__, __LINE__)

void stackdump_line(lua_State* L, const char* filename, int line);
void vardump_line(lua_State* L, int arg, const char* filename, int line);

inline std::string lua_check_string(lua_State* L, int arg)
{
	size_t l = 0;
	const char* s = luaL_checklstring(L, arg, &l);
	return std::string(s, l);
}

int appendObjectToGlobal(lua_State* L);
void getObjectFromGlobal(lua_State* L, int ref);
void releaseObjectFromGlobal(lua_State* L, int ref);

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

struct GetGlobalObject
{
	lua_State* m_L;
	GetGlobalObject(lua_State* L, int ref);
	~GetGlobalObject();
};

namespace helper {

	void helper_handlerError(lua_State* L, int errortype, int errorvalue, const std::string& info);

} // namespace helper {

void fireNoitfyNoParam(lua_State* L, int ref, const std::string& funcname, limax::ScriptErrorCollector sec);
void fireErrorOccured(lua_State* L, int ref, int errortype, int errorvalue, const std::string& info);

template<class T> inline void pushParam(lua_State* L, const T& v)
{
	luaL_error(L, "unsupported input type");
}

template<> inline void pushParam(lua_State* L, const int& v)
{
	lua_pushinteger(L, v);
}

template<> inline void pushParam(lua_State* L, const std::string& v)
{
	lua_pushlstring(L, v.c_str(), v.size());
}

template<class T1> void fireNoitfyOneParam(lua_State* L, int ref, const std::string& funcname, const T1& p1, limax::ScriptErrorCollector sec)
{
	if (-1 == ref)
		return;

	GetGlobalObject _dummy_object(L, ref);

	if (!lua_istable(L, -1))
		return;

	lua_pushstring(L, funcname.c_str());
	lua_gettable(L, -2);
	if (!lua_isfunction(L, -1))
	{
		lua_pop(L, 1);
		return;
	}

	pushParam<T1>(L, p1);
	int ec = lua_pcall(L, 1, 0, 0);
	if (LUA_OK != ec)
	{
		const char* msg = lua_tostring(L, -1);
		std::stringstream ss;
		ss << "call " << funcname << " error = " << ec << " msg = " << msg;
		sec(limax::SOURCE_LUA, limax::LUA_CALL_LUA_FUNC_FAILED, ss.str());
		lua_pop(L, 1);
	}
}

template<class T1, class T2> void fireNoitfyTwoParam(lua_State* L, int ref, const std::string& funcname, const T1& p1, const T2& p2, limax::ScriptErrorCollector sec)
{
	if (-1 == ref)
		return;

	GetGlobalObject _dummy_object(L, ref);

	if (!lua_istable(L, -1))
		return;

	lua_pushstring(L, funcname.c_str());
	lua_gettable(L, -2);
	if (!lua_isfunction(L, -1))
	{
		lua_pop(L, 1);
		return;
	}

	pushParam<T1>(L, p1);
	pushParam<T2>(L, p2);
	int ec = lua_pcall(L, 2, 0, 0);
	if (LUA_OK != ec)
	{
		const char* msg = lua_tostring(L, -1);

		std::stringstream ss;
		ss << "call " << funcname << " error = " << ec << " msg = " << msg;
		sec(limax::SOURCE_LUA, limax::LUA_CALL_LUA_FUNC_FAILED, ss.str());
		lua_pop(L, 1);
	}
}

template<class T1, class T2, class T3> void fireNoitfyThreeParam(lua_State* L, int ref, const std::string& funcname, const T1& p1, const T2& p2, const T3& p3, limax::ScriptErrorCollector sec)
{
	if (-1 == ref)
		return;

	GetGlobalObject _dummy_object(L, ref);

	if (!lua_istable(L, -1))
		return;

	lua_pushstring(L, funcname.c_str());
	lua_gettable(L, -2);
	if (!lua_isfunction(L, -1))
	{
		lua_pop(L, 1);
		return;
	}

	pushParam<T1>(L, p1);
	pushParam<T2>(L, p2);
	pushParam<T3>(L, p3);
	int ec = lua_pcall(L, 3, 0, 0);
	if (LUA_OK != ec)
	{
		const char* msg = lua_tostring(L, -1);

		std::stringstream ss;
		ss << "call " << funcname << " error = " << ec << " msg = " << msg;
		sec(limax::SOURCE_LUA, limax::LUA_CALL_LUA_FUNC_FAILED, ss.str());
		lua_pop(L, 1);
	}
}

struct DictionaryCachePtrGCWrapper
{
	limax::DictionaryCachePtr cache;
	DictionaryCachePtrGCWrapper(limax::DictionaryCachePtr _cache) : cache(_cache) {}
};

struct TunnelReceiverGCWrapper
{
	limax::TunnelReceiver ontunnel;
	TunnelReceiverGCWrapper(limax::TunnelReceiver _ontunnel) : ontunnel(_ontunnel) { }
};

