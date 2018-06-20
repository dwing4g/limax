#include "common.h"
#include "luahelper.h"

namespace limax{
	namespace script{

		struct ScriptException
		{
			std::string message;
			ScriptException(int32_t _line)
				: message("ScriptException __LINE__ = " + std::to_string(_line))
			{}
		};

#define THROW_SCRIPT_EXCEPTION throw ScriptException(__LINE__)

		struct _ScriptObject
		{
			virtual ~_ScriptObject(){}
			virtual void update(lua_State *L) { lua_pushnil(L); }
		};
		typedef std::shared_ptr<_ScriptObject> ScriptObject;

		struct Parser
		{
			const char *p;
			const std::vector<std::string>* pdict;

			ScriptObject action();

			ScriptObject action(const char *_p)
			{
				if (!_p)
					THROW_SCRIPT_EXCEPTION;
				p = _p;
				return action();
			}

			void installDict(const std::vector<std::string>& _dict)
			{
				pdict = &_dict;
			}

			const std::string& translate(int64_t f)
			{
				decltype(pdict->size()) i = decltype(pdict->size())(f);
				if (i < 0 || i >= pdict->size())
					THROW_SCRIPT_EXCEPTION;
				return (*pdict)[i];
			}
		};

		struct ___I : _ScriptObject
		{
			int64_t value;
			___I(Parser *parser, int base = 36) : value(strtoll(parser->p, (char **)&parser->p, base))
			{
				if (*parser->p++ != ':')
					THROW_SCRIPT_EXCEPTION;
			}

			virtual void update(lua_State *L)
			{
				lua_pushinteger(L, value);
			}
		};
		typedef std::shared_ptr<___I> __I;

		struct ___F : _ScriptObject
		{
			double value;
			___F(Parser *parser) : value(strtod(parser->p, (char **)&parser->p))
			{
				if (*parser->p++ != ':')
					THROW_SCRIPT_EXCEPTION;
			}

			virtual void update(lua_State *L)
			{
				lua_pushnumber(L, value);
			}
		};
		typedef std::shared_ptr<___F> __F;

		struct ___B : _ScriptObject
		{
			bool value;
			___B(Parser *parser) : value(*parser->p == 'T')
			{
				if (*parser->p++ == 0)
					THROW_SCRIPT_EXCEPTION;
			}

			virtual void update(lua_State *L)
			{
				lua_pushboolean(L, value ? 1 : 0);
			}
		};
		typedef std::shared_ptr<___B> __B;

		struct ___D : _ScriptObject
		{
			___D(Parser *parser){}
		};
		typedef std::shared_ptr<___D> __D;

		struct ___S : _ScriptObject
		{
			std::string value;
			___S(Parser *parser)
			{
				int64_t l = ___I(parser).value;
				const char *q = parser->p;
				while (l > 0)
				{
					unsigned char c = (unsigned char)*parser->p;
					if (c > 251)
					{
						parser->p += 6;
						l--;
					}
					else if (c > 247)
					{
						parser->p += 5;
						l--;
					}
					else if (c > 239)
					{
						parser->p += 4;
						l--;
					}
					else if (c > 223)
					{
						parser->p += 3;
						l--;
					}
					else if (c > 191)
					{
						parser->p += 2;
						l--;
					}
					else if (c > 127)
					{
						parser->p++;
					}
					else if (c == 0)
					{
						THROW_SCRIPT_EXCEPTION;
					}
					else
					{
						parser->p++;
						l--;
					}
				}
				value = std::string(q, parser->p);
			}

			virtual void update(lua_State *L)
			{
				lua_pushstring(L, value.c_str());
			}
		};
		typedef std::shared_ptr<___S> __S;

		struct ___O : _ScriptObject
		{
			std::vector<int16_t> value;
			int16_t get(const char *p)
			{
				unsigned char v = (unsigned char)*p++;
				if (v >= '0' && v <= '9')
					return v - '0';
				else if (v >= 'A' && v <= 'F')
					return v - 'A' + 10;
				else if (v >= 'a' && v <= 'f')
					return v - 'a' + 10;
				THROW_SCRIPT_EXCEPTION;
			}
			___O(Parser *parser)
			{
				for (int64_t i = ___I(parser).value; i-- > 0;)
				{
					int16_t h = get(parser->p);
					int16_t l = get(parser->p);
					value.push_back(h * 16 + l);
				}
			}

			virtual void update(lua_State *L)
			{
				lua_newtable(L);
				lua_Integer i = 0;
				for (auto v : value)
				{
					lua_pushinteger(L, v);
					lua_rawseti(L, -2, ++i);
				}
			}
		};
		typedef std::shared_ptr<___O> __O;

		struct ___P : _ScriptObject
		{
			std::vector<ScriptObject> value;
			___P(Parser* parser)
			{
				while (*(parser->p) != ':')
					value.push_back(parser->action());
				(parser->p)++;
			}

			virtual void update(lua_State *L)
			{
				lua_newtable(L);
				lua_Integer i = 0;
				for (auto v : value)
				{
					v->update(L);
					lua_rawseti(L, -2, ++i);
				}
			}
		};
		typedef std::shared_ptr<___P> __P;

