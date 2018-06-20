#include "common.h"
#include "oshelper.h"
#include "luahelper.h"


struct HoldObject
{
	lua_State* L;
	int obj;

	HoldObject(lua_State* _L_, int _o)
		: L(_L_), obj(_o)
	{}
	~HoldObject()
	{
		releaseObjectFromGlobal(L, obj);
	}
};
static std::shared_ptr<HoldObject> g_TraceDestinationObject;

class EndpointObject
{
	lua_State*	lua_state;
	int lua_ref_config = -1;
	int lua_ref_manager = -1;
	int lua_ref_variant_callback = -1;
	int lua_ref_variant = -1;
	int closeReason = 0;
	std::shared_ptr<limax::EndpointConfig> config;
	limax::EndpointManager* manager = nullptr;
	limax::LuaVariantContextPtr variant;
	volatile bool isLoginDone = false;

	class Listener : public limax::EndpointListener
	{
		EndpointObject* object;
	public:
		Listener(EndpointObject* _object)
			: object(_object)
		{}
	public:
		virtual void onAbort(limax::Transport*) override
		{
			auto l = object->lua_state;
			auto c = object->lua_ref_config;
			fireNoitfyNoParam(object->lua_state, object->lua_ref_config, "onAbort", [l, c](int s, int e, const std::string& m) { fireErrorOccured(l, c, s, e, m); });
		}
		virtual void onTransportRemoved(limax::Transport*) override
		{
			object->isLoginDone = false;
			auto l = object->lua_state;
			auto c = object->lua_ref_config;
			auto lsec = [l, c](int s, int e, const std::string& m) { fireErrorOccured(l, c, s, e, m); };
			fireNoitfyOneParam(object->lua_state, object->lua_ref_variant, "onclose", object->closeReason, lsec);
			fireNoitfyNoParam(object->lua_state, object->lua_ref_config, "onTransportRemoved", lsec);

			auto variant = object->variant;
			limax::runOnUiThread([variant]()
			{
				if (auto v = variant)
					v->doClose();
			});
		}
		virtual void onErrorOccured(int errorsrouce, int errorvalue, const std::string& info) override
		{
			if (limax::SOURCE_LIMAX == errorsrouce && info == "switcherendpoint::SessionKick")
				object->closeReason = errorvalue;
			fireErrorOccured(object->lua_state, object->lua_ref_config, errorsrouce, errorvalue, info);
		}

		virtual void onTransportAdded(limax::Transport*) override
		{
			object->isLoginDone = true;

			{
				lua_State* L = object->lua_state;
				GetGlobalObject _dummy_object(L, object->lua_ref_manager);

				lua_pushinteger(L, object->manager->getSessionId());
				lua_setfield(L, -2, "sessionId");

				lua_pushinteger(L, object->manager->getAccountFlags());
				lua_setfield(L, -2, "accountFlags");

				const auto& pvids = object->config->getVariantProviderIds();
				if (!pvids.empty())
				{
					object->variant = limax::LuaCreator::createLuaVariantContext(L, object->lua_ref_config);
					object->variant->createEndpointManagerTable(object->manager, object->config);
					lua_pushvalue(L, -1);
					object->lua_ref_variant = appendObjectToGlobal(L);
					lua_setfield(L, -2, "variant");

					if (-1 != object->lua_ref_variant_callback)
					{
						getObjectFromGlobal(L, object->lua_ref_variant_callback);
						getObjectFromGlobal(L, object->lua_ref_variant);
						int ec = lua_pcall(L, 1, 0, 0);
						if (LUA_OK != ec)
						{
							const char* msg = lua_tostring(L, -1);
							onErrorOccured(limax::SOURCE_LUA, limax::LUA_CALL_LUA_FUNC_FAILED, msg);
							lua_pop(L, 1);
						}
						releaseObjectFromGlobal(L, object->lua_ref_variant_callback);
						object->lua_ref_variant_callback = -1;
					}
				}
			}
			auto l = object->lua_state;
			auto c = object->lua_ref_config;
			auto lsec = [l, c](int s, int e, const std::string& m) { fireErrorOccured(l, c, s, e, m); };
			fireNoitfyNoParam(object->lua_state, object->lua_ref_config, "onTransportAdded", lsec);
			fireNoitfyNoParam(object->lua_state, object->lua_ref_variant, "onopen", lsec);
		}

