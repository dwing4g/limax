#include "common.h"
#include "luahelper.h"

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
namespace dump {

	static const int max_dump_tabs = 9;

	void outputTabs(int tabs)
	{
		for (int i = 0; i < tabs; i++)
			printf("\t");
	}

	void dump_table(lua_State* L, int arg, bool dumptabs, int tabs);

	void dump_value(lua_State* L, int arg, bool dumptabs, int tabs)
	{
		if (dumptabs)
			outputTabs(tabs);
		int type = lua_type(L, arg);
		switch (type)
		{
		case LUA_TSTRING:
			printf("string:%s", lua_tostring(L, arg));
			break;
		case LUA_TBOOLEAN:
			printf("bool:%s", lua_toboolean(L, arg) ? "true" : "false");
			break;
		case LUA_TNUMBER:
			printf("number:%g", lua_tonumber(L, arg));
			break;
		case LUA_TTABLE:
			dump_table(L, arg, false, tabs);
			break;
		default:
			printf("%s[%d]", lua_typename(L, type), type);
			break;
		}
	}

	void dump_table(lua_State* L, int arg, bool dumptabs, int tabs)
	{
		if (dumptabs)
			outputTabs(tabs);
		printf("table(%d):\n", tabs);
		tabs++;
		if (tabs > max_dump_tabs)
			return;
		if (arg < 0)
			arg = lua_gettop(L) + arg + 1;
		lua_pushnil(L);
		while (lua_next(L, arg))
		{
			outputTabs(tabs);
			printf("\tkey = ");
			dump_value(L, -2, false, tabs);
			printf("\tvalue = ");
			dump_value(L, -1, false, tabs);
			printf("\n");
			lua_pop(L, 1);
		}
	}

} // namespace dump {

void stackdump_line(lua_State* L, const char* filename, int line)
{
	int top = lua_gettop(L);
	printf("stack dump %d : %s ( %d)\n", top, filename, line);
	for (int i = 1; i <= top; i++)
	{
		dump::dump_value(L, i, true, 1);
		printf("\n");
	}
	printf("done\n\n");
}

void vardump_line(lua_State* L, int arg, const char* filename, int line)
{
	printf("var dump %d : %s ( %d)\n", arg, filename, line);
	dump::dump_value(L, arg, true, 1);
}

int appendObjectToGlobal(lua_State* L)
{
	return luaL_ref(L, LUA_REGISTRYINDEX);
}

void releaseObjectFromGlobal(lua_State* L, int ref)
{
	if (-1 != ref)
		luaL_unref(L, LUA_REGISTRYINDEX, ref);
}

void getObjectFromGlobal(lua_State* L, int ref)
{
	lua_rawgeti(L, LUA_REGISTRYINDEX, ref);
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

GetGlobalObject::GetGlobalObject(lua_State* L, int ref)
: m_L(L)
{
	lua_rawgeti(L, LUA_REGISTRYINDEX, ref);
}

GetGlobalObject::~GetGlobalObject()
{
	lua_pop(m_L, 1);
}


void fireNoitfyNoParam(lua_State* L, int ref, const std::string& funcname, limax::ScriptErrorCollector sec)
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

	int ec = lua_pcall(L, 0, 0, 0);
	if (LUA_OK != ec)
	{
		const char* msg = lua_tostring(L, -1);
		if (nullptr == msg)
		{
			vardump(L, -1);
			msg = "";
		}
		std::stringstream ss;
		ss << "call " << funcname << " error = " << ec << " msg = " << msg;
		sec(limax::SOURCE_LUA, limax::LUA_CALL_LUA_FUNC_FAILED, ss.str());

		lua_pop(L, 1);
	}
}

void fireErrorOccured(lua_State* L, int ref, int errortype, int errorvalue, const std::string& info)
{
	if (-1 == ref)
		return;
	GetGlobalObject _dummy_object(L, ref);

	if (!lua_istable(L, -1))
		return;

	lua_pushstring(L, "onErrorOccured");
	lua_gettable(L, -2);
	if (!lua_isfunction(L, -1))
	{
		lua_pop(L, 1);
		return;
	}

	pushParam(L, errortype);
	pushParam(L, errorvalue);
	pushParam(L, info);
	int ec = lua_pcall(L, 3, 0, 0);
	if (LUA_OK != ec)
		lua_pop(L, 1);
}