		struct ___V : _ScriptObject
		{
			std::pair<std::string, ScriptObject> value;
			___V(Parser *parser)
			{
				value.first = parser->translate(strtol(parser->p, (char **)&parser->p, 36));
				parser->p++;
				value.second = parser->action();
			}

			virtual void update(lua_State *L)
			{
				lua_pushstring(L, value.first.c_str());
				value.second->update(L);
				lua_rawset(L, -3);
			}
		};
		typedef std::shared_ptr<___V> __V;

		struct ___L : _ScriptObject
		{
			std::vector<__V> value;
			___L(Parser *parser)
			{
				while (*parser->p != ':')
				{
					__V v = std::dynamic_pointer_cast<___V> (parser->action());
					if (!v)
						THROW_SCRIPT_EXCEPTION;
					value.push_back(v);
				}
				parser->p++;
			}

			virtual void update(lua_State *L)
			{
				lua_newtable(L);
				for (auto v : value)
					v->update(L);
			}
		};
		typedef std::shared_ptr<___L> __L;

		struct ___W : _ScriptObject
		{
			std::vector<ScriptObject> value;
			___W(Parser *parser) : value(___P(parser).value)
			{
			}
		};
		typedef std::shared_ptr<___W> __W;

		struct ___X : _ScriptObject
		{
			std::string f;
			ScriptObject v;
			___X(Parser *parser) : f(parser->translate(___I(parser).value)), v(parser->action())
			{
			}
		};
		typedef std::shared_ptr<___X> __X;

		struct ___Y : _ScriptObject
		{
			std::string f;
			std::vector<ScriptObject> a;
			std::vector<ScriptObject> r;
			___Y(Parser *parser) : f(parser->translate(___I(parser).value))
			{
				__P _a = std::dynamic_pointer_cast<___P>(parser->action());
				if (!_a)
					THROW_SCRIPT_EXCEPTION;
				a.swap(_a->value);
				__P _r = std::dynamic_pointer_cast<___P>(parser->action());
				if (!_r)
					THROW_SCRIPT_EXCEPTION;
				r.swap(_r->value);
			}
		};
		typedef std::shared_ptr<___Y> __Y;

		struct ___M : _ScriptObject
		{
			std::vector<std::pair<ScriptObject, ScriptObject>> value;
			___M(Parser *parser)
			{
				while (*parser->p != ':'){
					ScriptObject k = parser->action();
					ScriptObject v = parser->action();
					value.push_back(std::make_pair(k, v));
				}
				parser->p++;
			}

			virtual void update(lua_State *L)
			{
				lua_newtable(L);
				for (auto v : value)
				{
					v.first->update(L);
					v.second->update(L);
					lua_rawset(L, -3);
				}
			}
		};
		typedef std::shared_ptr<___M> __M;

		struct ___Z : _ScriptObject
		{
			std::string f;
			std::vector<std::pair<ScriptObject, ScriptObject>> c;
			std::vector<ScriptObject> r;
			___Z(Parser *parser) : f(parser->translate(___I(parser).value))
			{
				__M _m = std::dynamic_pointer_cast<___M>(parser->action());
				if (!_m)
					THROW_SCRIPT_EXCEPTION;
				c.swap(_m->value);
				__P _r = std::dynamic_pointer_cast<___P>(parser->action());
				if (!_r)
					THROW_SCRIPT_EXCEPTION;
				r.swap(_r->value);
			}
		};
		typedef std::shared_ptr<___Z> __Z;

		struct ___U : _ScriptObject
		{
			___U(Parser *parser){}
		};
		typedef std::shared_ptr<___U> __U;

		ScriptObject Parser::action()
		{
			switch (*p++)
			{
			case 'I':
				return std::make_shared<___I>(this, 36);
			case 'J':
				return std::make_shared<___I>(this, 10);
			case 'F':
				return std::make_shared<___F>(this);
			case 'B':
				return std::make_shared<___B>(this);
			case 'D':
				return std::make_shared<___D>(this);
			case 'S':
				return std::make_shared<___S>(this);
			case 'O':
				return std::make_shared<___O>(this);
			case 'P':
				return std::make_shared<___P>(this);
			case '?':
				return std::make_shared<___V>(this);
			case 'L':
				if (*p != '?')
					return std::make_shared<___P>(this);
				else
					return std::make_shared<___L>(this);
			case 'W':
				return std::make_shared<___W>(this);
			case 'X':
				return std::make_shared<___X>(this);
			case 'Y':
				return std::make_shared<___Y>(this);
			case 'Z':
				return std::make_shared<___Z>(this);
			case 'M':
				return std::make_shared<___M>(this);
			case 'U':
				return std::make_shared<___U>(this);
			default:
				THROW_SCRIPT_EXCEPTION;
			}
		}

		struct Context
		{
			bool z = false;
			bool g = true;
			std::unordered_map<int64_t, std::vector<std::string>> dicts;
			std::unordered_map<int64_t, std::vector<std::string>> paths;
			Parser *parser;
			int64_t sessionid;
			limax::DictionaryCache *cache;
			limax::TunnelReceiver ontunnel;
			int logindatas = -1;
			int login = -1;
			int tunnel = -1;
			~Context() { delete parser; }
			Context(limax::DictionaryCache *_cache, limax::TunnelReceiver _ontunnel) : parser(new Parser()), cache(_cache), ontunnel(_ontunnel) { }