		virtual void onKeepAlived(int ping) override
		{
			auto l = object->lua_state;
			auto c = object->lua_ref_config;
			fireNoitfyOneParam(object->lua_state, object->lua_ref_config, "onKeepAlived", ping, [l, c](int s, int e, const std::string& m) { fireErrorOccured(l, c, s, e, m); });
		}

		virtual void onSocketConnected() override
		{
			auto l = object->lua_state;
			auto c = object->lua_ref_config;
			fireNoitfyNoParam(object->lua_state, object->lua_ref_config, "onSocketConnected", [l, c](int s, int e, const std::string& m) { fireErrorOccured(l, c, s, e, m); });
		}
		virtual void onKeyExchangeDone() override
		{
			auto l = object->lua_state;
			auto c = object->lua_ref_config;
			fireNoitfyNoParam(object->lua_state, object->lua_ref_config, "onKeyExchangeDone", [l, c](int s, int e, const std::string& m) { fireErrorOccured(l, c, s, e, m); });
		}

		virtual void onManagerInitialized(limax::EndpointManager* mng, limax::EndpointConfig*) override
		{
			object->manager = mng;
			auto l = object->lua_state;
			auto c = object->lua_ref_config;
			fireNoitfyNoParam(object->lua_state, object->lua_ref_config, "onManagerInitialized", [l, c](int s, int e, const std::string& m) { fireErrorOccured(l, c, s, e, m); });
		}

		virtual void onManagerUninitialized(limax::EndpointManager*) override
		{
			object->manager = nullptr;
			auto l = object->lua_state;
			auto c = object->lua_ref_config;
			fireNoitfyNoParam(object->lua_state, object->lua_ref_config, "onManagerUninitialized", [l, c](int s, int e, const std::string& m) { fireErrorOccured(l, c, s, e, m); });
		}

		virtual void destroy() override
		{
			delete this;
		}
	};
	friend class Listener;
	Listener* listener;

	static std::mutex	object_mutex;
	static std::unordered_set<EndpointObject*> object_set;
public:
	EndpointObject(lua_State* _lua_state)
		: lua_state(_lua_state), listener(nullptr)
	{}
	~EndpointObject()
	{
		lua_State* L = lua_state;
		releaseObjectFromGlobal(L, lua_ref_config);
		releaseObjectFromGlobal(L, lua_ref_manager);
		releaseObjectFromGlobal(L, lua_ref_variant);
		releaseObjectFromGlobal(L, lua_ref_variant_callback);
	}
public:
	void start(std::shared_ptr<limax::EndpointConfig> _config, int refconfig, int refmanager, int refvariant)
	{
		lua_ref_config = refconfig;
		lua_ref_manager = refmanager;
		lua_ref_variant_callback = refvariant;
		config = _config;
		listener = new Listener(this);
		limax::Endpoint::start(config, listener);

		std::lock_guard<std::mutex> __locker__(object_mutex);
		object_set.insert(this);
	}

	void release(lua_State* L)
	{
		if (manager)
		{
			manager->close();
		}
		else
		{
			{
				std::lock_guard<std::mutex> __locker__(object_mutex);
				object_set.erase(this);
			}
			delete this;
		}
	}

	static void ReleaseObjects()
	{
		std::unordered_set<EndpointObject*> objs;
		{
			std::lock_guard<std::mutex> __locker__(object_mutex);
			object_set.swap(objs);
		}
		for (auto obj : objs)
			delete obj;
	}
};

std::mutex	EndpointObject::object_mutex;
std::unordered_set<EndpointObject*> EndpointObject::object_set;

int lua_context_object_close(lua_State* L)
{
	lua_getfield(L, -1, getLimaxUserDataName());
	EndpointObject* obj = (EndpointObject*)lua_touserdata(L, -1);
	lua_pop(L, 1);
	obj->release(L);
	return 0;
}

