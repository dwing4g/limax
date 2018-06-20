#include <jni.h>
#include <sstream>
#include <android/log.h>
#include "org_limax_android_chatclient_ndk_LimaxInterface.h"
#include "../../../../cpp/limax/include/limax.h"
#include "rapidjson/rapidjson.h"
#include "rapidjson/writer.h"

/////////////////////////////////////////////////////////////////////////////////////////

static inline std::string jstringTostring(JNIEnv* env, jstring jstr) {
	std::string result;
	const char* text = env->GetStringUTFChars(jstr, nullptr);
	if (nullptr != text)
		result.assign(text);
	env->ReleaseStringUTFChars(jstr, text);
	return result;
}

static inline jstring stringtojstring(JNIEnv* env, const std::string& str) {
	return env->NewStringUTF(str.c_str());
}

static inline void outputDebugInfo(const char* info, ...) {
	va_list vl;
	va_start(vl, info);
	__android_log_vprint(ANDROID_LOG_DEBUG, "ndkimpl", info, vl);
	va_end(vl);
}

///////////////////////////////////////////////////////////////////////////////////////////

static void outputvariantvalue(
		rapidjson::Writer<rapidjson::StringBuffer>& writer, const char* type,
		std::function<void()> f) {
	writer.StartObject();
	writer.Key("type");
	writer.String(type);
	writer.Key("value");
	f();
	writer.EndObject();
}

static void outputvariant(const limax::Variant& v,
		rapidjson::Writer<rapidjson::StringBuffer>& writer) {
	switch (v.getVariantType()) {
	case limax::VariantType::Null:
		outputvariantvalue(writer, "null", [&]() {writer.Null();});
		break;
	case limax::VariantType::Boolean:
		outputvariantvalue(writer, "bool",
				[&]() {writer.Bool(v.getBooleanValue());});
		break;
	case limax::VariantType::Byte:
		outputvariantvalue(writer, "byte",
				[&]() {writer.Int(v.getByteValue());});
		break;
	case limax::VariantType::Short:
		outputvariantvalue(writer, "short",
				[&]() {writer.Int(v.getShortValue());});
		break;
	case limax::VariantType::Int:
		outputvariantvalue(writer, "int", [&]() {writer.Int(v.getIntValue());});
		break;
	case limax::VariantType::Long:
		outputvariantvalue(writer, "long",
				[&]() {writer.Int64(v.getLongValue());});
		break;
	case limax::VariantType::Float:
		outputvariantvalue(writer, "float",
				[&]() {writer.Double(v.getFloatValue());});
		break;
	case limax::VariantType::Double:
		outputvariantvalue(writer, "double",
				[&]() {writer.Double(v.getDoubleValue());});
		break;
	case limax::VariantType::String: {
		const auto& s = v.getStringValue();
		outputvariantvalue(writer, "string",
				[&]() {writer.String(s.c_str(), s.length());});
		break;
	}
	case limax::VariantType::Binary:
		throw "binary";
	case limax::VariantType::List: {
		const auto& c = v.getListValue();
		outputvariantvalue(writer, "list", [&]()
		{
			writer.StartArray();
			for (const auto& e : c)
			outputvariant(e, writer);
			writer.EndArray();
		});
		break;
	}
	case limax::VariantType::Vector: {
		const auto& c = v.getVectorValue();
		outputvariantvalue(writer, "vector", [&]()
		{
			writer.StartArray();
			for (const auto& e : c)
			outputvariant(e, writer);
			writer.EndArray();
		});
		break;
	}
	case limax::VariantType::Set: {
		const auto& c = v.getSetValue();
		outputvariantvalue(writer, "set", [&]()
		{
			writer.StartArray();
			for (const auto& e : c)
			outputvariant(e, writer);
			writer.EndArray();
		});
		break;
	}
	case limax::VariantType::Map: {
		const auto& m = v.getMapValue();
		outputvariantvalue(writer, "map", [&]()
		{
			writer.StartArray();
			for (const auto& e : m)
			{
				writer.StartObject();
				writer.Key("key");
				outputvariant(e.first, writer);
				writer.Key("value");
				outputvariant(e.second, writer);
				writer.EndObject();
			}
			writer.EndArray();
		});
		break;
	}
	case limax::VariantType::Struct: {
		outputvariantvalue(writer, "struct",
				[&]()
				{
					writer.StartObject();
					auto sd = std::dynamic_pointer_cast<limax::StructDeclaration>(v.makeDeclaration());
					auto vs = sd->getVariables();
					for (const auto& d : vs) {
						const auto& k = d->getName();
						writer.Key(k.c_str(), k.length());
						outputvariant(v.getVariant(k), writer);
					}
					writer.EndObject();
				});
		break;
	}
	case limax::VariantType::Object:
		throw "object";
	default:
		throw "default";
	}
}

