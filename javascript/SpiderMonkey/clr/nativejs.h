#pragma once

namespace limax
{
	namespace script
	{
		struct Value
		{
			enum { T_BOOLEAN, T_INT32, T_DOUBLE, T_STRING, T_NULL, T_UNDEFINED, T_CSOBJECT, T_JSOBJECT, T_JSARRAY, T_JSFUNCTION, T_EXCEPTION } tag;
			union
			{
				bool b;
				int32_t i;
				double d;
				struct
				{
					int l;
					char16_t *v;
				} s;
				void *obj;
			}u;
			template<typename T>
			static Value createBoolean(T v)
			{
				Value r;
				r.tag = T_BOOLEAN;
				r.u.b = (bool)v;
				return r;
			}
			template<typename T>
			static Value createInt32(T v)
			{
				Value r;
				r.tag = T_INT32;
				r.u.i = (int32_t)v;
				return r;
			}
			template<typename T>
			static Value createDouble(T v)
			{
				Value r;
				r.tag = T_DOUBLE;
				r.u.d = (double)v;
				return r;
			}
			template<typename T, typename L>
			static Value createString(T v, L l)
			{
				Value r;
				r.tag = T_STRING;
				r.u.s.v = new char16_t[r.u.s.l = (int)l];
				for (L i = 0; i < l; i++)
					r.u.s.v[i] = v[i];
				return r;
			}
			static Value createNull()
			{
				Value r;
				r.tag = T_NULL;
				return r;
			}
			static Value createUndefined()
			{
				Value r;
				r.tag = T_UNDEFINED;
				return r;
			}
			static Value createCSObject(void *obj)
			{
				Value r;
				r.tag = T_CSOBJECT;
				r.u.obj = obj;
				return r;
			}
			static Value createJSObject(void *obj)
			{
				Value r;
				r.tag = T_JSOBJECT;
				r.u.obj = obj;
				return r;
			}
			static Value createJSArray(void *obj)
			{
				Value r;
				r.tag = T_JSARRAY;
				r.u.obj = obj;
				return r;
			}
			static Value createJSFunction(void *obj)
			{
				Value r;
				r.tag = T_JSFUNCTION;
				r.u.obj = obj;
				return r;
			}
			static Value createException(void *obj)
			{
				Value r;
				r.tag = T_EXCEPTION;
				r.u.obj = obj;
				return r;
			}
		};

		struct NativeJs
		{
			typedef std::basic_string<char16_t> String;
			struct Operations
			{
				std::function<void(const char *)> print;
				std::function<Value(void*, Value&)> opGetProperty;
				std::function<Value(void*, Value&, Value&)> opSetProperty;
				std::function<Value(void*, std::vector<Value>)> opCall;
				std::function<Value(void*, std::vector<Value>)> opConstruct;
				std::function<Value(void*, Value&)> opInstanceOf;
			};
			struct Exception
			{
				_variant_t* cs;
				std::string js;
				~Exception() { delete cs; }
			};
			virtual ~NativeJs() {}
			virtual void name(const std::string& chunkname) = 0;
			virtual Value eval(const String& code) = 0;
			virtual Value eval(const String& code, const String& prefix, std::unordered_map<int, Value> parameters) = 0;
			virtual Value propertyCount(void *holder) = 0;
			virtual Value propertyContains(void *holder, const String& key) = 0;
			virtual Value propertyGet(void *holder, const String& key) = 0;
			virtual Value propertySet(void *holder, const String& key, const Value& value) = 0;
			virtual Value propertyRemove(void *holder, const String& key) = 0;
			virtual Value propertyClear(void *holder) = 0;
			virtual std::vector<std::pair<Value, Value>> propertyCopy(void *holder) = 0;
			virtual std::vector<Value> propertyCopy(void *holder, bool needKey) = 0;
			virtual Value arrayCount(void *holder) = 0;
			virtual Value arrayAdd(void *holder, const Value& value) = 0;
			virtual Value arrayGet(void *holder, uint32_t index) = 0;
			virtual Value arraySet(void *holder, uint32_t index, const Value& value) = 0;
			virtual Value arrayIndexOf(void *holder, const Value& value) = 0;
			virtual Value arrayInsert(void *holder, uint32_t index, const Value& value) = 0;
			virtual Value arrayRemove(void *holder, const Value& value) = 0;
			virtual Value arrayRemoveAt(void *holder, uint32_t index) = 0;
			virtual Value arrayClear(void *holder) = 0;
			virtual std::vector<Value> arrayCopy(void *holder) = 0;
			virtual Value functionCall(void *holder, void *parent_holder, const std::vector<Value>& parameters) = 0;
			virtual Value functionCreate(void *holder, const std::vector<Value>& parameters) = 0;
			virtual Value toString(void *holder) = 0;
			static void unlinkJsObject(void *holder);
			static NativeJs *createInstance(Operations* operations, uint32_t maxbytes);
		};
	}
}
