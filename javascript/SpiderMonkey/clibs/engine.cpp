#include "common.h"

namespace js
{
	void ReportOutOfMemory(ExclusiveContext* cxArg)
	{
		JS_ReportOutOfMemory(reinterpret_cast<JSContext *>(cxArg));
	}
}

namespace limax
{

	JSClass JsEngine::global_class = { "global", JSCLASS_GLOBAL_FLAGS, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, JS_GlobalObjectTraceHook };

	JsEngine::JsEngine() : rt(nullptr), cx(nullptr), thread(std::thread([this](){
		while (true)
		{
			Task task = tasks.get();
			if (task == nullptr)
				break;
			task(rt, cx, global);
		}
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
	}))
	{
	}

	JsEngine::~JsEngine()
	{
		tasks.put(nullptr);
		thread.join();
	}

	bool JsEngine::init(uint32_t maxbytes)
	{
		static std::once_flag once;
		std::call_once(once, JS_Init);
		rt = JS_NewRuntime(maxbytes);
		if (!rt)
			return false;
		cx = JS_NewContext(rt, 8192);
		if (!cx)
			return false;
		JS_BeginRequest(cx);
		global.init(cx, JS_NewGlobalObject(cx, &global_class, nullptr, JS::FireOnNewGlobalHook));
		if (!global)
			return false;
		cp = JS_EnterCompartment(cx, global);
		return JS_InitStandardClasses(cx, global);
	}

	void JsEngine::execute(Task r)
	{
		tasks.put(r);
	}

	void JsEngine::wait(Task r)
	{
		if (thread.get_id() == std::this_thread::get_id()) {
			r(rt, cx, global);
			return;
		}
		std::mutex mutex;
		std::condition_variable_any cond;
		std::lock_guard<std::mutex> l(mutex);
		execute([r, &mutex, &cond](JSRuntime* rt, JSContext* cx, JS::HandleObject global){
			std::lock_guard<std::mutex> l(mutex);
			r(rt, cx, global);
			cond.notify_one();
		});
		cond.wait(mutex);
	}

	std::shared_ptr<JsEngine> JsEngine::create(uint32_t maxbytes)
	{
		auto r = std::shared_ptr<JsEngine>(new JsEngine());
		bool running;
		r->wait([maxbytes, &r, &running](JSRuntime*, JSContext*, JS::HandleObject){ running = r->init(maxbytes); });
		if (!running)
			r.reset();
		return r;
	}

	JSString *JS_NewStringFromUTF8(JSContext *cx, const char *buf, size_t length)
	{
		mozilla::UniquePtr<char16_t, JS::FreePolicy> chars(JS::UTF8CharsToNewTwoByteCharsZ(cx, JS::UTF8Chars(buf, length), &length).get());
		return JS_NewUCStringCopyN(cx, chars.get(), length);
	}
}
