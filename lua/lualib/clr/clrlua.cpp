#include <comutil.h>
#include <string>

using namespace System;
using namespace System::Collections;
using namespace System::Text;
using namespace System::Text::RegularExpressions;
using namespace System::Runtime::InteropServices;
using namespace System::Threading;
using namespace limax::util;
#pragma warning (disable:4357)
namespace limax
{
	namespace script
	{
#include "..\src\lua.hpp"
#include "..\src\lstate.h"
		public delegate Object^ LuaFunction(... array<Object^>^ parameters);

		private struct _Lua
		{
			lua_State *L;
			_variant_t *eh;
			std::string chunkname;
			~_Lua();
			_Lua(_variant_t *lua, _variant_t *_eh);
			static std::string to_string(String^ s);
			static ReflectionCache^ ref_cache(lua_State *L);
			static Object^ lua_toobject(lua_State *L, int index);
			static void lua_pushobject(lua_State *L, Object^ o);
			static void lua_newobject(lua_State *L, Object^ o);
			Object^ lua_return(lua_State *L, int top, int e);
			int lua_loadstring(String^ code);
			void name(String^ chunkname);
			Object^ eval(String^ code);
			Object^ eval(String^ code, Hashtable^ map);
		};

		public ref class Lua sealed
		{
		private:
			Regex^ pattern;
			static Object^ string_cast(Object ^o);
		internal:
			ReflectionCache^ refcache;
			_Lua *lua;
		public:
			delegate void ErrorHandle(String^ msg);
			~Lua()
			{
				delete lua;
			}

			Lua(ErrorHandle^ eh) : pattern(gcnew Regex("<\\s*(\\d+)\\s*>")), refcache(gcnew ReflectionCache(gcnew ReflectionCache::ToStringCast(string_cast)))
			{
				_variant_t *var = new _variant_t();
				Marshal::GetNativeVariantForObject(this, IntPtr(var));
				_variant_t *veh = new _variant_t();
				Marshal::GetNativeVariantForObject(eh, IntPtr(veh));
				lua = new _Lua(var, veh);
			}

			Lua^ name(String^ chunkname);
			Object^ eval(String^ code);
			Object^ eval(String^ codepattern, ... array<Object^>^ parameters);
		};

		public ref struct LuaObject
		{
		internal:
			lua_State *L;
			Lua^ lua;
			int serial;
			~LuaObject()
			{
				Monitor::Enter(lua);
				try
				{
					luaL_unref(L, LUA_REGISTRYINDEX, serial);
				}
				finally
				{
					Monitor::Exit(lua);
				}
			}

			LuaObject(lua_State *_L, int index) : L(_L), lua((Lua^)Marshal::GetObjectForNativeVariant(IntPtr(L->hook)))
			{
				lua_pushvalue(L, index);
				serial = luaL_ref(L, LUA_REGISTRYINDEX);
			}

			void lua_newobject()
			{
				lua_rawgeti(L, LUA_REGISTRYINDEX, serial);
			}
		public:
			String^ ToString() override
			{
				lua_getglobal(L, "tostring");
				lua_newobject();
				lua_call(L, 1, 1);
				size_t len;
				String^ s;
				if (const char *value = lua_tolstring(L, -1, &len))
					s = gcnew String(value, 0, (int)len, Encoding::UTF8);
				else
					s = nullptr;
				lua_pop(L, 1);
				return s;
			}
		};

