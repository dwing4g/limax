#include <js/RequiredDefines.h>
#include <js/Initialization.h>
#include <js/Conversions.h>
#include <js/CharacterEncoding.h>
#include <mozilla/UniquePtr.h>
#include <jsapi.h>
#include <comutil.h>
#include <stdint.h>
#include <thread>
#include <mutex>
#include <sstream>
#include <unordered_map>
#include <functional>
#include <iostream>
#include <locale>
#include "nativejs.h"

namespace js
{
	void ReportOutOfMemory(ExclusiveContext* cxArg)
	{
		JS_ReportOutOfMemory(reinterpret_cast<JSContext *>(cxArg));
	}
}

namespace limax
{
	namespace script
	{
		JSString *JS_NewStringFromUTF8(JSContext *cx, const char *buf, size_t length)
		{
			mozilla::UniquePtr<char16_t, JS::FreePolicy> chars(JS::UTF8CharsToNewTwoByteCharsZ(cx, JS::UTF8Chars(buf, length), &length).get());
			return JS_NewUCStringCopyN(cx, chars.get(), length);
		}

		struct NativeJsImpl : public NativeJs
		{
			static JSClass global_class;
			static JSClass holder_class;
			static std::locale locale;
			Operations *operations;
			std::string chunkname;
			JSRuntime *rt;
			JSContext *cx;
			JS::PersistentRootedObject global;
			JSCompartment *cp;
			Exception *exp;
			~NativeJsImpl()
			{
				if (global)
				{
					JS_LeaveCompartment(cx, cp);
					global.reset();
				}
				if (cx)
				{
					JS_EndRequest(cx);
					JS_DestroyContext(cx);
				}
				if (rt)
					JS_DestroyRuntime(rt);
			}
			NativeJsImpl(Operations* _operations) : operations(_operations), rt(nullptr), cx(nullptr), exp(nullptr) {}
			static void buildErrorMessage(JSContext *cx, JS::HandleObject eo, JS::MutableHandleString rs);
			static void postError(JSContext *cx);
			bool init(uint32_t maxbytes);
			Value o2v(JS::HandleValue v);
			bool v2o(const Value& v, JS::MutableHandleValue r);

			virtual void name(const std::string& chunkname) override;
			virtual Value eval(const String& code) override;
			virtual Value eval(const String& code, const String& prefix, std::unordered_map<int, Value> parameters) override;
			virtual Value propertyCount(void *holder) override;
			virtual Value propertyContains(void *holder, const String& key) override;
			virtual Value propertyGet(void *holder, const String& key) override;
			virtual Value propertySet(void *holder, const String& key, const Value& value) override;
			virtual Value propertyRemove(void *holder, const String& key);
			virtual Value propertyClear(void *holder);
			virtual std::vector<std::pair<Value, Value>> propertyCopy(void *holder) override;
			virtual std::vector<Value> propertyCopy(void *holder, bool needKey) override;
			virtual Value arrayCount(void *holder) override;
			virtual Value arrayAdd(void *holder, const Value& value) override;
			virtual Value arrayGet(void *holder, uint32_t index) override;
			virtual Value arraySet(void *holder, uint32_t index, const Value& value) override;
			virtual Value arrayIndexOf(void *holder, const Value& value) override;
			virtual Value arrayInsert(void *holder, uint32_t index, const Value& value) override;
			virtual Value arrayRemove(void *holder, const Value& value) override;
			virtual Value arrayRemoveAt(void *holder, uint32_t index) override;
			virtual Value arrayClear(void *holder) override;
			virtual std::vector<Value> arrayCopy(void *holder) override;
			virtual Value functionCall(void *holder, void *parent_holder, const std::vector<Value>& parameters) override;
			virtual Value functionCreate(void *holder, const std::vector<Value>& parameters) override;
			virtual Value toString(void *holder) override;
		};

