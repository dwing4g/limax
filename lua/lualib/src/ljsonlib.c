#define ljsonlib_c
#define LUA_LIB

#include "lprefix.h"


#include <assert.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>

#include "lua.h"

#include "lauxlib.h"
#include "lualib.h"

static const char* parseS(lua_State *L, const char *s, const char *e);
static const char* parseV(lua_State *L, const char *s, const char *e);

static const char* parseT(lua_State *L, const char *s, const char *e) {
	for (;; s++)
		if (s < e){
			switch (*s) {
			case ' ':
			case '\f':
			case '\n':
			case '\r':
			case '\t':
			case '\v':
				break;
			default:
				return s;
			}
		}
		else
			luaL_error(L, "insufficient input string");
}

static const char* parseO(lua_State *L, const char *s, const char *e) {
	lua_newtable(L);
	while (1) {
		while (1) {
			s = parseT(L, s, e);
			if (*s == '"') {
				s = parseS(L, s + 1, e);
				break;
			}
			else if (*s == '}')
				return s + 1;
			else if (*s == ',' || *s == ';')
				s = s + 1;
			else
				luaL_error(L, "want string or '}'");
		}
		s = parseT(L, s, e);
		if (*s == ':' || *s == '=') {
			s = parseV(L, s + 1, e);
			lua_rawset(L, -3);
		}
		else
			luaL_error(L, "want [:=]");
	}
}

static const char* parseA(lua_State *L, const char *s, const char *e) {
	int n = 0;
	lua_newtable(L);
	while (1) {
		s = parseT(L, s, e);
		if (*s == ']')
			return s + 1;
		else if (*s == ',' || *s == ';')
			s = s + 1;
		else {
			s = parseV(L, s, e);
			lua_rawseti(L, -2, ++n);
		}
	}
}

static int parseC(lua_State *L, char c) {
	if (c >= '0' && c <= '9')
		return c - '0';
	if (c >= 'A' && c <= 'F')
		return c - 'A' + 10;
	if (c >= 'a' && c <= 'f')
		return c - 'a' + 10;
	luaL_error(L, "invalid hex");
	return 0;
}

static const char* parseS(lua_State *L, const char *s, const char *e) {
	size_t length = 0, total = 16;
	char c, *buf = (char *)malloc(total);
	int cp, ls;
	while (1) {
		if (s >= e) {
			free(buf);
			luaL_error(L, "insufficient input string");
		}
		c = *s++;
		if (c == '"') {
			lua_pushlstring(L, buf, length);
			free((void*)buf);
			return s;
		}
		if (c == '\\') {
			if (s >= e) {
				free(buf);
				luaL_error(L, "insufficient input string");
			}
			c = *s++;
			switch (c) {
			case '"':
			case '\\':
			case '/':
				break;
			case 'b':
				c = '\b';
				break;
			case 'f':
				c = '\f';
				break;
			case 'n':
				c = '\n';
				break;
			case 'r':
				c = '\r';
				break;
			case 't':
				c = '\t';
				break;
			case 'u':
				if (s + 3 >= e) {
					free(buf);
					luaL_error(L, "insufficient input string");
				}
				cp = parseC(L, *s++) << 12;
				cp |= parseC(L, *s++) << 8;
				cp |= parseC(L, *s++) << 4;
				cp |= parseC(L, *s++);
				if (cp < 0x80) {
					c = (char)cp;
					break;
				}
				if (cp >= 0x80 && cp <= 0x7ff) {
					if (total - length <= 1)
						buf = (char *)realloc((void*)buf, total *= 2);
					buf[length++] = (char)(((cp >> 6) & 0x1f) | 0xc0);
					buf[length++] = (char)((cp & 0x3f) | 0x80);
					continue;
				}
				if (cp >= 0xd800 && cp <= 0xdbff) {
					if (s + 5 >= e) {
						free(buf);
						luaL_error(L, "insufficient input string");
					}
					if (*s++ != '\\' || *s++ != 'u') {
						free(buf);
						luaL_error(L, "error low-surrogates");
					}
					ls = parseC(L, *s++) << 12;
					ls |= parseC(L, *s++) << 8;
					ls |= parseC(L, *s++) << 4;
					ls |= parseC(L, *s++);
					cp = (((cp - 0xd800) << 10) | (ls - 0xdc00)) + 0x10000;
					if (total - length <= 3)
						buf = (char *)realloc((void*)buf, total *= 2);
					buf[length++] = (char)((cp >> 18) | 0xf0);
					buf[length++] = (char)(((cp >> 12) & 0x3f) | 0x80);
					buf[length++] = (char)(((cp >> 6) & 0x3f) | 0x80);
					buf[length++] = (char)((cp & 0x3f) | 0x80);
					continue;
				}
				if (total - length <= 2)
					buf = (char *)realloc((void*)buf, total *= 2);
				buf[length++] = (char)(((cp >> 12) & 0x0f) | 0xe0);
				buf[length++] = (char)(((cp >> 6) & 0x3f) | 0x80);
				buf[length++] = (char)((cp & 0x3f) | 0x80);
				continue;
			default:
				free((void *)buf);
				luaL_error(L, "unsupported escape character <%c>", c);
			}
		}
		if (length == total)
			buf = (char *)realloc((void*)buf, total *= 2);
		buf[length++] = c;
	}
}