		public ref struct LuaTable sealed : public LuaObject, public IDictionary
		{
		private:
			ref class DictionaryEnumerator : public IDictionaryEnumerator
			{
			private:
				IEnumerator^ it;
			public:
				DictionaryEnumerator(IEnumerator^ _it) : it(_it) {}
				virtual property DictionaryEntry Entry { DictionaryEntry get() { return safe_cast<DictionaryEntry>(it->Current); }}
				virtual property Object^ Key { Object^ get() { return Entry.Key; }}
				virtual property Object^ Value { Object^ get() { return Entry.Value; }}
				virtual bool MoveNext() { return it->MoveNext(); }
				virtual void Reset() { it->Reset(); }
				virtual property Object^ Current { Object^ get(){ return it->Current; }};
			};
			ref class Collection : public ICollection
			{
			private:
				bool needKey;
				LuaTable^ outer;
			public:
				Collection(LuaTable^ _outer, bool _needKey) : outer(_outer), needKey(_needKey) {}
				virtual property bool IsSynchronized { bool get() { return outer->IsSynchronized; }};
				virtual property int Count { int get() { return outer->Count; }; };
				virtual property Object^ SyncRoot { Object^ get(){ return outer->SyncRoot; } };
				virtual System::Collections::IEnumerator^ GetEnumerator(){ return outer->copy(needKey)->GetEnumerator(); }
				virtual void CopyTo(Array^ a, int index) { outer->copy(needKey)->CopyTo(a, index); }
			};
			ArrayList^ copy(bool needKey)
			{
				ArrayList^ a = gcnew ArrayList();
				Monitor::Enter(lua);
				try
				{
					lua_rawgeti(L, LUA_REGISTRYINDEX, serial);
					for (lua_pushnil(L); lua_next(L, -2); lua_pop(L, 1))
						a->Add(_Lua::lua_toobject(L, needKey ? -2 : -1));
					lua_pop(L, 1);
				}
				finally
				{
					Monitor::Exit(lua);
				}
				return a;
			}
			ArrayList^ copy()
			{
				ArrayList^ a = gcnew ArrayList();
				Monitor::Enter(lua);
				try
				{
					lua_rawgeti(L, LUA_REGISTRYINDEX, serial);
					for (lua_pushnil(L); lua_next(L, -2); lua_pop(L, 1))
						a->Add(DictionaryEntry(_Lua::lua_toobject(L, -2), _Lua::lua_toobject(L, -1)));
					lua_pop(L, 1);
				}
				finally
				{
					Monitor::Exit(lua);
				}
				return a;
			}
		internal:
			LuaTable(lua_State *L, int index) : LuaObject(L, index) {}
		public:
			virtual property int Count
			{
				int get() {
					Monitor::Enter(lua);
					try
					{
						int count = 0;
						lua_rawgeti(L, LUA_REGISTRYINDEX, serial);
						for (lua_pushnil(L); lua_next(L, -2); lua_pop(L, 1))
							count++;
						lua_pop(L, 1);
						return count;
					}
					finally
					{
						Monitor::Exit(lua);
					}
				};
			};
			virtual property Object^ SyncRoot { Object^ get(){ return lua; } };
			virtual property bool IsSynchronized { bool get() { return false; }};
			virtual property bool IsReadOnly { bool get() { return false; }};
			virtual property bool IsFixedSize { bool get() { return false; }};
			virtual property Object^ default[Object^]
			{
				Object^ get(Object^ key)
				{
					Monitor::Enter(lua);
					try
					{
						lua_rawgeti(L, LUA_REGISTRYINDEX, serial);
						_Lua::lua_pushobject(L, key);
						lua_gettable(L, -2);
						Object^ r = _Lua::lua_toobject(L, -1);
						lua_pop(L, 2);
						return r;
					}
					finally
					{
						Monitor::Exit(lua);
					}
				};
				void set(Object^ key, Object^ value)
				{
					Add(key, value);
				};
			};
			virtual property ICollection^ Keys { ICollection^ get(){ return gcnew Collection(this, true); } };
			virtual property ICollection^ Values { ICollection^ get(){ return gcnew Collection(this, false); } };
			virtual bool Contains(Object^ key)
			{
				Monitor::Enter(lua);
				try
				{
					lua_rawgeti(L, LUA_REGISTRYINDEX, serial);
					_Lua::lua_pushobject(L, key);
					lua_gettable(L, -2);
					bool r = !lua_isnil(L, -1);
					lua_pop(L, 2);
					return r;
				}
				finally
				{
					Monitor::Exit(lua);
				}
			}
			virtual void Add(Object^ key, Object^ value)
			{
				Monitor::Enter(lua);
				try
				{
					lua_rawgeti(L, LUA_REGISTRYINDEX, serial);
					_Lua::lua_pushobject(L, key);
					_Lua::lua_pushobject(L, value);
					lua_settable(L, -3);
					lua_pop(L, 1);
				}
				finally
				{
					Monitor::Exit(lua);
				}
			}
			virtual void Clear()
			{
				Monitor::Enter(lua);
				try
				{
					ArrayList^ keys = gcnew ArrayList();
					lua_rawgeti(L, LUA_REGISTRYINDEX, serial);
					for (lua_pushnil(L); lua_next(L, -2); lua_pop(L, 1))
						keys->Add(_Lua::lua_toobject(L, -2));
					for (int i = 0, len = keys->Count; i < len; i++)
					{
						_Lua::lua_pushobject(L, keys[i]);
						lua_pushnil(L);
						lua_rawset(L, -3);
					}
					lua_pop(L, 1);
				}
				finally
				{
					Monitor::Exit(lua);
				}
			}
			virtual void Remove(Object^ key) { Add(key, nullptr); }
			virtual void CopyTo(Array^ a, int index) { copy()->CopyTo(a, index); }
			virtual IEnumerator^ GetEnumeratorStupid() = IEnumerable::GetEnumerator{ return copy()->GetEnumerator(); }
			virtual IDictionaryEnumerator^ GetEnumerator() { return gcnew DictionaryEnumerator(copy()->GetEnumerator()); }
		};