		JSClass NativeJsImpl::global_class = { "global", JSCLASS_GLOBAL_FLAGS, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, JS_GlobalObjectTraceHook };
		JSClass NativeJsImpl::holder_class =
		{
			"holder",
			JSCLASS_HAS_PRIVATE,
			nullptr,
			nullptr,
			[](JSContext *cx, JS::HandleObject obj, JS::HandleId id, JS::MutableHandleValue vp)
			{
				NativeJsImpl* impl = (NativeJsImpl*)JS_GetContextPrivate(cx);
				JS_IdToValue(cx, id, vp);
				if (vp.isSymbol())
				{
					vp.setUndefined();
					return true;
				}
				return impl->v2o(impl->operations->opGetProperty(JS_GetPrivate(obj), impl->o2v(vp)), vp);
			},
				[](JSContext* cx, JS::HandleObject obj, JS::HandleId id, JS::MutableHandleValue vp, JS::ObjectOpResult& result)
			{
				NativeJsImpl* impl = (NativeJsImpl*)JS_GetContextPrivate(cx);
				JS::RootedValue key(cx);
				JS_IdToValue(cx, id, &key);
				return impl->v2o(impl->operations->opSetProperty(JS_GetPrivate(obj), impl->o2v(key), impl->o2v(vp)), &key) ? result.succeed() : false;
			},
				nullptr,
				nullptr,
				nullptr,
				[](JSFreeOp *fop, JSObject *obj)
			{
				delete (_variant_t *)JS_GetPrivate(obj);
			},
				[](JSContext* cx, unsigned argc, JS::Value* vp)
			{
				NativeJsImpl* impl = (NativeJsImpl*)JS_GetContextPrivate(cx);
				JS::CallArgs args = JS::CallArgsFromVp(argc, vp);
				std::vector<Value> vargs;
				for (unsigned i = 0; i < argc; i++)
					vargs.push_back(impl->o2v(args[i]));
				return impl->v2o(impl->operations->opCall(JS_GetPrivate(&args.callee()), vargs), args.rval());
			},
				[](JSContext *cx, JS::HandleObject obj, JS::MutableHandleValue vp, bool *bp)
			{
				NativeJsImpl* impl = (NativeJsImpl*)JS_GetContextPrivate(cx);
				JS::RootedValue rv(cx);
				if (!impl->v2o(impl->operations->opInstanceOf(JS_GetPrivate(obj), impl->o2v(vp)), &rv))
					return false;
				*bp = rv.isTrue();
				return true;
			},
				[](JSContext* cx, unsigned argc, JS::Value* vp)
			{
				NativeJsImpl* impl = (NativeJsImpl*)JS_GetContextPrivate(cx);
				JS::CallArgs args = JS::CallArgsFromVp(argc, vp);
				std::vector<Value> vargs;
				for (unsigned i = 0; i < argc; i++)
					vargs.push_back(impl->o2v(args[i]));
				return impl->v2o(impl->operations->opConstruct(JS_GetPrivate(&args.callee()), vargs), args.rval());
			},
				JS_GlobalObjectTraceHook
		};
		std::locale NativeJsImpl::locale("");

		void NativeJsImpl::buildErrorMessage(JSContext *cx, JS::HandleObject eo, JS::MutableHandleString rs)
		{
			JS::RootedValue rv(cx);
			JS_GetProperty(cx, eo, "fileName", &rv);
			rs.set(rv.isString() ? rv.toString() : JS_NewStringCopyZ(cx, "[no filename]"));
			rs.set(JS_ConcatStrings(cx, rs, JS::RootedString(cx, JS_NewStringCopyZ(cx, ":"))));
			JS_GetProperty(cx, eo, "lineNumber", &rv);
			rs.set(JS_ConcatStrings(cx, rs, JS::RootedString(cx, JS::ToString(cx, rv))));
			rs.set(JS_ConcatStrings(cx, rs, JS::RootedString(cx, JS_NewStringCopyZ(cx, ":"))));
			JS_GetProperty(cx, eo, "message", &rv);
			rs.set(JS_ConcatStrings(cx, rs, JS::RootedString(cx, JS::ToString(cx, rv))));
			JS_GetProperty(cx, eo, "stack", &rv);
			if (rv.isString())
			{
				rs.set(JS_ConcatStrings(cx, rs, JS::RootedString(cx, JS_NewStringCopyZ(cx, "\nwith stack:\n"))));
				rs.set(JS_ConcatStrings(cx, rs, JS::RootedString(cx, JS::ToString(cx, rv))));
			}
		}