int lua_create_context_and_start_endpoint(lua_State* L)
{
	lua_pushstring(L, "ping");
	lua_gettable(L, -2);
	int isping = lua_toboolean(L, -1);
	lua_pop(L, 1);

	std::string serverip;
	int serverport;
	{
		lua_pushstring(L, "serverip");
		lua_gettable(L, -2);
		serverip = lua_check_string(L, -1);
		lua_pop(L, 1);

		lua_pushstring(L, "serverport");
		lua_gettable(L, -2);
		serverport = (int)luaL_checkinteger(L, -1);
		lua_pop(L, 1);
	}

	auto object = std::unique_ptr<EndpointObject>(new EndpointObject(L));
	std::shared_ptr<limax::EndpointConfigBuilder> builder;
	if (isping)
	{
		builder = limax::Endpoint::createPingOnlyConfigBuilder(serverip, serverport);
	}
	else
	{
		lua_pushstring(L, "username");
		lua_gettable(L, -2);
		std::string username = lua_check_string(L, -1);
		lua_pop(L, 1);

		lua_pushstring(L, "token");
		lua_gettable(L, -2);
		std::string token = lua_check_string(L, -1);
		lua_pop(L, 1);

		lua_pushstring(L, "platflag");
		lua_gettable(L, -2);
		std::string paltflag = lua_check_string(L, -1);
		lua_pop(L, 1);

		builder = limax::Endpoint::createEndpointConfigBuilder(serverip, serverport, limax::LoginConfig::plainLogin(username, token, paltflag));

		lua_pushstring(L, "auanyservice");
		lua_gettable(L, -2);
		int auanyservice = 1;
		if(lua_isboolean(L, -1))
			auanyservice = lua_toboolean(L, -1);
		lua_pop(L, 1);
		builder->auanyService(!!auanyservice);
	}

	int refvariantcallback = -1;
	{
		lua_pushstring(L, "variant");
		lua_gettable(L, -2);
		if (lua_istable(L, -1))
		{
			std::vector<int32_t> pvids;
			lua_pushfstring(L, "pvids");
			lua_gettable(L, -2);
			if (lua_istable(L, -1))
			{
				int n = (int)lua_rawlen(L, -1);
				pvids.reserve(n);
				for (int i = 1; i <= n; i++)
				{
					lua_rawgeti(L, -1, i);
					int32_t pvid = (int32_t)luaL_checkinteger(L, -1);
					pvids.push_back(pvid);
					lua_pop(L, 1);
				}
				builder->variantProviderIds(pvids);
			}
			lua_pop(L, 1);

			if (!pvids.empty())
			{
				lua_pushfstring(L, "callback");
				lua_gettable(L, -2);
				if (lua_isfunction(L, -1))
					refvariantcallback = appendObjectToGlobal(L);
				else
					lua_pop(L, 1);
			}
			else
			{
				luaL_error(L, "variant table need member : pvids(table) [, callback(function)]");
			}
		}
		lua_pop(L, 1);
	}

	lua_pushvalue(L, -1);
	int refconfig = appendObjectToGlobal(L);
	{
		lua_pushstring(L, "script");
		lua_gettable(L, -2);
		lua_pushstring(L, "fastscript");
		lua_gettable(L, -3);
		bool fastscript = lua_istable(L, -1);
		if (fastscript || lua_istable(L, -2))
		{
			limax::ScriptEngineHandlePtr seh = limax::LuaCreator::createScriptEngineHandle(L, fastscript ? -1 : -2, fastscript, [L, refconfig](int s, int e, const std::string& m) { fireErrorOccured(L, refconfig, s, e, m); });
			if (seh)
				builder->scriptEngineHandle(seh);
		}
		lua_pop(L, 2);
	}
	builder->executor(limax::runOnUiThread);

	lua_newtable(L);
	lua_pushlightuserdata(L, object.get());
	lua_setfield(L, -2, getLimaxUserDataName());

	lua_pushcfunction(L, lua_context_object_close);
	lua_setfield(L, -2, "close");

	lua_pushvalue(L, -1);
	int refmanager = appendObjectToGlobal(L);

	object->start(builder->build(), refconfig, refmanager, refvariantcallback);
	object.release();
	return 1;
}