		private ref struct LuaFunction_ sealed : public LuaObject
		{
		internal:
			LuaFunction_(lua_State *L, int index) : LuaObject(L, index) { }
		public:
			Object^ Invoke(array<Object^>^ parameters);
		};

		_Lua::~_Lua()
		{
			delete (_variant_t *)L->hook;
			delete eh;
			lua_close(L);
		}

		_Lua::_Lua(_variant_t *lua, _variant_t *_eh) : L(luaL_newstate()), eh(_eh)
		{
			luaL_openlibs(L);
			L->hookmask = 0;
			L->hookcount = 0;
			L->hook = (lua_Hook)lua;
			lua_getglobal(L, "debug");
			lua_pushstring(L, "sethook");
			lua_pushnil(L);
			lua_rawset(L, -3);
			lua_pushstring(L, "gethook");
			lua_pushnil(L);
			lua_rawset(L, -3);
			lua_pop(L, 1);
		}

		std::string _Lua::to_string(String^ s)
		{
			std::string r;
			for each (auto c in Encoding::UTF8->GetBytes(s))
				r.push_back(c);
			return r;
		}

		ReflectionCache^ _Lua::ref_cache(lua_State *L)
		{
			return safe_cast<Lua^>(Marshal::GetObjectForNativeVariant(IntPtr(L->hook)))->refcache;
		}

		Object^ _Lua::lua_toobject(lua_State *L, int index)
		{
			switch (lua_type(L, index))
			{
			case LUA_TNIL:
				return nullptr;
			case LUA_TBOOLEAN:
				return lua_toboolean(L, index) ? true : false;
			case LUA_TNUMBER:
				if (lua_isinteger(L, index))
					return lua_tointeger(L, index);
				else
					return lua_tonumber(L, index);
			case LUA_TSTRING:
			{
				size_t len;
				const char* value = lua_tolstring(L, index, &len);
				return gcnew String(value, 0, (int)len, Encoding::UTF8);
			}
			case LUA_TTABLE:
				return gcnew LuaTable(L, index);
			case LUA_TFUNCTION:
				return gcnew LuaFunction(gcnew LuaFunction_(L, index), &LuaFunction_::Invoke);
			case LUA_TUSERDATA:
			{
				Object^ o = Marshal::GetObjectForNativeVariant(IntPtr(*(_variant_t **)lua_touserdata(L, index)));
				return ReflectionCache::Invokable::typeid->IsInstanceOfType(o) ? safe_cast<ReflectionCache::Invokable^>(o)->GetTarget() : o;
			}
			}
			return gcnew LuaObject(L, index);
		}