			void onerror(lua_State *L)
			{
				lua_pushvalue(L, lua_upvalueindex(1));
				lua_pushstring(L, "onerror");
				lua_rawget(L, -2);
				lua_pushvalue(L, -3);
				if (lua_pcall(L, 1, 0, 0) != LUA_OK)
					lua_pop(L, 1);
				lua_pop(L, 1);
			}

			int onclose(lua_State *L)
			{
				z = true;
				lua_pushvalue(L, lua_upvalueindex(1));
				lua_pushstring(L, "onclose");
				lua_rawget(L, -2);
				lua_pushvalue(L, -3);
				if (lua_pcall(L, 1, 0, 0) != LUA_OK)
					lua_pop(L, 1);
				lua_pop(L, 1);
				return 3;
			}

			int onmessage(lua_State *L)
			{
				if (g)
				{
					const char *s = lua_tostring(L, -1);
					if (s[0] == '$')
					{
						getObjectFromGlobal(L, login);
						lua_pushinteger(L, strtoll(s + 1, NULL, 36));
						lua_call(L, 1, 0);
						return 0;
					}
					init(L);
					if (!lua_isnil(L, -1))
						return onclose(L);
					lua_pushvalue(L, lua_upvalueindex(1));
					lua_pushstring(L, "onopen");
					lua_rawget(L, -2);
					if (lua_isfunction(L, -1))
						lua_call(L, 0, 0);
					else
					{
						lua_pushstring(L, "context onopen not defined");
						onerror(L);
					}
					g = false;
					return 2;
				}
				update(L);
				return 0;
			}

			void init(lua_State *L)
			{
				__I r0 = std::dynamic_pointer_cast<___I>(parser->action(lua_tostring(L, -1)));
				if (!r0)
					THROW_SCRIPT_EXCEPTION;
				__I r1 = std::dynamic_pointer_cast<___I>(parser->action());
				if (!r1)
					THROW_SCRIPT_EXCEPTION;
				if (r1->value != 0)
				{
					r1->update(L);
					return;
				}
				__I _i = std::dynamic_pointer_cast<___I>(parser->action());
				if (!_i)
					THROW_SCRIPT_EXCEPTION;
				lua_pushvalue(L, lua_upvalueindex(1));
				lua_pushstring(L, "i");
				_i->update(L);
				lua_rawset(L, -3);
				sessionid = _i->value;
				__I _f = std::dynamic_pointer_cast<___I>(parser->action());
				if (!_f)
					THROW_SCRIPT_EXCEPTION;
				lua_pushstring(L, "f");
				_f->update(L);
				lua_rawset(L, -3);
				__P pl = std::dynamic_pointer_cast<___P>(parser->action());
				if (!pl)
					THROW_SCRIPT_EXCEPTION;
				for (uint32_t i = 0; i < pl->value.size(); i += 3)
				{
					__I _pvid = std::dynamic_pointer_cast<___I>(pl->value[i]);
					if (!_pvid)
						THROW_SCRIPT_EXCEPTION;
					lua_newtable(L);
					int32_t pvid = static_cast<int32_t>(_pvid->value);
					__S _dict = std::dynamic_pointer_cast<___S>(pl->value[i + 1]);
					if (!_dict)
						THROW_SCRIPT_EXCEPTION;
					std::vector<std::string> dict;
					for (std::string::size_type pos = 0;;)
					{
						std::string::size_type next = _dict->value.find_first_of(',', pos);
						dict.push_back(std::string(_dict->value, pos, next - pos));
						if (next == std::string::npos)
							break;
						pos = next + 1;
					}
					__M _ns;
					if (cache)
					{
						std::string ck = dict.back();
						std::string cv = cache->get(ck);
						if (cv.size())
						{
							_dict = std::dynamic_pointer_cast<___S>(parser->action(cv.c_str()));
							if (!_dict)
								THROW_SCRIPT_EXCEPTION;
							dict.clear();
							for (std::string::size_type pos = 0;;)
							{
								std::string::size_type next = _dict->value.find_first_of(',', pos);
								dict.push_back(std::string(_dict->value, pos, next - pos));
								if (next == std::string::npos)
									break;
								pos = next + 1;
							}
							_ns = std::dynamic_pointer_cast<___M>(parser->action());
							if (!_ns)
								THROW_SCRIPT_EXCEPTION;
						}
						else
						{
							std::string join;
							dict.pop_back();
							for (auto s : dict)
								join.append(s).push_back(',');
							cv.clear();
							cv.push_back('S');
							cv.append(limax::tostring36(join.size() - 1));
							cv.push_back(':');
							cv.insert(cv.end(), join.begin(), join.end() - 1);
							cv.push_back('M');
							_ns = std::dynamic_pointer_cast<___M>(pl->value[i + 2]);
							if (!_ns)
								THROW_SCRIPT_EXCEPTION;
							for (auto ns : _ns->value)
							{
								__I _k = std::dynamic_pointer_cast<___I>(ns.first);
								if (!_k)
									THROW_SCRIPT_EXCEPTION;
								__P _v = std::dynamic_pointer_cast<___P>(ns.second);
								if (!_v)
									THROW_SCRIPT_EXCEPTION;
								cv.push_back('I');
								cv.append(tostring36(_k->value));
								cv.append(":L");
								for (auto partial : _v->value)
								{
									__I _i = std::dynamic_pointer_cast<___I>(partial);
									if (!_i)
										THROW_SCRIPT_EXCEPTION;
									cv.push_back('I');
									cv.append(tostring36(_i->value));
									cv.push_back(':');
								}
								cv.push_back(':');
							}
							cv.push_back(':');
							cache->put(ck, cv);
						}
					}
					else
					{
						_ns = std::dynamic_pointer_cast<___M>(pl->value[i + 2]);
						if (!_ns)
							THROW_SCRIPT_EXCEPTION;
					}
					for (auto ns : _ns->value)
					{
						__I _k = std::dynamic_pointer_cast<___I>(ns.first);
						if (!_k)
							THROW_SCRIPT_EXCEPTION;
						__P _v = std::dynamic_pointer_cast<___P>(ns.second);
						if (!_v)
							THROW_SCRIPT_EXCEPTION;
						int top = lua_gettop(L);
						std::string fullname;
						std::vector<std::string> path;
						for (auto partial : _v->value)
						{
							__I _i = std::dynamic_pointer_cast<___I>(partial);
							if (!_i)
								THROW_SCRIPT_EXCEPTION;
							auto i = (decltype(dict.size()))_i->value;
							if (i < 0 || i >= dict.size())
								THROW_SCRIPT_EXCEPTION;
							const char *_p = dict[i].c_str();
							fullname.append(_p).append(".");
							path.push_back(dict[i]);
							lua_pushstring(L, _p);
							lua_rawget(L, -2);
							if (!lua_istable(L, -1))
							{
								lua_pop(L, 1);
								lua_pushstring(L, _p);
								lua_newtable(L);
								lua_rawset(L, -3);
								lua_pushstring(L, _p);
								lua_rawget(L, -2);
							}
						}
						lua_pushstring(L, "__c__");
						_k->update(L);
						lua_rawset(L, -3);
						lua_pushstring(L, "__p__");
						_pvid->update(L);
						lua_rawset(L, -3);
						fullname.pop_back();
						paths.insert(std::make_pair(_k->value, path));
						lua_pushstring(L, "__n__");
						lua_pushstring(L, fullname.c_str());
						lua_rawset(L, -3);
						lua_settop(L, top);
					}
					dicts.insert(std::make_pair(pvid, dict));
					lua_rawseti(L, -2, pvid);
				}
				if (ontunnel)
				{
					__S s = std::dynamic_pointer_cast<___S>(parser->action());
					if (!s)
						THROW_SCRIPT_EXCEPTION;
					ontunnel(1, 0, s->value);
				}
				lua_pushnil(L);
			}

