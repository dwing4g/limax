package org.limax.android.chatclient.ndk;

public class LimaxInterface {

	public static interface DataNotify {
		void onNotify(String view, String field, long sessionid, int type,
				String value);

		void onStatus(String status, String param);
	}

	public static native void initializeLimaxLib();

	public static native void startLogin(String username, String token,
			String platflag, String serverip, int port);

	public static native void closeLogin(DataNotify notiy);

	public static native void sendMessage(String view, String cmd);

	public static native String getFieldValue(String view, String field);

	public static native void idleProcess(DataNotify notiy);

	public static native long getSessionId();

	static {
		System.loadLibrary("ndkimpl");
	}

}