		void NativeJsImpl::postError(JSContext *cx)
		{
			JS::RootedValue rv(cx);
			if (!JS_GetPendingException(cx, &rv))
				return;
			NativeJs::Exception* exp = new NativeJs::Exception();
			JS_ClearPendingException(cx);
			JS::RootedString rs(cx);
			if (rv.isObject())
			{
				JS::RootedObject eo(cx, &rv.toObject());
				JS_GetProperty(cx, eo, "__exception__", &rv);
				exp->cs = rv.isObject() ? (_variant_t*)JS_GetPrivate(&rv.toObject()) : nullptr;
				NativeJsImpl::buildErrorMessage(cx, eo, &rs);
			}
			else
			{
				exp->cs = nullptr;
				rs.set(JS_NewStringCopyZ(cx, "[no filename]:0:uncaught exception: "));
				rs.set(JS_ConcatStrings(cx, rs, JS::RootedString(cx, JS::ToString(cx, rv))));
			}
			char *msg = JS_EncodeStringToUTF8(cx, rs);
			exp->js = std::string(msg);
			JS_free(cx, msg);
			NativeJsImpl* impl = (NativeJsImpl*)JS_GetContextPrivate(cx);
			if (impl->exp)
				delete exp;
			else
				impl->exp = exp;
		}

		bool NativeJsImpl::init(uint32_t maxbytes)
		{
			static std::once_flag once;
			std::call_once(once, [](){
				std::locale::global(locale);
				JS_Init();
			});
			rt = JS_NewRuntime(maxbytes);
			if (rt == nullptr)
				return false;
			JS_SetDefaultLocale(rt, locale.c_str());
			cx = JS_NewContext(rt, 8192);
			if (cx == nullptr)
				return false;
			JS_BeginRequest(cx);
			global.init(cx, JS_NewGlobalObject(cx, &global_class, nullptr, JS::FireOnNewGlobalHook));
			if (!global)
				return false;
			cp = JS_EnterCompartment(cx, global);
			JS_SetContextPrivate(cx, this);
			JS_InitStandardClasses(cx, global);
			JS_SetErrorReporter(rt, [](JSContext *cx, const char *message, JSErrorReport *report){
				postError(cx);
			});
			JS_DefineFunction(cx, global, "print", [](JSContext *cx, unsigned argc, JS::Value *vp){
				Operations *op = ((NativeJsImpl*)JS_GetContextPrivate(cx))->operations;
				JS::CallArgs args = JS::CallArgsFromVp(argc, vp);
				for (unsigned i = 0; i < argc; i++)
				{
					if (i)
						op->print(" ");
					char *s = JS_EncodeStringToUTF8(cx, JS::RootedString(cx, JS::ToString(cx, args[i])));
					op->print(s);
					JS_free(cx, s);
				}
				op->print("\n");
				args.rval().setUndefined();
				return true;
			}, 0, JSPROP_READONLY | JSPROP_PERMANENT);
			return true;
		}