static const char* parseN(lua_State *L, const char *s, const char *e) {
	lua_Number v = strtod(s, (char **)&e);
	if (v - (lua_Integer)v == 0.0)
		lua_pushinteger(L, (lua_Integer)v);
	else
		lua_pushnumber(L, v);
	return e;
}

static const char* parseV(lua_State *L, const char *s, const char *e) {
	if (s < e) {
		s = parseT(L, s, e);
		switch (*s) {
		case '{':
			return parseO(L, s + 1, e);
		case '[':
			return parseA(L, s + 1, e);
		case '"':
			return parseS(L, s + 1, e);
		case '0':
		case '1':
		case '2':
		case '3':
		case '4':
		case '5':
		case '6':
		case '7':
		case '8':
		case '9':
		case '-':
			s = parseN(L, s, e);
			break;
		case 't':
		case 'T':
			if (e - s > 3 && (s[1] == 'r' || s[1] == 'R') && (s[2] == 'u' || s[2] == 'U') && (s[3] == 'e' || s[3] == 'E')) {
				lua_pushboolean(L, 1);
				return s + 4;
			}
			else
				luaL_error(L, "true");
			break;
		case 'f':
		case 'F':
			if (e - s > 4 && (s[1] == 'a' || s[1] == 'A') && (s[2] == 'l' || s[2] == 'L') && (s[3] == 's' || s[3] == 'S') && (s[4] == 'e' || s[4] == 'E')) {
				lua_pushboolean(L, 0);
				return s + 5;
			}
			else
				luaL_error(L, "false");
			break;
		case 'n':
		case 'N':
			if (e - s > 3 && (s[1] == 'u' || s[1] == 'U') && (s[2] == 'l' || s[2] == 'L') && (s[3] == 'l' || s[3] == 'L')) {
				lua_pushnil(L);
				return s + 4;
			}
			else
				luaL_error(L, "null");
			break;
		default:
			luaL_error(L, "unexpected token %c ", *s);
		}
	}
	else
		lua_pushnil(L);
	return s;
}

static int parse(lua_State *L) {
	size_t length;
	const char *s;
	const char *e;
	if (!lua_isstring(L, -1))
		luaL_error(L, "not json string");
	s = lua_tolstring(L, -1, &length);
	e = s + length;
	for (s = parseV(L, s, e); s < e; s++) {
		switch (*s){
		case ' ':
		case '\f':
		case '\n':
		case '\r':
		case '\t':
		case '\v':
			break;
		default:
			luaL_error(L, "unexpected token %c ", *s);
		}
	}
	return 1;
}

#define FREEABLE 0x80000000
#define LENMASK 0x7fffffff

struct piece {
	size_t total;
	size_t count;
	size_t length;
	size_t total_mark;
	size_t count_mark;
	const char **slice;
	size_t *slice_len;
	const void **mark;
	char *join;
};

static struct piece *piece_alloc() {
	struct piece* p = (struct piece*)malloc(sizeof(struct piece));
	p->total = 16;
	p->count = 0;
	p->length = 0;
	p->total_mark = 16;
	p->count_mark = 0;
	p->slice = (const char **)malloc(p->total * sizeof(const char *));
	p->slice_len = (size_t *)malloc(p->total * sizeof(size_t));
	p->mark = (const void **)malloc(p->total_mark * sizeof(const void *));
	p->join = NULL;
	return p;
}

static void piece_free(struct piece *p) {
	size_t i;
	for (i = 0; i < p->count; i++)
		if (p->slice_len[i] & FREEABLE)
			free((void*)p->slice[i]);
	free((void*)p->slice);
	free((void*)p->slice_len);
	free((void*)p->mark);
	free((void*)p->join);
	free(p);
}

static int piece_mark(struct piece *p, const void *v) {
	size_t i;
	for (i = 0; i < p->count_mark; i++)
		if (p->mark[i] == v)
			return 0;
	if (p->count_mark == p->total_mark) {
		p->total_mark *= 2;
		p->mark = (const void **)realloc((void *)p->mark, p->total_mark * sizeof(const void *));
	}
	p->mark[p->count_mark++] = v;
	return 1;
}

