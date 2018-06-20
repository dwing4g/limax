package org.limax.android.chatclient;

import java.util.concurrent.Executor;

import limax.endpoint.variant.Variant;

public interface LimaxEngine {

	void startLogin(String username, String token, String platflag,
			String serverip, int port, Executor executor);

	void closeLogin();

	void sendMessage(String view, String cmd);

	Variant getFieldValue(String view, String field);

	Runnable registerNotify(String view, String field, LimaxFieldNotify notify);

	long getSessionId();
	
}
