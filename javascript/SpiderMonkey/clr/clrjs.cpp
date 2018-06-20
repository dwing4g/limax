#include <js/RequiredDefines.h>
#include <comutil.h>
#include <string>
#include <stdint.h>
#include <unordered_map>
#include <functional>

#include "nativejs.h"

using namespace System;
using namespace System::Collections;
using namespace System::Text;
using namespace System::Text::RegularExpressions;
using namespace System::Runtime::InteropServices;
using namespace System::Threading;
using namespace limax::util;
#pragma warning(disable:4357)
namespace limax
{
	namespace script
	{
		ref class Js;
		ref struct JsObject;
		public delegate Object^ JsFunction(... array<Object^>^ parameters);

		static NativeJs::String to_string(String ^s);
		static String^ to_string(NativeJs::String s);
		template<typename T>
		static String^ to_string(const char16_t *s, T l);
		static Object^ string_cast(Object^ o);

		static void print(const char *s);
		static Value marshalException(Js^ js, Exception^ e);
		static Value opGetProperty(void* pobj, Value& key);
		static Value opSetProperty(void* pobj, Value& key, Value& value);
		static Value opCall(void* pobj, std::vector<Value> vargs);
		static Value opConstruct(void* pobj, std::vector<Value> vargs);
		static Value opInstanceOf(void* pobj, Value& vobj);
		static NativeJs::Operations operations = { print, opGetProperty, opSetProperty, opCall, opConstruct, opInstanceOf };

		public ref class Js sealed
		{
		public:
			ref class ThreadContextException : public Exception
			{
			};
			ref class ScriptException : public Exception
			{
			internal:
				ScriptException(String^ js) : Exception(js)
				{
				}
				ScriptException(String^ js, Exception^ cs) : Exception(js, cs)
				{
				}
			};
		private:
			int serial;
			int threadId;
			Regex^ pattern;
			NativeJs *native;
		internal:
			ReflectionCache^ refcache;
			NativeJs *validate()
			{
				if (Thread::CurrentThread->ManagedThreadId == threadId)
					return native;
				throw gcnew ThreadContextException();
			}
			Object^ v2o(Value& v, JsObject^ parent);
			Object^ v2o(Value& v);
			Value o2v(Object^ o);
		public:
			~Js()
			{
				delete native;
			}

			Js(uint32_t maxbytes);

			Js() : Js(8 * 1048576)
			{
			}

			Js^ name(String^ chunkname)
			{
				std::string name;
				for each(auto c in Encoding::UTF8->GetBytes(chunkname))
					name.push_back(c);
				validate()->name(name);
				return this;
			}

			Object^ eval(String^ code)
			{
				return v2o(validate()->eval(to_string(code)));
			}

			Object^ eval(String^ codepattern, ... array<Object^>^ parameters)
			{
				if (parameters == nullptr)
					parameters = gcnew array<Object^> { nullptr };
				StringBuilder^ sb = gcnew StringBuilder();
				std::unordered_map<int, Value> map;
				String^ prefix = gcnew String("___limax_" + serial++ + "___");
				int index = 0;
				for (Match^ match = pattern->Match(codepattern); match->Success; match = match->NextMatch())
				{
					int pos = Int32::Parse(match->Groups[1]->Value);
					sb->Append(codepattern->Substring(index, match->Index - index))->Append(" ")->Append(prefix)->Append("[" + pos + "]")->Append(" ");
					index = match->Index + match->Length;
					if (pos < parameters->Length)
						map[pos] = o2v(parameters[pos]);
				}
				sb->Append(codepattern->Substring(index, codepattern->Length - index));
				return v2o(validate()->eval(to_string(sb->ToString()), to_string(prefix), map));
			}
		};

		private ref struct CsObject
		{
			Js^ js;
			Object^ obj;
			CsObject(Js^ _js, Object^ _obj) : js(_js), obj(_obj) { }
			static CsObject^ get(void *pobj) { return safe_cast<CsObject^>(Marshal::GetObjectForNativeVariant(IntPtr(pobj))); }
		};

