#pragma once 

#ifdef LIMAX_OS_WINDOWS
#	ifndef LIMAX_BUILD_AS_STATIC
#		ifdef LIMAXJS_EXPORTS
#define LIMAX_JS_API __declspec( dllexport )
#pragma warning( disable: 4275)
#		else
#define LIMAX_JS_API
#		endif
#	else
#define LIMAX_JS_API
#	endif
#else
#define LIMAX_JS_API
#endif

namespace limax
{
	class LIMAX_JS_API JsEngine
	{
	public:
		typedef std::function<void(JSRuntime*, JSContext*, JS::HandleObject)> Task;
	private:
		static JSClass global_class;
		JSRuntime *rt;
		JSContext *cx;
		JS::PersistentRootedObject global;
		JSCompartment *cp;
		BlockingQueue<Task> tasks;
		std::thread thread;
		JsEngine();
		bool init(uint32_t maxbytes);
	public:
		~JsEngine();
		void execute(Task);
		void wait(Task);
		static std::shared_ptr<JsEngine> create(uint32_t maxbytes);
	};

	typedef std::shared_ptr<JsEngine> JsEnginePtr;

	LIMAX_JS_API JSString *JS_NewStringFromUTF8(JSContext *cx, const char *buf, size_t length);

	class LIMAX_JS_API JsCreator
	{
		JsCreator();
		~JsCreator();
	public:
		static ScriptEngineHandlePtr createScriptEngineHandle(JsEnginePtr engine, const char *initscript, std::vector<int> providers, DictionaryCachePtr cache, TunnelReceiver ontunnel);
		static ScriptEngineHandlePtr createScriptEngineHandle(JsEnginePtr engine, const char *initscript, std::vector<int> providers, DictionaryCachePtr cache);
		static ScriptEngineHandlePtr createScriptEngineHandle(JsEnginePtr engine, const char *initscript, std::vector<int> providers);
		static ScriptEngineHandlePtr createScriptEngineHandle(JsEnginePtr engine, const char *initscript, DictionaryCachePtr cache);
		static ScriptEngineHandlePtr createScriptEngineHandle(JsEnginePtr engine, const char *initscript);
	};
}