int lua_open_engine(lua_State* L)
{
	limax::Endpoint::openEngine();
	return 0;
}

int lua_close_engine(lua_State* L)
{
	volatile bool done = false;
	limax::Endpoint::closeEngine([&done](){ done = true; });
	while (!done)
	{
		limax::uiThreadSchedule();
		std::this_thread::sleep_for(std::chrono::milliseconds(1));
	}
	EndpointObject::ReleaseObjects();
	g_TraceDestinationObject.reset();
	return 0;
}

int lua_idle_process(lua_State* L)
{
	limax::uiThreadSchedule();
	return 0;
}

int lua_idle_process_time(lua_State* L)
{
	int time = (int)luaL_checkinteger(L, -1);
	limax::uiThreadScheduleTime(time);
	return 0;
}

int lua_idle_process_count(lua_State* L)
{
	int count = (int)luaL_checkinteger(L, -1);
	limax::uiThreadScheduleCount(count);
	return 0;
}

int lua_http_download(lua_State* L)
{
	std::string url = lua_check_string(L, 1);
	int timeout = (int)luaL_checkinteger(L, 2);
	int maxsize = (int)luaL_checkinteger(L, 3);
	std::string cachedir = lua_check_string(L, 4);
	bool staleenable = (int)lua_toboolean(L, 5) != 0;
	auto result = limax::http::httpDownload(url, timeout, (size_t)maxsize, cachedir, staleenable);
	lua_pushlstring(L, result.c_str(), result.size());
	return 1;
}

int lua_open_trace(lua_State* L)
{
	limax::Trace::Destination dest;
	limax::Trace::Level level = (limax::Trace::Level)luaL_checkinteger(L, -1);
	if (lua_isfunction(L, -2))
	{
		lua_pushvalue(L, -2);
		int obj = appendObjectToGlobal(L);
		g_TraceDestinationObject = std::make_shared<HoldObject>(L, obj);
		std::weak_ptr<HoldObject> destobj = g_TraceDestinationObject;
		dest = [destobj](const std::string& msg)
		{
			if (auto o = destobj.lock())
			{
				getObjectFromGlobal(o->L, o->obj);
				lua_pushlstring(o->L, msg.c_str(), msg.size());
				int ec = lua_pcall(o->L, 1, 0, 0);
				if (LUA_OK != ec)
					lua_pop(o->L, 1);
			}
		};
	}
	else
	{
		dest = [](const std::string& msg){ printf("%s\n", msg.c_str()); };
	}
	limax::Trace::open(dest, level);
	return 0;
}

limax::AuanyService::Result buildAuanyServiceResult(lua_State* L, int arg)
{
	luaL_checktype(L, arg, LUA_TFUNCTION);
	lua_pushvalue(L, arg);
	int id = appendObjectToGlobal(L);
	return [L, id](int s, int e, const std::string& c)
	{
		getObjectFromGlobal(L, id);
		lua_pushinteger(L, s);
		lua_pushinteger(L, e);
		lua_pushlstring(L, c.c_str(), c.size());
		int ec = lua_pcall(L, 3, 0, 0);
		if (LUA_OK != ec)
		{
			if (limax::Trace::isErrorEnabled())
			{
				const char* info = lua_tostring(L, -1);
				std::ostringstream oss;
				oss << "buildAuanyServiceResult result call failed! " << info;
				limax::Trace::error(oss.str());
			}
			lua_pop(L, 1);
		}
		releaseObjectFromGlobal(L, id);
	};
}