static void piece_append(struct piece *p, const char *s, size_t l) {
	if (p->count == p->total) {
		p->total *= 2;
		p->slice = (const char **)realloc((void*)p->slice, p->total * sizeof(const char *));
		p->slice_len = (size_t *)realloc((void*)p->slice_len, p->total * sizeof(size_t));
	}
	p->slice[p->count] = s;
	p->slice_len[p->count++] = l;
	p->length += l & LENMASK;
}

static void piece_append_string_convert(struct piece *p, lua_State *L, int index) {
	size_t l;
	const char *s = luaL_tolstring(L, index, &l);
	char *r = (char *)malloc(l);
	memcpy(r, s, l);
	lua_pop(L, 1);
	piece_append(p, r, l | FREEABLE);
}

static void piece_append_string(struct piece *p, lua_State *L, int index) {
	size_t l;
	const char *s;
	if (lua_type(L, index) == LUA_TSTRING) {
		s = lua_tolstring(L, index, &l);
		piece_append(p, s, l);
	}
	else
		piece_append_string_convert(p, L, index);
}

static char *piece_join(struct piece *p, size_t *l) {
	size_t i, length = 0;
	free(p->join);
	p->join = (char *)malloc(*l = p->length);
	for (i = 0, length = 0; i < p->count; length += p->slice_len[i++] & LENMASK)
		memcpy(p->join + length, p->slice[i], p->slice_len[i] & LENMASK);
	return p->join;
}

static void _stringify(lua_State *L, struct piece *p) {
	static char *conchars = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
	lua_Integer i, len;
	size_t size;
	const char *s;
	int comma;
	switch (lua_type(L, -1)) {
	case LUA_TNIL:
		piece_append(p, "null", 4);
		break;
	case LUA_TTABLE:
		if (!piece_mark(p, lua_topointer(L, -1))) {
			piece_free(p);
			luaL_error(L, "loop detected");
			return;
		}
		lua_len(L, -1);
		len = lua_tointeger(L, -1);
		lua_pop(L, 1);
		comma = 0;
		if (len > 0) {
			piece_append(p, "[", 1);
			for (i = 1; i <= len; i++) {
				if (comma)
					piece_append(p, ",", 1);
				lua_geti(L, -1, i);
				_stringify(L, p);
				lua_pop(L, 1);
				comma = 1;
			}
			piece_append(p, "]", 1);
		}
		else{
			piece_append(p, "{", 1);
			lua_pushnil(L);
			while (lua_next(L, -2) != 0) {
				if (comma)
					piece_append(p, ",", 1);
				piece_append(p, "\"", 1);
				piece_append_string(p, L, -2);
				piece_append(p, "\":", 2);
				_stringify(L, p);
				lua_pop(L, 1);
				comma = 1;
			}
			piece_append(p, "}", 1);
		}
		break;
	case LUA_TSTRING:
		piece_append(p, "\"", 1);
		for (s = lua_tolstring(L, -1, &size); size-- > 0; s++) {
			switch (*s)
			{
			case '\"':
				piece_append(p, "\\\"", 2);
				break;
			case '\\':
				piece_append(p, "\\\\", 2);
				break;
			case '\b':
				piece_append(p, "\\b", 2);
				break;
			case '\f':
				piece_append(p, "\\f", 2);
				break;
			case '\n':
				piece_append(p, "\\n", 2);
				break;
			case '\r':
				piece_append(p, "\\r", 2);
				break;
			case '\t':
				piece_append(p, "\\t", 2);
				break;
			default:
				if (*s < ' ') {
					piece_append(p, "\\u00", 4);
					piece_append(p, conchars + (*s << 1), 2);
				}
				else
					piece_append(p, s, 1);
				break;
			}
		}
		piece_append(p, "\"", 1);
		break;
	case LUA_TNUMBER:
		piece_append_string_convert(p, L, -1);
		break;
	case LUA_TBOOLEAN:
		if (lua_toboolean(L, -1))
			piece_append(p, "true", 4);
		else
			piece_append(p, "false", 5);
		break;
	default:
		piece_append(p, "\"", 1);
		piece_append_string_convert(p, L, -1);
		piece_append(p, "\"", 1);
	}
}

static int stringify(lua_State *L) {
	size_t length;
	struct piece *p = piece_alloc();
	if (lua_gettop(L) == 0)
		lua_pushnil(L);
	_stringify(L, p);
	char *s = piece_join(p, &length);
	lua_pushlstring(L, s, length);
	piece_free(p);
	return 1;
}

static const luaL_Reg funcs[] = {
	{ "parse", parse },
	{ "stringify", stringify },
	{ NULL, NULL }
};

LUAMOD_API int luaopen_JSON(lua_State *L) {
	luaL_newlib(L, funcs);
	return 1;
}