		void _Lua::lua_pushobject(lua_State *L, Object^ o)
		{
			if (!o)
			{
				lua_pushnil(L);
				return;
			}
			switch (Type::GetTypeCode(o->GetType()))
			{
			case TypeCode::Boolean:
				lua_pushboolean(L, safe_cast<Boolean>(o));
				return;
			case TypeCode::Byte:
				lua_pushinteger(L, safe_cast<Byte>(o));
				return;
			case TypeCode::SByte:
				lua_pushinteger(L, safe_cast<SByte>(o));
				return;
			case TypeCode::Int16:
				lua_pushinteger(L, safe_cast<Int16>(o));
				return;
			case TypeCode::UInt16:
				lua_pushinteger(L, safe_cast<UInt16>(o));
				return;
			case TypeCode::Int32:
				lua_pushinteger(L, safe_cast<Int32>(o));
				return;
			case TypeCode::UInt32:
				lua_pushinteger(L, safe_cast<UInt32>(o));
				return;
			case TypeCode::Int64:
				lua_pushinteger(L, safe_cast<Int64>(o));
				return;
			case TypeCode::UInt64:
				lua_pushinteger(L, safe_cast<UInt64>(o));
				return;
			case TypeCode::Single:
				lua_pushnumber(L, safe_cast<Single>(o));
				return;
			case TypeCode::Double:
				lua_pushnumber(L, safe_cast<Double>(o));
				return;
			case TypeCode::Decimal:
				lua_pushnumber(L, (lua_Number)safe_cast<Decimal>(o));
				return;
			case TypeCode::Char:
				lua_pushstring(L, to_string(gcnew String(safe_cast<Char>(o), 1)).c_str());
				return;
			case TypeCode::String:
				lua_pushstring(L, to_string(safe_cast<String^>(o)).c_str());
				return;
			}
			if (LuaFunction::typeid->IsInstanceOfType(o))
				safe_cast<LuaFunction_^>(safe_cast<LuaFunction^>(o)->Target)->lua_newobject();
			else if (LuaObject::typeid->IsInstanceOfType(o))
				safe_cast<LuaObject^>(o)->lua_newobject();
			else
				lua_newobject(L, Delegate::typeid->IsInstanceOfType(o) ? ref_cache(L)->GetValue(o, "Invoke") : o);
		}