		public ref struct JsObject : public IDictionary
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
				JsObject^ outer;
			public:
				Collection(JsObject^ _outer, bool _needKey) : outer(_outer), needKey(_needKey) { }
				virtual property bool IsSynchronized { bool get() { return outer->IsSynchronized; }};
				virtual property int Count { int get() { return outer->Count; }; };
				virtual property Object^ SyncRoot { Object^ get(){ return outer->SyncRoot; } };
				virtual System::Collections::IEnumerator^ GetEnumerator(){ return outer->copy(needKey)->GetEnumerator(); }
				virtual void CopyTo(Array^ a, int index) { outer->copy(needKey)->CopyTo(a, index); }
			};
			ArrayList^ copy(bool needKey)
			{
				ArrayList^ a = gcnew ArrayList();
				for (auto item : js->validate()->propertyCopy(holder, needKey))
					a->Add(js->v2o(item, this));
				return a;
			}
			ArrayList^ copy()
			{
				ArrayList^ a = gcnew ArrayList();
				for (auto item : js->validate()->propertyCopy(holder))
					a->Add(DictionaryEntry(js->v2o(item.first, this), js->v2o(item.second, this)));
				return a;
			}
		internal:
			Js ^js;
			void *holder;
			~JsObject() { NativeJs::unlinkJsObject(holder); }
			JsObject(Js^ _js, void *_holder) : js(_js), holder(_holder) {}
		public:
			virtual property Object^ SyncRoot { Object^ get(){ return js; } };
			virtual property bool IsSynchronized { bool get() { return false; }};
			virtual property bool IsReadOnly { bool get() { return false; }};
			virtual property bool IsFixedSize { bool get() { return false; }};
			virtual property int Count {
				int get(){ return (int)js->v2o(js->validate()->propertyCount(holder)); }
			}
			virtual property Object^ default[Object^]
			{
				Object^ get(Object^ key){ return js->v2o(js->validate()->propertyGet(holder, to_string(key->ToString())), this); }
				void set(Object^ key, Object^ value){ Add(key, value); }
			}
			virtual property ICollection^ Keys { ICollection^ get(){ return gcnew Collection(this, true); } };
			virtual property ICollection^ Values { ICollection^ get(){ return gcnew Collection(this, false); } };
			virtual bool Contains(Object^ key){ return (bool)js->v2o(js->validate()->propertyContains(holder, to_string(key->ToString()))); }
			virtual void Add(Object^ key, Object^ value){ js->v2o(js->validate()->propertySet(holder, to_string(key->ToString()), js->o2v(value))); }
			virtual void Clear(){ js->v2o(js->validate()->propertyClear(holder)); }
			virtual void Remove(Object^ key){ js->v2o(js->validate()->propertyRemove(holder, to_string(key->ToString()))); }
			virtual void CopyTo(Array^ a, int index){ copy()->CopyTo(a, index); }
			virtual IEnumerator^ GetEnumeratorStupid() = IEnumerable::GetEnumerator{ return copy()->GetEnumerator(); }
			virtual IDictionaryEnumerator^ GetEnumerator() { return gcnew DictionaryEnumerator(copy()->GetEnumerator()); }
			virtual String^ ToString() override
			{
				Value v = js->validate()->toString(holder);
				if (v.tag != Value::T_UNDEFINED)
				{
					Object^ o = js->v2o(v);
					if (String::typeid->IsInstanceOfType(o))
						return safe_cast<String^>(o);
				}
				return __super::ToString();
			}
			static JsObject^ create(JsFunction^ constructor, ... array <Object^>^ parameters);
		};

		public ref struct JsArray sealed : public JsObject, public IList
		{
		private:
			ArrayList^ copy()
			{
				ArrayList^ a = gcnew ArrayList();
				for (auto v : js->validate()->arrayCopy(holder))
					a->Add(js->v2o(v, this));
				return a;
			}
		internal:
			JsArray(Js^ js, void *_holder) : JsObject(js, _holder) {}
		public:
			virtual property int Count
			{
				int get() new{ return (int)js->v2o(js->validate()->arrayCount(holder)); };
			};
			virtual property Object^ default[int]
			{
				Object^ get(int index){ return js->v2o(js->validate()->arrayGet(holder, index), this); }
				void set(int index, Object^ v){ js->v2o(js->validate()->arraySet(holder, index, js->o2v(v))); }
			}
			virtual int Add(Object^ value){ return (int)js->v2o(js->validate()->arrayAdd(holder, js->o2v(value))); }
			virtual bool Contains(Object^ value) new { return IndexOf(value) != -1; }
			virtual int IndexOf(Object^ value){ return (int)js->v2o(js->validate()->arrayIndexOf(holder, js->o2v(value))); }
			virtual void Insert(int index, Object^ value){ js->v2o(js->validate()->arrayInsert(holder, index, js->o2v(value))); }
			virtual void Remove(Object^ value) new { js->v2o(js->validate()->arrayRemove(holder, js->o2v(value))); }
			virtual void RemoveAt(int index){ js->v2o(js->validate()->arrayRemoveAt(holder, index)); }
			virtual void Clear() new{ js->v2o(js->validate()->arrayClear(holder)); }
			virtual void CopyTo(Array^ a, int index) new { copy()->CopyTo(a, index); }
			virtual IEnumerator^ GetEnumerator() new { return copy()->GetEnumerator(); }
		};

		private ref struct JsFunction_ sealed : public JsObject
		{
		private:
			JsObject^ parent;
			std::vector<Value> transform(array<Object^>^ parameters)
			{
				std::vector<Value> params;
				if (parameters == nullptr)
					params.push_back(js->o2v(nullptr));
				else
					for each(Object^ p in parameters)
						params.push_back(js->o2v(p));
				return params;
			}
		internal:
			JsFunction_(Js^ js, void *_holder, JsObject^ _parent) : JsObject(js, _holder), parent(_parent) {}
			Object^ Invoke(array<Object^>^ parameters)
			{
				return js->v2o(js->validate()->functionCall(holder, parent ? parent->holder : nullptr, transform(parameters)), this);
			}
			JsObject^ create(... array<Object^>^ parameters)
			{
				std::vector<Value> params;
				if (parameters == nullptr)
					params.push_back(js->o2v(nullptr));
				else
					for each(Object^ p in parameters)
						params.push_back(js->o2v(p));
				return (JsObject^)js->v2o(js->validate()->functionCreate(holder, transform(parameters)), this);
			}
		};

		JsObject^ JsObject::create(JsFunction^ constructor, ... array <Object^>^ parameters)
		{
			return ((JsFunction_^)constructor->Target)->create(parameters);
		}

		Js::Js(uint32_t maxbytes) : threadId(Thread::CurrentThread->ManagedThreadId), pattern(gcnew Regex("<\\s*(\\d+)\\s*>")),
			native(NativeJs::createInstance(&operations, maxbytes)), refcache(gcnew ReflectionCache(gcnew ReflectionCache::ToStringCast(string_cast)))
		{
		}

		static Object^ string_cast(Object^ o)
		{
			if (JsFunction::typeid->IsInstanceOfType(o))
				return safe_cast<JsFunction^>(o)->Target->ToString();
			return o ? o->ToString() : o;
		}

		static NativeJs::String to_string(String ^s)
		{
			NativeJs::String r;
			for each(auto c in s)
				r.push_back(c);
			return r;
		}

		static String^ to_string(NativeJs::String s)
		{
			StringBuilder^ sb = gcnew StringBuilder();
			for (auto c : s)
				sb->Append(c);
			return sb->ToString();
		}

		template<typename T>
		static String^ to_string(const char16_t *s, T l)
		{
			StringBuilder^ sb = gcnew StringBuilder();
			for (T i = 0; i < l; i++)
				sb->Append(s[i]);
			return sb->ToString();
		}

		Object^ Js::v2o(Value& v, JsObject^ parent)
		{
			switch (v.tag)
			{
			case Value::T_BOOLEAN:
				return v.u.b;
			case Value::T_INT32:
				return v.u.i;
			case Value::T_DOUBLE:
				return v.u.d;
			case Value::T_STRING:
			{
				String ^r = to_string(v.u.s.v, v.u.s.l);
				delete[] v.u.s.v;
				return r;
			}
			case Value::T_UNDEFINED:
				return DBNull::Value;
			case Value::T_CSOBJECT:
			{
				Object^ o = ((CsObject^)Marshal::GetObjectForNativeVariant(IntPtr(v.u.obj)))->obj;
				return ReflectionCache::Invokable::typeid->IsInstanceOfType(o) ? safe_cast<ReflectionCache::Invokable^>(o)->GetTarget() : o;
			}
			case Value::T_JSOBJECT:
				return gcnew JsObject(this, v.u.obj);
			case Value::T_JSARRAY:
				return gcnew JsArray(this, v.u.obj);
			case Value::T_JSFUNCTION:
				return gcnew JsFunction(gcnew JsFunction_(this, v.u.obj, parent), &JsFunction_::Invoke);
			case Value::T_EXCEPTION:
			{
				NativeJs::Exception *exp = (NativeJs::Exception *)v.u.obj;
				String^ js = gcnew String(exp->js.c_str(), 0, (int)exp->js.length(), Encoding::UTF8);
				throw exp->cs ? gcnew Js::ScriptException(js, safe_cast<Exception^>(safe_cast<CsObject^>(Marshal::GetObjectForNativeVariant(IntPtr(exp->cs)))->obj)) : gcnew Js::ScriptException(js);
			}
			}
			return nullptr;
		}

		Object^ Js::v2o(Value& v)
		{
			return v2o(v, nullptr);
		}

		Value Js::o2v(Object^ o)
		{
			if (!o)
				return Value::createNull();
			else switch (Type::GetTypeCode(o->GetType()))
			{
			case TypeCode::DBNull:
				return Value::createUndefined();
			case TypeCode::Boolean:
				return Value::createBoolean(safe_cast<Boolean>(o));
			case TypeCode::Byte:
				return Value::createInt32(safe_cast<Byte>(o));
			case TypeCode::SByte:
				return Value::createInt32(safe_cast<SByte>(o));
			case TypeCode::Int16:
				return Value::createInt32(safe_cast<Int16>(o));
			case TypeCode::UInt16:
				return Value::createInt32(safe_cast<UInt16>(o));
			case TypeCode::Int32:
				return Value::createInt32(safe_cast<Int32>(o));
			case TypeCode::UInt32:
			{
				UInt32 i = safe_cast<UInt32>(o);
				return i <= 0x7fffffff ? Value::createInt32(i) : Value::createDouble(i);
			}
			case TypeCode::Int64:
				return Value::createDouble(safe_cast<Int64>(o));
			case TypeCode::UInt64:
				return Value::createDouble(safe_cast<UInt64>(o));
			case TypeCode::Single:
				return Value::createDouble(safe_cast<Single>(o));
			case TypeCode::Double:
				return Value::createDouble(safe_cast<Double>(o));
			case TypeCode::Decimal:
				return Value::createDouble(safe_cast<Decimal>(o));
			case TypeCode::Char:
			{
				Char c = safe_cast<Char>(o);
				return Value::createString(&c, 1);
			}
			case TypeCode::String:
			{
				String ^s = safe_cast<String^>(o);
				return Value::createString(s, s->Length);
			}
			}
			if (JsFunction::typeid->IsInstanceOfType(o))
				return Value::createJSFunction(safe_cast<JsFunction_^>(safe_cast<JsFunction^>(o)->Target)->holder);
			if (JsArray::typeid->IsInstanceOfType(o))
				return Value::createJSArray(safe_cast<JsArray^>(o)->holder);
			if (JsObject::typeid->IsInstanceOfType(o))
				return Value::createJSObject(safe_cast<JsObject^>(o)->holder);
			_variant_t *var = new _variant_t();
			Marshal::GetNativeVariantForObject(gcnew CsObject(this, Delegate::typeid->IsInstanceOfType(o) ? refcache->GetValue(o, "Invoke") : o), IntPtr(var));
			return Value::createCSObject(var);
		}

		void print(const char *s)
		{
			Console::Write(gcnew String(s, 0, (int)strlen(s), Encoding::UTF8));
		}

		Value marshalException(Js^ js, Exception^ e)
		{
			_variant_t *ex = new _variant_t();
			Marshal::GetNativeVariantForObject(gcnew CsObject(js, e), IntPtr(ex));
			return Value::createException(ex);
		}

		Value opGetProperty(void* pobj, Value& key)
		{
			CsObject^ csobj = CsObject::get(pobj);
			Js^ js = csobj->js;
			try
			{
				return js->o2v(js->refcache->GetValue(csobj->obj, js->v2o(key)));
			}
			catch (Exception^ e)
			{
				return marshalException(js, e);
			}
		}

		Value opSetProperty(void* pobj, Value& key, Value& val)
		{
			CsObject^ csobj = CsObject::get(pobj);
			Js^ js = csobj->js;
			try
			{
				js->refcache->SetValue(csobj->obj, js->v2o(key), js->v2o(val));
				return Value::createUndefined();
			}
			catch (Exception^ e)
			{
				return marshalException(js, e);
			}
		}

		Value opCall(void* pobj, std::vector<Value> vargs)
		{
			CsObject^ csobj = CsObject::get(pobj);
			Js^ js = csobj->js;
			try
			{
				Object^ obj = csobj->obj;
				if (!ReflectionCache::Invokable::typeid->IsInstanceOfType(obj))
					throw gcnew Exception("call on non-invokable object[" + obj + "]");
				int nparameters = (int)vargs.size();
				array<Object^>^ oargs = gcnew array<Object^>(nparameters);
				for (int i = 0; i < nparameters; i++)
					oargs[i] = js->v2o(vargs[i]);
				return js->o2v(safe_cast<ReflectionCache::Invokable^>(obj)->Invoke(oargs));
			}
			catch (Exception^ e)
			{
				return marshalException(js, e);
			}
		}

		Value opConstruct(void* pobj, std::vector<Value> vargs)
		{
			CsObject^ csobj = CsObject::get(pobj);
			Js^ js = csobj->js;
			try
			{
				Object^ obj = csobj->obj;
				if (!Type::typeid->IsInstanceOfType(obj))
					throw gcnew Exception("construct on non-type object[" + obj + "]");
				int nparameters = (int)vargs.size();
				array<Object^>^ oargs = gcnew array<Object^>(nparameters);
				for (int i = 0; i < nparameters; i++)
					oargs[i] = js->v2o(vargs[i]);
				return js->o2v(js->refcache->Construct(obj, oargs));
			}
			catch (Exception^ e)
			{
				return marshalException(js, e);
			}
		}

		Value opInstanceOf(void* pobj, Value& vobj)
		{
			CsObject^ csobj = CsObject::get(pobj);
			Js^ js = csobj->js;
			try
			{
				return Type::typeid->IsInstanceOfType(csobj->obj) ? Value::createBoolean(safe_cast<Type^>(csobj->obj)->IsInstanceOfType(js->v2o(vobj))) : Value::createBoolean(false);
			}
			catch (Exception^ e)
			{
				return marshalException(js, e);
			}
		}
	}
}