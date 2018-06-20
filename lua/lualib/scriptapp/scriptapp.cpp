#include <limax.h>
#include <iostream>

#include <lua.hpp>
#include "../include/limax.lua.h"

using namespace limax;

class MyApp : public EndpointListener
{
	lua_State* L;
public:
	MyApp()
	{
		L = luaL_newstate();
		luaL_openlibs(L);
		int e = luaL_dofile(L, "callback.lua");
		if (e != LUA_OK)
		{
			std::cout << "lua load 'callback.lua' failed! " << lua_tostring(L, -1) << std::endl;
			exit(-1);
		}
		auto sehptr = LuaCreator::createScriptEngineHandle(L, -1, false, [this](int s, int e, const std::string& m){ onErrorOccured(s, e, m); });
		if (!sehptr)
			exit(-1);
		lua_pop(L, 1);

		Endpoint::openEngine();
		LoginConfigPtr loginConfig = LoginConfig::plainLogin("testscriptapp", "123456", "test");
		auto config = Endpoint::createEndpointConfigBuilder("127.0.0.1", 10000, loginConfig)
			->scriptEngineHandle( sehptr)
			->build();
		Endpoint::start(config, this);
	}
	~MyApp(){
		std::mutex mutex;
		std::condition_variable_any cond;
		std::lock_guard<std::mutex> l(mutex);
		Endpoint::closeEngine([&](){
			std::lock_guard<std::mutex> l(mutex);
			cond.notify_one();
		});
		cond.wait(mutex);
		
		lua_close(L);
	}
	void run()	{ Sleep(2000); }
	void onManagerInitialized(EndpointManager*, EndpointConfig*) { std::cout << "onManagerInitialized" << std::endl; }
	void onManagerUninitialized(EndpointManager*) { std::cout << "onManagerUninitialized" << std::endl; }
	void onTransportAdded(Transport*) {
		std::cout << "onTransportAdded" << std::endl;
	}
	void onTransportRemoved(Transport*){ std::cout << "onTransportRemoved" << std::endl; }
	void onAbort(Transport*) { std::cout << "onAbort" << std::endl; }
	void onSocketConnected() { std::cout << "onSocketConnected" << std::endl; }
	void onKeyExchangeDone() { std::cout << "onKeyExchangeDone" << std::endl; }
	void onKeepAlived(int ping) { std::cout << "onKeepAlived " << ping << std::endl; }
	void onErrorOccured(int errorsource, int errorvalue, const std::string& info) { std::cout << "onErrorOccured " << errorsource << " " << errorvalue << " " << info << std::endl; }
	void destroy() {}
};

int main(int argc, char* argv[])
{
	MyApp().run();
	return 0;
}