		Value NativeJsImpl::o2v(JS::HandleValue v)
		{
			if (JS_IsExceptionPending(cx))
				postError(cx);
			if (exp)
			{
				void *tmp = exp;
				exp = nullptr;
				return Value::createException(tmp);
			}
			if (v.isNull())
				return Value::createNull();
			else if (v.isBoolean())
				return Value::createBoolean(v.isTrue());
			else if (v.isInt32())
				return Value::createInt32(v.toInt32());
			else if (v.isDouble())
				return Value::createDouble(v.toDouble());
			else if (v.isString())
			{
				char *s = JS_EncodeStringToUTF8(cx, JS::RootedString(cx, v.toString()));
				size_t l = strlen(s);
				mozilla::UniquePtr<char16_t, JS::FreePolicy> chars(JS::UTF8CharsToNewTwoByteCharsZ(cx, JS::UTF8Chars(s, l), &l).get());
				JS_free(cx, s);
				return Value::createString(chars.get(), l);
			}
			else if (v.isObject())
			{
				JS::RootedObject obj(cx, &v.toObject());
				if (JS_InstanceOf(cx, obj, &holder_class, nullptr))
					return Value::createCSObject(JS_GetPrivate(obj));
				JS::PersistentRootedObject* pobj = new JS::PersistentRootedObject(cx, obj);
				bool isArray = false;
				JS_IsArrayObject(cx, obj, &isArray);
				if (isArray)
					return Value::createJSArray(pobj);
				if (JS_ObjectIsFunction(cx, obj))
					return Value::createJSFunction(pobj);
				return Value::createJSObject(pobj);
			}
			return Value::createUndefined();
		}

		bool NativeJsImpl::v2o(const Value& v, JS::MutableHandleValue r)
		{
			switch (v.tag)
			{
			case Value::T_BOOLEAN:
				r.setBoolean(v.u.b);
				return true;
			case Value::T_INT32:
				r.setInt32(v.u.i);
				return true;
			case Value::T_DOUBLE:
				r.setDouble(v.u.d);
				return true;
			case Value::T_STRING:
				r.setString(JS_NewUCStringCopyN(cx, v.u.s.v, v.u.s.l));
				delete[] v.u.s.v;
				return true;
			case Value::T_NULL:
				r.setNull();
				return true;
			case Value::T_UNDEFINED:
				r.setUndefined();
				return true;
			case Value::T_CSOBJECT:
			{
				JSObject *obj = JS_NewObject(cx, &holder_class);
				JS_DefineFunction(cx, JS::RootedObject(cx, obj), "toString", nullptr, 0, 0);
				JS_SetPrivate(obj, v.u.obj);
				r.setObject(*obj);
				return true;
			}
			case Value::T_JSOBJECT:
			case Value::T_JSARRAY:
			case Value::T_JSFUNCTION:
				r.setObject(**(JS::PersistentRootedObject*)v.u.obj);
				return true;
			case Value::T_EXCEPTION:
			{
				JSObject *obj = JS_NewObject(cx, &holder_class);
				JS_DefineFunction(cx, JS::RootedObject(cx, obj), "toString", nullptr, 0, 0);
				JS_SetPrivate(obj, v.u.obj);
				r.setObject(*obj);
				JS::RootedValue ev(cx);
				JS_ReportError(cx, "Exception from C#");
				JS_GetPendingException(cx, &ev);
				JS::RootedObject eo(cx, &ev.toObject());
				JS_SetProperty(cx, eo, "__exception__", r);
				JS_DefineFunction(cx, JS::RootedObject(cx, &ev.toObject()), "toString", [](JSContext* cx, unsigned argc, JS::Value* vp)
				{
					JS::CallArgs args = JS::CallArgsFromVp(argc, vp);
					JS::RootedObject eo(cx, &args.thisv().toObject());
					JS::RootedString rs(cx);
					NativeJsImpl::buildErrorMessage(cx, eo, &rs);
					JS::RootedValue rv(cx);
					JS_GetProperty(cx, eo, "__exception__", &rv);
					rs.set(JS_ConcatStrings(cx, rs, JS::RootedString(cx, JS::ToString(cx, rv))));
					args.rval().setString(rs);
					return true;
				}, 0, JSPROP_READONLY | JSPROP_PERMANENT);
				r.setUndefined();
				return false;
			}
			}
			return false;
		}