int lua_auservice_derive(lua_State* L)
{
	int top = lua_gettop(L);
	if (4 == top)
	{
		std::string credential = lua_check_string(L, 1);
		std::string authcode = lua_check_string(L, 2);
		long timeout = (long)luaL_checkinteger(L, 3);
		limax::AuanyService::Result onresult = buildAuanyServiceResult(L, 4);
		limax::AuanyService::derive(credential, authcode, timeout, onresult);
	}
	else if (6 == top)
	{
		std::string httpHost = lua_check_string(L, 1);
		int httpPort = (int)luaL_checkinteger(L, 2);
		int appid = (int)luaL_checkinteger(L, 3);
		std::string authcode = lua_check_string(L, 4);
		long timeout = (long)luaL_checkinteger(L, 5);
		limax::AuanyService::Result onresult = buildAuanyServiceResult(L, 6);
		limax::AuanyService::derive(httpHost, httpPort, appid, authcode, timeout, onresult);
	}
	else
	{
		lua_error(L);
	}
	return 0;
}

int lua_auservice_bind(lua_State* L)
{
	int top = lua_gettop(L);
	if (7 == top)
	{
		std::string credential = lua_check_string(L, 1);
		std::string authcode = lua_check_string(L, 2);
		std::string username = lua_check_string(L, 3);
		std::string token = lua_check_string(L, 4);
		std::string platflag = lua_check_string(L, 5);
		long timeout = (long)luaL_checkinteger(L, 6);
		limax::AuanyService::Result onresult = buildAuanyServiceResult(L, 7);
		limax::AuanyService::bind(credential, authcode, limax::LoginConfig::plainLogin(username, token, platflag), timeout, onresult);
	}
	else if (9 == top)
	{
		std::string httpHost = lua_check_string(L, 1);
		int httpPort = (int)luaL_checkinteger(L, 2);
		int appid = (int)luaL_checkinteger(L, 3);
		std::string authcode = lua_check_string(L, 4);
		std::string username = lua_check_string(L, 5);
		std::string token = lua_check_string(L, 6);
		std::string platflag = lua_check_string(L, 7);
		long timeout = (long)luaL_checkinteger(L, 8);
		limax::AuanyService::Result onresult = buildAuanyServiceResult(L, 9);
		limax::AuanyService::bind(httpHost, httpPort, appid, authcode, limax::LoginConfig::plainLogin(username, token, platflag), timeout, onresult);
	}
	else
	{
		lua_error(L);
	}
	return 0;
}

int lua_auservice_temporary(lua_State* L)
{
	int top = lua_gettop(L);
	if (8 == top)
	{
		std::string credential = lua_check_string(L, 1);
		std::string authcode = lua_check_string(L, 2);
		std::string authcode2 = lua_check_string(L, 3);
		long millisecond = (long)luaL_checkinteger(L, 4);
		int8_t usage = (int8_t)luaL_checkinteger(L, 5);
		std::string subid = lua_check_string(L, 6);
		long timeout = (long)luaL_checkinteger(L, 7);
		limax::AuanyService::Result onresult = buildAuanyServiceResult(L, 8);
		limax::AuanyService::temporary(credential, authcode, authcode2, millisecond, usage, subid, timeout, onresult);
	}
	else if (11 == top)
	{
		std::string httpHost = lua_check_string(L, 1);
		int httpPort = (int)luaL_checkinteger(L, 2);
		int appid = (int)luaL_checkinteger(L, 3);
		std::string credential = lua_check_string(L, 4);
		std::string authcode = lua_check_string(L, 5);
		std::string authcode2 = lua_check_string(L, 6);
		long millisecond = (long)luaL_checkinteger(L, 7);
		int8_t usage = (int8_t)luaL_checkinteger(L, 8);
		std::string subid = lua_check_string(L, 9);
		long timeout = (long)luaL_checkinteger(L, 10);
		limax::AuanyService::Result onresult = buildAuanyServiceResult(L, 11);
		limax::AuanyService::temporary(httpHost, httpPort, appid, credential, authcode, authcode2, millisecond, usage, subid, timeout, onresult);
	}
	else if (12 == top)
	{
		std::string httpHost = lua_check_string(L, 1);
		int httpPort = (int)luaL_checkinteger(L, 2);
		int appid = (int)luaL_checkinteger(L, 3);
		std::string username = lua_check_string(L, 4);
		std::string token = lua_check_string(L, 5);
		std::string platflag = lua_check_string(L, 6);
		std::string authcode = lua_check_string(L, 7);
		long millisecond = (long)luaL_checkinteger(L, 8);
		int8_t usage = (int8_t)luaL_checkinteger(L, 9);
		std::string subid = lua_check_string(L, 10);
		long timeout = (long)luaL_checkinteger(L, 11);
		limax::AuanyService::Result onresult = buildAuanyServiceResult(L, 12);
		limax::AuanyService::temporary(httpHost, httpPort, appid, limax::LoginConfig::plainLogin(username, token, platflag), authcode, millisecond, usage, subid, timeout, onresult);
	}
	else if (10 == top)
	{
		std::string username = lua_check_string(L, 1);
		std::string token = lua_check_string(L, 2);
		std::string platflag = lua_check_string(L, 3);
		int appid = (int)luaL_checkinteger(L, 4);
		std::string authcode = lua_check_string(L, 5);
		long millisecond = (long)luaL_checkinteger(L, 6);
		int8_t usage = (int8_t)luaL_checkinteger(L, 7);
		std::string subid = lua_check_string(L, 8);
		long timeout = (long)luaL_checkinteger(L, 9);
		limax::AuanyService::Result onresult = buildAuanyServiceResult(L, 10);
		limax::AuanyService::temporary(limax::LoginConfig::plainLogin(username, token, platflag), appid, authcode, millisecond, usage, subid, timeout, onresult);
	}
	else
	{
		lua_error(L);
	}
	return 0;
}