		void _Lua::lua_newobject(lua_State *L, Object^ o)
		{
			Marshal::GetNativeVariantForObject(o, IntPtr(*(_variant_t **)lua_newuserdata(L, sizeof(_variant_t *)) = new _variant_t()));
			lua_newtable(L);
			lua_pushstring(L, "__gc");
			lua_pushcfunction(L, [](lua_State *L){
				delete *(_variant_t **)lua_touserdata(L, 1);
				return 0;
			});
			lua_rawset(L, -3);
			lua_pushstring(L, "__index");
			lua_pushcfunction(L, [](lua_State *L){
				try
				{
					Object^ o = Marshal::GetObjectForNativeVariant(IntPtr(*(_variant_t **)lua_touserdata(L, 1)));
					lua_pushobject(L, ref_cache(L)->GetValue(o, lua_toobject(L, 2)));
					return 1;
				}
				catch (Exception^ e)
				{
					luaL_traceback(L, L, to_string(e->ToString()).c_str(), 0);
					lua_error(L);
				}
				return 0;
			});
			lua_rawset(L, -3);
			lua_pushstring(L, "__newindex");
			lua_pushcfunction(L, [](lua_State *L){
				try
				{
					Object^ o = Marshal::GetObjectForNativeVariant(IntPtr(*(_variant_t **)lua_touserdata(L, 1)));
					ref_cache(L)->SetValue(o, lua_toobject(L, 2), lua_toobject(L, 3));
				}
				catch (Exception^ e)
				{
					luaL_traceback(L, L, to_string(e->ToString()).c_str(), 0);
					lua_error(L);
				}
				return 0;
			});
			lua_rawset(L, -3);
			lua_pushstring(L, "__tostring");
			lua_pushcfunction(L, [](lua_State *L){
				lua_pushstring(L, to_string(Marshal::GetObjectForNativeVariant(IntPtr(*(_variant_t **)lua_touserdata(L, 1)))->ToString()).c_str());
				return 1;
			});
			lua_rawset(L, -3);
			bool isType = Type::typeid->IsInstanceOfType(o);
			if (isType || ReflectionCache::Invokable::typeid->IsInstanceOfType(o))
			{
				lua_pushstring(L, "__call");
				lua_pushboolean(L, isType);
				lua_pushcclosure(L, [](lua_State *L){
					try
					{
						int nparameters = lua_gettop(L) - 1;
						array<Object^>^ oargs = gcnew array<Object^>(nparameters);
						for (int i = 0; i < nparameters; i++)
							oargs[i] = lua_toobject(L, i + 2);
						Object^ o = Marshal::GetObjectForNativeVariant(IntPtr(*(_variant_t **)lua_touserdata(L, 1)));
						Object^ r = lua_toboolean(L, lua_upvalueindex(1)) ? ref_cache(L)->Construct(o, oargs) : safe_cast<ReflectionCache::Invokable^>(o)->Invoke(oargs);
						if (r != DBNull::Value)
						{
							lua_pushobject(L, r);
							return 1;
						}
					}
					catch (Exception^ e)
					{
						luaL_traceback(L, L, to_string(e->ToString()).c_str(), 0);
						lua_error(L);
					}
					return 0;
				}, 1);
				lua_rawset(L, -3);
			}
			lua_setmetatable(L, -2);
		}

		Object^ _Lua::lua_return(lua_State *L, int top, int e)
		{
			if (e)
			{
				if (Object^ obj = Marshal::GetObjectForNativeVariant(IntPtr(eh)))
					obj->GetType()->GetMethod("Invoke")->Invoke(obj, gcnew array<Object^>{ lua_toobject(L, -1) });
			}
			else
			{
				if (top == 1)
					return lua_toobject(L, -1);
				else if (top > 1)
				{
					array<Object^>^ a = gcnew array<Object^>(top);
					for (int i = 0; i < top; i++)
						a[i] = (lua_toobject(L, -top + i));
					return a;
				}
			}
			return DBNull::Value;
		}

		int _Lua::lua_loadstring(String^ code)
		{
			std::string _code = to_string(code);
			std::pair<const char *, size_t> pair(_code.c_str(), _code.length());
			return lua_load(L, [](lua_State *L, void *data, size_t *size){
				auto p = (std::pair<const char *, size_t>*)data;
				auto r = p->first;
				*size = p->second;
				if (r)
				{
					p->first = nullptr;
					p->second = 0;
				}
				return r;
			}, &pair, chunkname.c_str(), nullptr);
		}

		void _Lua::name(String^ chunkname)
		{
			this->chunkname = to_string(chunkname);
		}

		Object^ _Lua::eval(String^ code)
		{
			try
			{
				int top = lua_gettop(L);
				int e = lua_loadstring(code);
				if (!e)
					e = lua_pcall(L, 0, LUA_MULTRET, 0);
				return lua_return(L, lua_gettop(L) - top, e);
			}
			finally
			{
				lua_pop(L, lua_gettop(L));
			}
		}