static std::string varianttojsonstring(const limax::Variant& v) {
	rapidjson::StringBuffer buffer;
	rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);
	outputvariant(v, writer);
	return std::string(buffer.GetString(), buffer.GetSize());
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////

enum {
	pvid_chat = 100,
};

const std::string chatroomview = "chatviews.ChatRoom";

class ChatClientListener: public limax::EndpointListener {

	limax::EndpointManager* m_manager = nullptr;
	JNIEnv* m_currentenv = nullptr;
	jobject m_currentobj = nullptr;
	limax::VariantView* m_chatroom = nullptr;
public:
	ChatClientListener() {
	}
	virtual ~ChatClientListener() {
	}
public:
	virtual void onManagerInitialized(limax::EndpointManager* manager,
			limax::EndpointConfig*) override {
		m_manager = manager;
		notifyString("onManagerInitialized");
	}
	virtual void onTransportAdded(limax::Transport*) override;
	virtual void onTransportRemoved(limax::Transport*) override {
		notifyString("onTransportRemoved");
	}
	virtual void onAbort(limax::Transport*) override {
		notifyString("onAbort");
	}
	virtual void onManagerUninitialized(limax::EndpointManager*) override {
		notifyString("onManagerUninitialized");
		m_manager = nullptr;
	}
	virtual void onSocketConnected() override {
		notifyString("onSocketConnected");
	}
	virtual void onKeyExchangeDone() override {
		notifyString("onKeyExchangeDone");
	}
	virtual void onKeepAlived(int ping) override {
		notifyString("onKeepAlived", std::to_string(ping));
	}
	virtual void onErrorOccured(int errorsource, int errorvalue,
			const std::string& info) override {
		std::stringstream ss;
		ss << "errorsource = " << errorsource << " errorvalue = "
				<< errorvalue + " info = " << info;
		notifyString("onErrorOccured", ss.str());
	}
	virtual void destroy() override {
	}
private:
	limax::VariantView* getView(const std::string& name) {
		if (chatroomview == name)
			return m_chatroom;
		auto mng = limax::VariantManager::getInstance(m_manager, pvid_chat);
		return mng->getSessionOrGlobalView(name);
	}

	limax::VariantViewChangedListener getVariantViewChangedListener() {
		return [&]( const limax::VariantViewChangedEvent& e) {

			const auto& _view = e.getView()->getViewName();
			const auto& _field = e.getFieldName();
			auto sessionid = e.getSessionId();
			auto type = (int)e.getType();
			auto _value = varianttojsonstring( e.getValue());

			outputDebugInfo( "VariantViewChangedListener %s %s", _view.c_str(), _field.c_str());

			auto jcls = m_currentenv->GetObjectClass(m_currentobj);
			auto mid = m_currentenv->GetMethodID(jcls, "onNotify",
					"(Ljava/lang/String;Ljava/lang/String;JILjava/lang/String;)V");
			auto view = stringtojstring(m_currentenv, _view);
			auto field = stringtojstring(m_currentenv, _field);
			auto value = stringtojstring(m_currentenv, _value);
			m_currentenv->CallVoidMethod(m_currentobj, mid, view, field, sessionid, type, value);
		};
	}
public:
	void notifyString(const std::string& _msg, const std::string& _info = "") {
		outputDebugInfo("notifyString %s %s", _msg.c_str(), _info.c_str());

		outputDebugInfo("notifyString %s %s", _msg.c_str(), _info.c_str());
		auto jcls = m_currentenv->GetObjectClass(m_currentobj);
		auto mid = m_currentenv->GetMethodID(jcls, "onStatus",
				"(Ljava/lang/String;Ljava/lang/String;)V");
		auto msg = stringtojstring(m_currentenv, _msg);
		auto info = stringtojstring(m_currentenv, _info);
		m_currentenv->CallVoidMethod(m_currentobj, mid, msg, info);
	}

	void setChatRoomViewInstance(limax::VariantView* view) {
		m_chatroom = view;
		if (nullptr != m_chatroom)
			m_chatroom->registerListener(getVariantViewChangedListener());
	}
public:
	void tryCloseManager() {
		if (nullptr != m_manager) {
			outputDebugInfo("tryCloseManager close start");
			m_manager->close();

			while (nullptr != m_manager) {
				limax::uiThreadSchedule();
				std::this_thread::sleep_for(std::chrono::milliseconds(1));
			}
			outputDebugInfo("tryCloseManager close done");
		}
	}

	void setCurrentEnvAndNotify(JNIEnv* env, jobject jobj) {
		m_currentenv = env;
		m_currentobj = jobj;
	}

	void sendMessage(const std::string& view, const std::string& msg) {
		auto v = getView(view);
		v->sendMessage(msg);
	}

	limax::Variant getFieldValue(const std::string& view,
			const std::string& field) {
		auto v = getView(view);
		limax::Variant var;
		v->visitField(field, [&var]( const limax::Variant& v) {var = v;});
		return var;
	}
	int64_t getSessionId() {
		return m_manager->getSessionId();
	}
} g_listener;

