package org.limax.android.chatclient;

import org.limax.android.chatclient.java.JavaEngine;
import org.limax.android.chatclient.ndk.NdkEngine;

public class LimaxEngineManager {

	private static LimaxEngine current = null;

	public static LimaxEngine createJavaEngin() {
		current = new JavaEngine(LimaxEngineNotifyManager.getInstance());
		return current;
	}

	public static LimaxEngine createNdkEngin() {
		current = new NdkEngine(LimaxEngineNotifyManager.getInstance());
		return current;
	}

	public static LimaxEngine getCurrentEngine() {
		return current;
	}
}