		void NativeJsImpl::name(const std::string& chunkname)
		{
			this->chunkname = chunkname;
		}

		Value NativeJsImpl::eval(const String& code)
		{
			JS::RootedValue r(cx);
			JS::CompileOptions opts(cx);
			if (!chunkname.empty())
				opts.setFile(chunkname.c_str());
			JS::Evaluate(cx, opts.setIsRunOnce(true), code.c_str(), code.size(), &r);
			return o2v(r);
		}

		Value NativeJsImpl::eval(const String& code, const String& prefix, std::unordered_map<int, Value> parameters)
		{
			JS::RootedObject array(cx, JS_NewArrayObject(cx, parameters.size()));
			for (auto& o : parameters)
			{
				JS::RootedValue v(cx);
				v2o(o.second, &v);
				JS_DefineElement(cx, array, o.first, v, JSPROP_READONLY);
			}
			JS_DefineUCProperty(cx, global, prefix.c_str(), prefix.length(), array, JSPROP_READONLY);
			Value r = eval(code);
			JS_DeleteUCProperty(cx, global, prefix.c_str(), prefix.length(), JS::ObjectOpResult());
			return r;
		}

		Value NativeJsImpl::propertyCount(void *holder)
		{
			JS::Rooted<JS::IdVector> ids(cx, JS::IdVector(cx));
			JS_Enumerate(cx, *(JS::PersistentRootedObject *)holder, &ids);
			JS::RootedValue rv(cx);
			rv.setInt32((int32_t)ids.length());
			return o2v(rv);
		}

		Value NativeJsImpl::propertyContains(void *holder, const String& key)
		{
			bool found = false;
			JS_HasUCProperty(cx, *(JS::PersistentRootedObject*)holder, key.c_str(), key.length(), &found);
			JS::RootedValue rv(cx);
			rv.setBoolean(found);
			return o2v(rv);
		}

		Value NativeJsImpl::propertyGet(void *holder, const String& key)
		{
			JS::RootedValue r(cx);
			JS_GetUCProperty(cx, *(JS::PersistentRootedObject*)holder, key.c_str(), key.length(), &r);
			return o2v(r);
		}

		Value NativeJsImpl::propertySet(void *holder, const String& key, const Value& value)
		{
			JS::RootedValue v(cx);
			v2o(value, &v);
			JS_SetUCProperty(cx, *(JS::PersistentRootedObject*)holder, key.c_str(), key.length(), v);
			return o2v(JS::RootedValue(cx));
		}

		Value NativeJsImpl::propertyRemove(void *holder, const String& key)
		{
			JS::ObjectOpResult r;
			JS_DeleteUCProperty(cx, *(JS::PersistentRootedObject*)holder, key.c_str(), key.length(), r);
			return o2v(JS::RootedValue(cx));
		}

		Value NativeJsImpl::propertyClear(void *holder)
		{
			JS::Rooted<JS::IdVector> ids(cx, JS::IdVector(cx));
			JS_Enumerate(cx, *(JS::PersistentRootedObject *)holder, &ids);
			JS::ObjectOpResult r;
			for (auto id : ids)
				JS_DeletePropertyById(cx, *(JS::PersistentRootedObject*)holder, JS::RootedId(cx, id), r);
			return o2v(JS::RootedValue(cx));
		}

		std::vector<std::pair<Value, Value>> NativeJsImpl::propertyCopy(void *holder)
		{
			JS::Rooted<JS::IdVector> ids(cx, JS::IdVector(cx));
			JS_Enumerate(cx, *(JS::PersistentRootedObject *)holder, &ids);
			std::vector<std::pair<Value, Value>> r;
			r.reserve(ids.length());
			for (auto id : ids)
			{
				JS::RootedId rid(cx, id);
				JS::RootedValue k(cx);
				JS::RootedValue v(cx);
				JS_IdToValue(cx, rid, &k);
				JS_GetPropertyById(cx, *(JS::PersistentRootedObject*)holder, rid, &v);
				r.push_back(std::make_pair(o2v(k), o2v(v)));
			}
			return r;
		}