struct CurrentEnvAndNotifyScoped {
	CurrentEnvAndNotifyScoped(JNIEnv* env, jobject jobj) {
		g_listener.setCurrentEnvAndNotify(env, jobj);
	}
	~CurrentEnvAndNotifyScoped() {
		g_listener.setCurrentEnvAndNotify(nullptr, nullptr);
	}
};

static class ChatRoomViewHandler: public limax::TemporaryViewHandler {
public:
	ChatRoomViewHandler() {
	}
	virtual ~ChatRoomViewHandler() {
	}
public:
	virtual void onOpen(limax::VariantView* view,
			const std::vector<int64_t>& sessionids) override {
		g_listener.setChatRoomViewInstance(view);
		g_listener.notifyString("onChatRoomOpen");
	}
	virtual void onClose(limax::VariantView* view) override {
		outputDebugInfo("onChatRoomClose");
		g_listener.notifyString("onChatRoomClose");
		g_listener.setChatRoomViewInstance(nullptr);
	}
	virtual void onAttach(limax::VariantView* view, int64_t sessionid)
			override {
		g_listener.notifyString("onChatRoomAttach", std::to_string(sessionid));
	}
	virtual void onDetach(limax::VariantView* view, int64_t sessionid,
			int reason) override {
		std::stringstream ss;
		ss << sessionid << "," << reason;
		g_listener.notifyString("onChatRoomDetach", ss.str());
	}
	virtual void destroy() override {
	}
} g_chatroomhandler;

void ChatClientListener::onTransportAdded(limax::Transport*) {
	notifyString("onTransportAdded");

	auto listener = getVariantViewChangedListener();
	auto mng = limax::VariantManager::getInstance(m_manager, pvid_chat);
	mng->getSessionOrGlobalView("chatviews.CommonInfo")->registerListener(
			listener);
	mng->getSessionOrGlobalView("chatviews.UserInfo")->registerListener(
			listener);
	mng->setTemporaryViewHandler(chatroomview, &g_chatroomhandler);
}

//////////////////////////////////////////////////////////////////////////////////////////////////

void Java_org_limax_android_chatclient_ndk_LimaxInterface_initializeLimaxLib(
		JNIEnv *, jclass) {
}

void Java_org_limax_android_chatclient_ndk_LimaxInterface_startLogin(
		JNIEnv* env, jclass, jstring _username, jstring _token,
		jstring _platflag, jstring _serverip, jint port) {
	limax::Endpoint::openEngine();
	auto username = jstringTostring(env, _username);
	auto token = jstringTostring(env, _token);
	auto platflag = jstringTostring(env, _platflag);
	auto serverip = jstringTostring(env, _serverip);
	auto config =
			limax::Endpoint::createEndpointConfigBuilder(serverip, port,
					limax::LoginConfig::plainLogin(username, token, platflag))->variantProviderIds(
					{ pvid_chat })->executor(limax::runOnUiThread)->build();
	limax::Endpoint::start(config, &g_listener);
}

void Java_org_limax_android_chatclient_ndk_LimaxInterface_closeLogin(
		JNIEnv* env, jclass, jobject jobj) {

	CurrentEnvAndNotifyScoped scoped(env, jobj);

	g_listener.tryCloseManager();

	volatile bool done = false;
	limax::Endpoint::closeEngine([&done]() {done = true;});
	while (!done) {
		limax::uiThreadSchedule();
		std::this_thread::sleep_for(std::chrono::milliseconds(1));
	}
}

void Java_org_limax_android_chatclient_ndk_LimaxInterface_sendMessage(
		JNIEnv* env, jclass, jstring _view, jstring _msg) {
	auto view = jstringTostring(env, _view);
	auto msg = jstringTostring(env, _msg);
	g_listener.sendMessage(view, msg);
	outputDebugInfo("sendMessage %s %s", view.c_str(), msg.c_str());
}

jstring Java_org_limax_android_chatclient_ndk_LimaxInterface_getFieldValue(
		JNIEnv* env, jclass, jstring _view, jstring _field) {
	auto view = jstringTostring(env, _view);
	auto field = jstringTostring(env, _field);
	outputDebugInfo("getFieldValue %s %s", view.c_str(), field.c_str());
	auto value = g_listener.getFieldValue(view, field);
	auto str = varianttojsonstring(value);
	outputDebugInfo("getFieldValue %s %s %s", view.c_str(), field.c_str(),
			str.c_str());
	return stringtojstring(env, str);
}

void Java_org_limax_android_chatclient_ndk_LimaxInterface_idleProcess(
		JNIEnv * env, jclass, jobject jobj) {
	CurrentEnvAndNotifyScoped scoped(env, jobj);
	limax::uiThreadSchedule();
}

jlong Java_org_limax_android_chatclient_ndk_LimaxInterface_getSessionId(
		JNIEnv *, jclass) {
	return g_listener.getSessionId();
}