			void update(lua_State *L)
			{
				__I _pvid = std::dynamic_pointer_cast<___I>(parser->action(lua_tostring(L, -1)));
				if (!_pvid) 
				{
					__S _data = std::dynamic_pointer_cast<___S>(parser->action(lua_tostring(L, -1)));
					if (!_data)
						THROW_SCRIPT_EXCEPTION;
					if (ontunnel)
					{
						__I _pvid = std::dynamic_pointer_cast<___I>(parser->action());
						if (!_pvid)
							THROW_SCRIPT_EXCEPTION;
						__I _label = std::dynamic_pointer_cast<___I>(parser->action());
						if (!_label)
							THROW_SCRIPT_EXCEPTION;
						ontunnel((int32_t)_pvid->value, (int32_t)_label->value, _data->value);
					}
					return;
				}
				int64_t pvid = _pvid->value;
				auto i_dict = dicts.find(pvid);
				if (i_dict == dicts.end())
					THROW_SCRIPT_EXCEPTION;
				parser->installDict(i_dict->second);
				lua_pushvalue(L, lua_upvalueindex(1));
				lua_rawgeti(L, -1, pvid);
				if (!lua_istable(L, -1))
					THROW_SCRIPT_EXCEPTION;
				__I _classindex = std::dynamic_pointer_cast<___I>(parser->action());
				if (!_classindex)
					THROW_SCRIPT_EXCEPTION;
				auto it = paths.find(_classindex->value);
				if (it == paths.end())
					THROW_SCRIPT_EXCEPTION;
				for (auto partial : (*it).second)
				{
					lua_pushstring(L, partial.c_str());
					lua_rawget(L, -2);
					if (!lua_istable(L, -1))
						THROW_SCRIPT_EXCEPTION;
				}
				__I _instanceindex = std::dynamic_pointer_cast<___I>(parser->action());
				if (!_instanceindex)
					THROW_SCRIPT_EXCEPTION;
				int32_t instanceindex = (int32_t)_instanceindex->value;
				__I _command = std::dynamic_pointer_cast<___I>(parser->action());
				if (!_command)
					THROW_SCRIPT_EXCEPTION;
				int32_t command = (int32_t)_command->value;
				if (command < 0 || command > 5)
					THROW_SCRIPT_EXCEPTION;
				__P _viewdata = std::dynamic_pointer_cast<___P>(parser->action());
				if (!_viewdata)
					THROW_SCRIPT_EXCEPTION;
				__P _subdata = std::dynamic_pointer_cast<___P>(parser->action());
				if (!_subdata)
					THROW_SCRIPT_EXCEPTION;
				switch (command)
				{
				case 0:
				{
					R(L, _viewdata, _subdata);
					break;
				}
				case 1:
				{
					lua_newtable(L);
					lua_pushstring(L, "__p__");
					lua_pushstring(L, "__p__");
					lua_rawget(L, -4);
					lua_rawset(L, -3);
					lua_pushstring(L, "__c__");
					lua_pushstring(L, "__c__");
					lua_rawget(L, -4);
					lua_rawset(L, -3);
					lua_pushstring(L, "__n__");
					lua_pushstring(L, "__n__");
					lua_rawget(L, -4);
					lua_rawset(L, -3);
					lua_pushstring(L, "__i__");
					lua_pushinteger(L, instanceindex);
					lua_rawset(L, -3);
					lua_pushvalue(L, -1);
					lua_rawseti(L, -3, instanceindex);
					lua_pushstring(L, "onopen");
					lua_rawget(L, -3);
					if (lua_isfunction(L, -1))
					{
						lua_pushvalue(L, -3);
						lua_pushinteger(L, instanceindex);
						lua_newtable(L);
						auto len = _subdata->value.size();
						if (len & 1)
							THROW_SCRIPT_EXCEPTION;
						for (decltype(len) i = 0; i < len; i += 2)
						{
							__I _sid = std::dynamic_pointer_cast<___I>(_subdata->value[i]);
							if (!_sid)
								THROW_SCRIPT_EXCEPTION;
							lua_pushinteger(L, _sid->value);
							lua_rawseti(L, -2, (i >> 1) + 1);
						}
						lua_call(L, 3, 0);
					}
					else
					{
						lua_pop(L, 1);
						lua_pushstring(L, "__n__");
						lua_rawget(L, -3);
						lua_pushstring(L, " onopen not defined");
						lua_concat(L, 2);
						onerror(L);
						lua_pop(L, 1);
					}
					R(L, _viewdata, _subdata);
					break;
				}
				case 2:
				{
					lua_pushinteger(L, instanceindex);
					lua_rawget(L, -2);
					if (!lua_istable(L, -1))
						THROW_SCRIPT_EXCEPTION;
					R(L, _viewdata, _subdata);
					break;
				}
				case 3:
				{
					lua_pushstring(L, "onattach");
					lua_rawget(L, -2);
					if (lua_isfunction(L, -1))
					{
						lua_pushvalue(L, -2);
						lua_pushinteger(L, instanceindex);
						if (_subdata->value.size() != 2)
							THROW_SCRIPT_EXCEPTION;
						__I _sid = std::dynamic_pointer_cast<___I>(_subdata->value[0]);
						if (!_sid)
							THROW_SCRIPT_EXCEPTION;
						lua_pushinteger(L, _sid->value);
						lua_call(L, 3, 0);
						lua_pushinteger(L, instanceindex);
						lua_rawget(L, -2);
						if (!lua_istable(L, -1))
							THROW_SCRIPT_EXCEPTION;
					}
					else
					{
						lua_pop(L, 1);
						lua_pushstring(L, "__n__");
						lua_rawget(L, -3);
						lua_pushstring(L, " onattach not defined");
						lua_concat(L, 2);
						onerror(L);
						lua_pop(L, 1);
					}
					R(L, _viewdata, _subdata);
					break;
				}
				case 4:
				{
					if (_subdata->value.size() != 2)
						THROW_SCRIPT_EXCEPTION;
					__I _sid = std::dynamic_pointer_cast<___I>(_subdata->value[0]);
					if (!_sid)
						THROW_SCRIPT_EXCEPTION;
					__I _code = std::dynamic_pointer_cast<___I>(_subdata->value[1]);
					if (!_code)
						THROW_SCRIPT_EXCEPTION;
					lua_pushstring(L, "ondetach");
					lua_rawget(L, -2);
					if (lua_isfunction(L, -1))
					{
						lua_pushvalue(L, -2);
						lua_pushinteger(L, instanceindex);
						lua_pushinteger(L, _sid->value);
						lua_pushinteger(L, _code->value);
						lua_call(L, 4, 0);
					}
					else
					{
						lua_pop(L, 1);
						lua_pushstring(L, "__n__");
						lua_rawget(L, -3);
						lua_pushstring(L, " ondetach not defined");
						lua_concat(L, 2);
						onerror(L);
						lua_pop(L, 1);
					}
					lua_pushinteger(L, instanceindex);
					lua_rawget(L, -2);
					lua_pushinteger(L, _sid->value);
					lua_pushnil(L);
					lua_rawset(L, -3);
					break;
				}
				case 5:
				{
					lua_pushstring(L, "onclose");
					lua_rawget(L, -2);
					if (lua_isfunction(L, -1))
					{
						lua_pushvalue(L, -2);
						lua_pushinteger(L, instanceindex);
						lua_call(L, 2, 0);
					}
					else
					{
						lua_pop(L, 1);
						lua_pushstring(L, "__n__");
						lua_rawget(L, -3);
						lua_pushstring(L, " onclose not defined");
						lua_concat(L, 2);
						onerror(L);
						lua_pop(L, 1);
					}
					lua_pushinteger(L, instanceindex);
					lua_pushnil(L);
					lua_rawset(L, -3);
					break;
				}
				}
			}