		Object^ _Lua::eval(String^ code, Hashtable^ map)
		{
			try
			{
				int top = lua_gettop(L);
				int e = lua_loadstring(code);
				if (!e)
				{
					lua_rawgeti(L, LUA_REGISTRYINDEX, LUA_RIDX_GLOBALS);
					lua_newtable(L);
					for (lua_pushnil(L); lua_next(L, -3);)
					{
						lua_pushvalue(L, -1);
						lua_copy(L, -3, -2);
						lua_rawset(L, -4);
					}
					for each (DictionaryEntry i in map)
					{
						lua_pushstring(L, to_string(safe_cast<String^>(i.Key)).c_str());
						lua_pushobject(L, i.Value);
						lua_rawset(L, -3);
					}
					lua_pushvalue(L, -3);
					lua_pushvalue(L, -2);
					lua_setupvalue(L, -2, 1);
					try
					{
						e = lua_pcall(L, 0, LUA_MULTRET, 0);
					}
					finally
					{
						for each (DictionaryEntry i in map)
						{
							lua_pushstring(L, to_string(safe_cast<String^>(i.Key)).c_str());
							lua_pushnil(L);
							lua_rawset(L, top + 3);
						}
						for (lua_pushnil(L); lua_next(L, top + 3);)
						{
							lua_pushvalue(L, -1);
							lua_copy(L, -3, -2);
							lua_rawset(L, top + 2);
						}
					}
				}
				return lua_return(L, lua_gettop(L) - top - 3, e);
			}
			finally
			{
				lua_pop(L, lua_gettop(L));
			}
		}

		Object^ Lua::string_cast(Object ^o)
		{
			if (LuaFunction::typeid->IsInstanceOfType(o))
				return safe_cast<LuaFunction^>(o)->Target->ToString();
			return o ? o->ToString() : o;
		}

		Lua^ Lua::name(String^ chunkname)
		{
			Monitor::Enter(this);
			try
			{
				lua->name(chunkname);
				return this;
			}
			finally	{ Monitor::Exit(this); }
		}

		Object^ Lua::eval(String^ code)
		{
			Monitor::Enter(this);
			try	{ return lua->eval(code); }
			finally	{ Monitor::Exit(this); }
		}

		Object^ Lua::eval(String^ codepattern, ... array<Object^>^ parameters)
		{
			if (parameters == nullptr)
				parameters = gcnew array<Object^> { nullptr };
			Monitor::Enter(this);
			try
			{
				StringBuilder^ sb = gcnew StringBuilder();
				Hashtable^ map = gcnew Hashtable();
				int index = 0;
				for (Match^ match = pattern->Match(codepattern); match->Success; match = match->NextMatch())
				{
					int pos = Int32::Parse(match->Groups[1]->Value);
					String^ key = "___limax_context_var_" + pos + "___";
					sb->Append(codepattern->Substring(index, match->Index - index))->Append(" ")->Append(key)->Append(" ");
					index = match->Index + match->Length;
					map[key] = pos < parameters->Length ? parameters[pos] : nullptr;
				}
				sb->Append(codepattern->Substring(index, codepattern->Length - index));
				return lua->eval(sb->ToString(), map);
			}
			finally
			{
				Monitor::Exit(this);
			}
		}

		Object^ LuaFunction_::Invoke(array<Object^>^ parameters)
		{
			Monitor::Enter(lua);
			try
			{
				int top = lua_gettop(L);
				lua_newobject();
				int nargs;
				if (parameters == nullptr)
				{
					lua_pushnil(L);
					nargs = 1;
				}
				else
				{
					for each(Object^ p in parameters)
						_Lua::lua_pushobject(L, p);
					nargs = parameters->Length;
				}
				int e = lua_pcall(L, nargs, LUA_MULTRET, 0);
				return lua->lua->lua_return(L, lua_gettop(L) - top, e);
			}
			finally
			{
				lua_pop(L, lua_gettop(L));
				Monitor::Exit(lua);
			}
		}
	}
}