		std::vector<Value> NativeJsImpl::propertyCopy(void *holder, bool needKey)
		{
			JS::Rooted<JS::IdVector> ids(cx, JS::IdVector(cx));
			JS_Enumerate(cx, *(JS::PersistentRootedObject *)holder, &ids);
			std::vector<Value> r;
			r.reserve(ids.length());
			for (auto id : ids)
			{
				JS::RootedId rid(cx, id);
				JS::RootedValue v(cx);
				if (needKey)
					JS_IdToValue(cx, rid, &v);
				else
					JS_GetPropertyById(cx, *(JS::PersistentRootedObject*)holder, rid, &v);
				r.push_back(o2v(v));
			}
			return r;
		}

		Value NativeJsImpl::arrayCount(void *holder)
		{
			uint32_t length;
			JS_GetArrayLength(cx, *(JS::PersistentRootedObject*)holder, &length);
			JS::RootedValue rv(cx);
			rv.setInt32((int32_t)length);
			return o2v(rv);
		}

		Value NativeJsImpl::arrayAdd(void *holder, const Value& value)
		{
			uint32_t length;
			JS::RootedValue rv(cx);
			v2o(value, &rv);
			JS_GetArrayLength(cx, *(JS::PersistentRootedObject*)holder, &length);
			JS_SetElement(cx, *(JS::PersistentRootedObject*)holder, length, rv);
			rv.setInt32((int32_t)length);
			return o2v(rv);
		}

		Value NativeJsImpl::arrayGet(void *holder, uint32_t index)
		{
			JS::RootedValue rv(cx);
			JS_GetElement(cx, *(JS::PersistentRootedObject*)holder, index, &rv);
			return o2v(rv);
		}

		Value NativeJsImpl::arraySet(void *holder, uint32_t index, const Value& value)
		{
			JS::RootedValue rv(cx);
			v2o(value, &rv);
			JS_SetElement(cx, *(JS::PersistentRootedObject*)holder, index, rv);
			return o2v(JS::RootedValue(cx));
		}

		Value NativeJsImpl::arrayIndexOf(void *holder, const Value& value)
		{
			JS::RootedValue c(cx);
			JS::RootedValue v(cx);
			v2o(value, &c);
			bool same = false;
			uint32_t length;
			JS_GetArrayLength(cx, *(JS::PersistentRootedObject*)holder, &length);
			for (uint32_t i = 0; i < length; i++)
			{
				JS_GetElement(cx, *(JS::PersistentRootedObject*)holder, i, &v);
				JS_SameValue(cx, c, v, &same);
				if (same)
				{
					v.setInt32((int32_t)i);
					return o2v(v);
				}
			}
			v.setInt32(-1);
			return o2v(v);
		}

		Value NativeJsImpl::arrayInsert(void *holder, uint32_t index, const Value& value)
		{
			JS::RootedValue rv(cx);
			uint32_t i;
			JS_GetArrayLength(cx, *(JS::PersistentRootedObject*)holder, &i);
			for (; i > index; i--)
			{
				JS_GetElement(cx, *(JS::PersistentRootedObject*)holder, i - 1, &rv);
				JS_SetElement(cx, *(JS::PersistentRootedObject*)holder, i, rv);
			}
			v2o(value, &rv);
			JS_SetElement(cx, *(JS::PersistentRootedObject*)holder, index, rv);
			return o2v(JS::RootedValue(cx));
		}