			void R(lua_State *L, __P viewdata, __P subdata)
			{
				lua_pushvalue(L, -1);
				for (auto item : viewdata->value)
				{
					__V _v = std::dynamic_pointer_cast<___V>(item);
					if (!_v)
						THROW_SCRIPT_EXCEPTION;
					U(L, sessionid, _v->value.first, _v->value.second);
				}
				lua_pop(L, 1);
				auto len = subdata->value.size();
				if (len & 1)
					THROW_SCRIPT_EXCEPTION;
				for (decltype(len) i = 0; i < len; i += 2)
				{
					__I _sid = std::dynamic_pointer_cast<___I>(subdata->value[i]);
					if (!_sid)
						THROW_SCRIPT_EXCEPTION;
					int64_t sid = _sid->value;
					__V _v = std::dynamic_pointer_cast<___V>(subdata->value[i + 1]);
					if (_v)
					{
						lua_rawgeti(L, -1, sid);
						if (!lua_istable(L, -1))
						{
							lua_pop(L, 1);
							lua_newtable(L);
							lua_pushvalue(L, -1);
							lua_rawseti(L, -3, sid);
						}
						U(L, sid, _v->value.first, _v->value.second);
						lua_pop(L, 1);
					}
					else if (!std::dynamic_pointer_cast<___U>(subdata->value[i + 1]))
						THROW_SCRIPT_EXCEPTION;
				}
			}

