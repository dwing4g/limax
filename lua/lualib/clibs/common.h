#pragma once

#include <limax.h>

#ifdef LIMAX_LUA_USE_EXTERNAL_LUA_LIB
extern "C" {
#include <lualib.h>
#include <lauxlib.h>
} // extern "C" {
#ifndef LUA_OK
#define LUA_OK		0
#endif

#else // #ifdef LIMAX_LUA_USE_EXTERNAL_LUA_LIB
#define __LUA_VERSION_53
extern "C" {
#include "../src/lualib.h"
#include "../src/lauxlib.h"
} // extern "C" {
#endif // else #ifdef LIMAX_LUA_USE_EXTERNAL_LUA_LIB

#ifndef __LUA_VERSION_53

inline size_t lua_rawlen(lua_State *L, int idx)
{
	return lua_objlen( L, idx);
}

inline void lua_pushunsigned(lua_State* L, int64_t value)
{
	auto str = std::to_string(value);
	lua_pushstring( L, str.c_str());
}

inline int64_t luaL_checkunsigned(lua_State* L, int idx)
{
	return (int64_t)std::stoll(luaL_checkstring(L, idx));
}

inline int64_t lua_tounsigned(lua_State* L, int idx)
{
	return (int64_t)std::stoll(lua_tostring(L, idx));
}

inline int lua_isinteger(lua_State *L, int idx)
{
	return lua_isnumber(L, idx);
}

#endif

#include "../include/limax.lua.h"

#include <sstream>