int lua_auservice_pay(lua_State* L)
{
	int top = lua_gettop(L);
	if (8 == top)
	{
		int gateway = (int)luaL_checkinteger(L, 1);
		int payid = (int)luaL_checkinteger(L, 2);
		int product = (int)luaL_checkinteger(L, 3);
		int price = (int)luaL_checkinteger(L, 4);
		int count = (int)luaL_checkinteger(L, 5);
		std::string invoice = lua_check_string(L, 6);
		long timeout = (long)luaL_checkinteger(L, 7);
		limax::AuanyService::Result onresult = buildAuanyServiceResult(L, 8);
		limax::AuanyService::pay(gateway, payid, product, price, count, invoice, timeout, onresult);
	}
	else
	{
		lua_error(L);
	}
	return 0;
}

extern "C" LIMAX_LUA_API int luaopen_limaxcontext(lua_State* L)
{
	lua_newtable(L);

	lua_pushcfunction(L, lua_open_engine);
	lua_setfield(L, -2, "openEngine");

	lua_pushcfunction(L, lua_close_engine);
	lua_setfield(L, -2, "closeEngine");

	lua_pushcfunction(L, lua_idle_process);
	lua_setfield(L, -2, "idle");

	lua_pushcfunction(L, lua_idle_process_time);
	lua_setfield(L, -2, "idleTime");

	lua_pushcfunction(L, lua_idle_process_count);
	lua_setfield(L, -2, "idleCount");

	lua_pushcfunction(L, lua_create_context_and_start_endpoint);
	lua_setfield(L, -2, "start");

	lua_pushcfunction(L, lua_http_download);
	lua_setfield(L, -2, "httpdownload");

	lua_pushcfunction(L, lua_open_trace);
	lua_setfield(L, -2, "openTrace");

	lua_newtable(L);
	{
		lua_pushcfunction(L, lua_auservice_derive);
		lua_setfield(L, -2, "derive");

		lua_pushcfunction(L, lua_auservice_bind);
		lua_setfield(L, -2, "bind");

		lua_pushcfunction(L, lua_auservice_temporary);
		lua_setfield(L, -2, "temporary");

		lua_pushcfunction(L, lua_auservice_pay);
		lua_setfield(L, -2, "pay");
	}
	lua_setfield(L, -2, "auanyservice");

	table_append_oshelper(L);
	return 1;
}