			void U(lua_State *L, int64_t sid, const std::string& varname, ScriptObject varvalue)
			{
				int type;
				lua_pushstring(L, varname.c_str());
				lua_rawget(L, -2);
				if (std::dynamic_pointer_cast<___U>(varvalue))
				{
					type = 2;
				}
				else if (std::dynamic_pointer_cast<___D>(varvalue))
				{
					type = 3;
					lua_pushstring(L, varname.c_str());
					lua_pushnil(L);
					lua_rawset(L, -4);
				}
				else if (__W bean = std::dynamic_pointer_cast<___W>(varvalue))
				{
					if (lua_isnil(L, -1))
					{
						lua_pop(L, 1);
						lua_newtable(L);
						lua_pushstring(L, varname.c_str());
						lua_pushvalue(L, -2);
						lua_rawset(L, -4);
						type = 0;
					}
					else
					{
						type = 1;
					}
					for (auto item : bean->value)
					{
						if (__X x = std::dynamic_pointer_cast<___X>(item))
						{
							lua_pushstring(L, x->f.c_str());
							x->v->update(L);
							lua_rawset(L, -3);
						}
						else if (__Y y = std::dynamic_pointer_cast<___Y>(item))
						{
							lua_pushstring(L, y->f.c_str());
							lua_rawget(L, -2);
							if (lua_isnil(L, -1))
							{
								lua_pop(L, 1);
								lua_newtable(L);
								lua_pushstring(L, y->f.c_str());
								lua_pushvalue(L, -2);
								lua_rawset(L, -4);
							}
							lua_getglobal(L, "table");
							lua_pushstring(L, "remove");
							lua_rawget(L, -2);
							for (auto r : y->r)
							{
								r->update(L);
								for (lua_pushnil(L); lua_next(L, -5) != 0; lua_pop(L, 1))
								{
									if (lua_rawequal(L, -1, -3))
									{
										lua_pushvalue(L, -4);
										lua_pushvalue(L, -7);
										lua_pushvalue(L, -4);
										lua_call(L, 2, 0);
										lua_pop(L, 2);
										break;
									}
								}
								lua_pop(L, 1);
							}
							lua_pop(L, 1);
							lua_pushstring(L, "insert");
							lua_rawget(L, -2);
							for (auto a : y->a)
							{
								lua_pushvalue(L, -1);
								lua_pushvalue(L, -4);
								a->update(L);
								lua_call(L, 2, 0);
							}
							lua_pop(L, 3);
						}
						else if (__Z z = std::dynamic_pointer_cast<___Z>(item))
						{
							lua_pushstring(L, z->f.c_str());
							lua_rawget(L, -2);
							if (lua_isnil(L, -1))
							{
								lua_pop(L, 1);
								lua_newtable(L);
								lua_pushstring(L, z->f.c_str());
								lua_pushvalue(L, -2);
								lua_rawset(L, -4);
							}
							for (auto r : z->r)
							{
								r->update(L);
								lua_pushnil(L);
								lua_rawset(L, -3);
							}
							for (auto c : z->c)
							{
								c.first->update(L);
								c.second->update(L);
								lua_rawset(L, -3);
							}
							lua_pop(L, 1);
						}
						else
							THROW_SCRIPT_EXCEPTION;
					}
				}
				else
				{
					type = lua_isnil(L, -1) ? 0 : 1;
					lua_pop(L, 1);
					varvalue->update(L);
					lua_pushstring(L, varname.c_str());
					lua_pushvalue(L, -2);
					lua_rawset(L, -4);
				}
				F(L, sid, varname, type);
				lua_pop(L, 1);
			}