		Value NativeJsImpl::arrayRemove(void *holder, const Value& value)
		{
			JS::AutoValueVector tmp(cx);
			JS::RootedValue c(cx);
			JS::RootedValue v(cx);
			v2o(value, &c);
			uint32_t i = 0, length;
			JS_GetArrayLength(cx, *(JS::PersistentRootedObject*)holder, &length);
			for (; i < length; i++)
			{
				bool same = false;
				JS_GetElement(cx, *(JS::PersistentRootedObject*)holder, i, &v);
				JS_SameValue(cx, c, v, &same);
				if (!same)
					tmp.append(v);
			}
			if (tmp.length() != length)
			{
				for (i = 0, length = (uint32_t)tmp.length(); i < length; i++)
					JS_SetElement(cx, *(JS::PersistentRootedObject*)holder, i, tmp[i]);
				JS_SetArrayLength(cx, *(JS::PersistentRootedObject*)holder, length);
			}
			return o2v(JS::RootedValue(cx));
		}

		Value NativeJsImpl::arrayRemoveAt(void *holder, uint32_t index)
		{
			uint32_t length;
			JS_GetArrayLength(cx, *(JS::PersistentRootedObject*)holder, &length);
			if (index >= length)
				return o2v(JS::RootedValue(cx));
			JS::RootedValue v(cx);
			for (length--; index < length; index++)
			{
				JS_GetElement(cx, *(JS::PersistentRootedObject*)holder, index + 1, &v);
				JS_SetElement(cx, *(JS::PersistentRootedObject*)holder, index, v);
			}
			JS_SetArrayLength(cx, *(JS::PersistentRootedObject*)holder, length);
			return o2v(JS::RootedValue(cx));
		}

		Value NativeJsImpl::arrayClear(void *holder)
		{
			JS_SetArrayLength(cx, *(JS::PersistentRootedObject*)holder, 0);
			return o2v(JS::RootedValue(cx));
		}

		std::vector<Value> NativeJsImpl::arrayCopy(void *holder)
		{
			std::vector<Value> r;
			uint32_t length;
			JS_GetArrayLength(cx, *(JS::PersistentRootedObject*)holder, &length);
			r.reserve(length);
			JS::RootedValue v(cx);
			for (uint32_t i = 0; i < length; i++)
			{
				JS_GetElement(cx, *(JS::PersistentRootedObject*)holder, i, &v);
				r.push_back(o2v(v));
			}
			return r;
		}

		Value NativeJsImpl::functionCall(void *holder, void *parent_holder, const std::vector<Value>& parameters)
		{
			JS::RootedValue v(cx);
			v.setObject(**(JS::PersistentRootedObject*)holder);
			JS::RootedFunction func(cx, JS_ValueToFunction(cx, v));
			JS::AutoValueVector params(cx);
			for (auto& p : parameters)
			{
				v2o(p, &v);
				params.append(v);
			}
			JS::Call(cx, parent_holder ? *(JS::PersistentRootedObject*)parent_holder : global, func, params, &v);
			return o2v(v);
		}

		Value NativeJsImpl::functionCreate(void *holder, const std::vector<Value>& parameters)
		{
			JS::RootedValue v(cx);
			JS::AutoValueVector params(cx);
			for (auto& p : parameters)
			{
				v2o(p, &v);
				params.append(v);
			}
			v.setObject(**(JS::PersistentRootedObject*)holder);
			JS::Construct(cx, v, params, &v);
			return o2v(v);
		}

		Value NativeJsImpl::toString(void *holder)
		{
			JS::RootedValue rv(cx);
			rv.setObject(**(JS::PersistentRootedObject*)holder);
			rv.setString(JS::ToString(cx, rv));
			return o2v(JS::RootedValue(cx, rv));
		}

		void NativeJs::unlinkJsObject(void *holder)
		{
			delete (JS::PersistentRootedObject *)holder;
		}

		NativeJs* NativeJs::createInstance(Operations* operations, uint32_t maxbytes)
		{
			NativeJsImpl* impl = new NativeJsImpl(operations);
			if (impl->init(maxbytes))
				return impl;
			delete impl;
			return nullptr;
		}
	}
}