			void F(lua_State *L, int64_t sid, const std::string& varname, int type)
			{
				lua_newtable(L);
				lua_pushstring(L, "view");
				lua_pushvalue(L, -5);
				lua_rawset(L, -3);
				lua_pushstring(L, "sessionid");
				lua_pushinteger(L, sid);
				lua_rawset(L, -3);
				lua_pushstring(L, "fieldname");
				lua_pushstring(L, varname.c_str());
				lua_rawset(L, -3);
				lua_pushstring(L, "value");
				lua_pushvalue(L, -3);
				lua_rawset(L, -3);
				lua_pushstring(L, "type");
				lua_pushinteger(L, type);
				lua_rawset(L, -3);
				lua_pushstring(L, "onchange");
				lua_rawget(L, -5);
				if (lua_isfunction(L, -1))
				{
					lua_pushvalue(L, -2);
					lua_call(L, 1, 0);
				}
				else
				{
					lua_pop(L, 1);
				}
				lua_pushstring(L, "__e__");
				lua_rawget(L, -5);
				if (lua_istable(L, -1))
				{
					lua_pushstring(L, varname.c_str());
					lua_rawget(L, -2);
					if (lua_isfunction(L, -1))
					{
						lua_pushvalue(L, -3);
						lua_call(L, 1, 0);
					}
					else if (lua_istable(L, -1))
					{
						for (lua_pushnil(L); lua_next(L, -2);)
						{
							lua_pushvalue(L, -5);
							lua_call(L, 1, 0);
						}
						lua_pop(L, 1);
					}
				}
				lua_pop(L, 2);
			}
		};
#undef THROW_SCRIPT_EXCEPTION
	}
}

int Limax(lua_State* L)
{
	lua_newtable(L);
	lua_pushvalue(L, 1);
	lua_pushvalue(L, -2);
	lua_call(L, 1, 0);
	limax::DictionaryCache *cache;
	if (lua_isnil(L, 2))
		cache = nullptr;
	else
	{
		lua_getmetatable(L, 2);
		lua_rawgetp(L, -1, L);
		cache = ((DictionaryCachePtrGCWrapper *)lua_touserdata(L, -1))->cache.get();
		lua_pop(L, 2);
	}
	limax::TunnelReceiver ontunnel;
	if (lua_isnil(L, 3))
		ontunnel = nullptr;
	else
	{
		lua_getmetatable(L, 3);
		lua_rawgetp(L, -1, L);
		ontunnel = ((TunnelReceiverGCWrapper *)lua_touserdata(L, -1))->ontunnel;
		lua_pop(L, 2);
	}
	lua_newtable(L);
	lua_pushlightuserdata(L, new limax::script::Context(cache, ontunnel));
	lua_rawsetp(L, -2, L);
	lua_pushstring(L, "__gc");
	lua_pushcfunction(L, [](lua_State *L){
		lua_getmetatable(L, 1);
		lua_rawgetp(L, -1, L);
		limax::script::Context *ctx = (limax::script::Context *)lua_touserdata(L, -1);
		releaseObjectFromGlobal(L, ctx->logindatas);
		releaseObjectFromGlobal(L, ctx->login);
		releaseObjectFromGlobal(L, ctx->tunnel);
		delete ctx;
		return 0;
	});
	lua_rawset(L, -3);
	lua_setmetatable(L, -2);
	lua_pushcclosure(L, [](lua_State *L){
		lua_pushvalue(L, lua_upvalueindex(1));
		lua_getmetatable(L, -1);
		lua_rawgetp(L, -1, L);
		limax::script::Context *ctx = (limax::script::Context *)lua_touserdata(L, -1);
		if (ctx->z)
		{
			lua_pushinteger(L, 3);
			return 1;
		}
		if (lua_type(L, 1) == LUA_TTABLE)
		{
			lua_pushvalue(L, 1);
			ctx->logindatas = appendObjectToGlobal(L);
			return 0;
		}
		if (lua_type(L, 3) == LUA_TSTRING)
		{
			if (ctx->tunnel != -1) {
				getObjectFromGlobal(L, ctx->tunnel);
				lua_pushvalue(L, 1);
				lua_pushvalue(L, 2);
				lua_pushvalue(L, 3);
				lua_call(L, 3, 0);
			}
			return 0;
		}
		int isnum;
		lua_Integer t = lua_tointegerx(L, 1, &isnum);
		if (isnum)
		{
			if (t == 0)
			{
				lua_pushlightuserdata(L, ctx);
				lua_pushvalue(L, 2);
				lua_pushcclosure(L, [](lua_State *L){
					limax::script::Context *ctx = (limax::script::Context *)lua_touserdata(L, lua_upvalueindex(1));
					if (!ctx->z)
					{
						lua_pushvalue(L, lua_upvalueindex(2));
						std::string v = limax::tostring36(lua_tointeger(L, 1));
						lua_pushstring(L, v.c_str());
						lua_pushstring(L, ",");
						if (ctx->logindatas != -1)
						{
							getObjectFromGlobal(L, ctx->logindatas);
							lua_pushvalue(L, 1);
							lua_rawget(L, -2);
							if (!lua_isnil(L, -1))
							{
								lua_pushstring(L, "label");
								lua_rawget(L, -2);
								if (lua_type(L, -1) == LUA_TNUMBER)
								{
									std::string label = limax::tostring36(lua_tointeger(L, -1));
									lua_pushstring(L, "data");
									lua_rawget(L, -3);
									std::string data = lua_tostring(L, -1);
									lua_pop(L, 4);
									lua_pushstring(L, "1,");
									lua_pushstring(L, label.c_str());
									lua_pushstring(L, ",");
									lua_pushstring(L, data.c_str());
									lua_concat(L, 6);
								}
								else
								{
									lua_pushstring(L, "data");
									lua_rawget(L, -3);
									std::string data = lua_tostring(L, -1);
									lua_pushstring(L, "base64");
									lua_rawget(L, -4);
									const char *type = lua_isnil(L, -1) ? "2," : "3,";
									lua_pop(L, 5);
									lua_pushstring(L, type);
									lua_pushstring(L, data.c_str());
									lua_concat(L, 4);
								}
							}
							else
							{
								lua_pop(L, 2);
								lua_pushstring(L, "0");
								lua_concat(L, 3);
							}
						}
						else 
						{
							lua_pushstring(L, "0");
							lua_concat(L, 3);
						}
						lua_call(L, 1, 1);
						if (!lua_isnil(L, -1))
							ctx->onclose(L);
					}
					return 0;
				}, 2);
				ctx->login = appendObjectToGlobal(L);
				lua_pushlightuserdata(L, ctx);
				lua_pushvalue(L, 2);
				lua_pushcclosure(L, [](lua_State *L){
					limax::script::Context *ctx = (limax::script::Context *)lua_touserdata(L, lua_upvalueindex(1));
					if (!ctx->z)
					{
						lua_pushvalue(L, lua_upvalueindex(2));
						std::string v = limax::tostring36(lua_tointeger(L, 1));
						std::string l = limax::tostring36(lua_tointeger(L, 2));
						lua_pushstring(L, v.c_str());
						lua_pushstring(L, ",");
						lua_pushstring(L, l.c_str());
						lua_pushstring(L, ",");
						lua_pushvalue(L, 3);
						lua_concat(L, 5);
						lua_call(L, 1, 1);
						if (!lua_isnil(L, -1))
							ctx->onclose(L);
					}
					return 0;
				}, 2);
				ctx->tunnel = appendObjectToGlobal(L);
				lua_pushstring(L, "send");
				lua_pushvalue(L, lua_upvalueindex(1));
				lua_pushvalue(L, 2);
				lua_pushcclosure(L, [](lua_State *L){
					lua_getmetatable(L, lua_upvalueindex(1));
					lua_rawgetp(L, -1, L);
					limax::script::Context *ctx = (limax::script::Context *)lua_touserdata(L, -1);
					if (!ctx->z)
					{
						lua_pushvalue(L, lua_upvalueindex(2));
						lua_pushstring(L, "__p__");
						lua_rawget(L, 1);
						std::string __p__ = limax::tostring36(lua_tointeger(L, -1));
						lua_pop(L, 1);
						lua_pushstring(L, "__c__");
						lua_rawget(L, 1);
						std::string __c__ = limax::tostring36(lua_tointeger(L, -1));
						lua_pop(L, 1);
						lua_pushstring(L, "__i__");
						lua_rawget(L, 1);
						std::string __i__ = limax::tostring36(lua_tointeger(L, -1));
						lua_pop(L, 1);
						lua_pushstring(L, __p__.c_str());
						lua_pushstring(L, ",");
						lua_pushstring(L, __c__.c_str());
						lua_pushstring(L, ",");
						lua_pushstring(L, __i__.c_str());
						lua_pushstring(L, ":");
						lua_pushvalue(L, 2);
						lua_concat(L, 7);
						lua_call(L, 1, 1);
						if (!lua_isnil(L, -1))
							ctx->onclose(L);
					}
					return 0;
				}, 2);
				lua_rawset(L, lua_upvalueindex(1));
				lua_pushstring(L, "register");
				lua_pushcfunction(L, [](lua_State *L){
					lua_pushstring(L, "__e__");
					lua_rawget(L, 1);
					if (!lua_istable(L, -1))
					{
						lua_pop(L, 1);
						lua_newtable(L);
						lua_pushstring(L, "__e__");
						lua_pushvalue(L, -2);
						lua_rawset(L, 1);
					}
					lua_pushvalue(L, 2);
					lua_rawget(L, -2);
					if (lua_isnil(L, -1))
					{
						lua_pushvalue(L, 2);
						lua_pushvalue(L, 3);
						lua_rawset(L, -4);
					}
					else if (lua_isfunction(L, -1))
					{
						lua_pushvalue(L, 2);
						lua_newtable(L);
						lua_pushvalue(L, -3);
						lua_rawseti(L, -2, 1);
						lua_pushvalue(L, 3);
						lua_rawseti(L, -2, 2);
						lua_rawset(L, -4);
					}
					else
					{
						lua_getglobal(L, "table");
						lua_pushstring(L, "insert");
						lua_rawget(L, -2);
						lua_pushvalue(L, -3);
						lua_pushvalue(L, 3);
						lua_call(L, 2, 0);
					}
					return 0;
				});
				lua_rawset(L, lua_upvalueindex(1));
				lua_pushinteger(L, 0);
				return 1;
			}
			else if (t == 1)
			{
				try
				{
					lua_pushvalue(L, 2);
					lua_pushinteger(L, ctx->onmessage(L));
					return 1;
				}
				catch (limax::script::ScriptException e)
				{
					lua_pushstring(L, e.message.c_str());
					ctx->onerror(L);
				}
			}
		}
		lua_pushvalue(L, 2);
		lua_pushinteger(L, ctx->onclose(L));
		return 1;
	}, 1);
	return 1;
}
