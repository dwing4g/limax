package org.limax.android.chatclient;

import java.util.HashMap;
import java.util.Map;

import limax.util.Trace;

class LimaxEngineNotifyManager implements LimaxEngineNotify {

	private static final LimaxEngineNotifyManager instance = new LimaxEngineNotifyManager();

	public static LimaxEngineNotifyManager getInstance() {
		return instance;
	}

	private LoginActivity loginAcrivity = null;

	private static interface Notify {
		void onNotify(String info);
	}

	private Map<String, Notify> notifies = new HashMap<String, Notify>();

	private LimaxEngineNotifyManager() {
		notifies.put("onTransportAdded", new Notify() {
			@Override
			public void onNotify(String info) {
				loginAcrivity.showMainFrame();
			}
		});
		notifies.put("onManagerUninitialized", new Notify() {
			@Override
			public void onNotify(String info) {
				loginAcrivity.showLoginFrame();
			}
		});

		notifies.put("onAbort", new Notify() {
			@Override
			public void onNotify(String info) {
				loginAcrivity.connectAbort();
			}
		});

		notifies.put("onErrorOccured", new Notify() {
			@Override
			public void onNotify(String info) {
				loginAcrivity.showMessage(info);
			}
		});

		notifies.put("onChatRoomOpen", new Notify() {
			@Override
			public void onNotify(String info) {
				loginAcrivity.showChatRoom();
			}
		});
		notifies.put("onChatRoomClose", new Notify() {
			@Override
			public void onNotify(String info) {
				loginAcrivity.showMainFrame();
			}
		});
		notifies.put("onChatRoomAttach", new Notify() {
			@Override
			public void onNotify(String info) {
				loginAcrivity.onChatRoomAttach(Long.parseLong(info));
			}
		});
		notifies.put("onChatRoomDetach", new Notify() {
			@Override
			public void onNotify(String info) {
				String[] args = info.split(",");
				final long sessionid = Long.parseLong(args[0]);
				final int reason = Integer.parseInt(args[1]);
				loginAcrivity.onChatRoomDetach(sessionid, reason);
			}
		});

	}

	public void setLoginActivity(LoginActivity a) {
		loginAcrivity = a;
	}

	@Override
	public void onNotify(String msg, String info) {
		if (Trace.isInfoEnabled())
			Trace.info("LimaxEngineNotifyManager.onNotify msg = " + msg
					+ " info = " + info);
		Notify n = notifies.get(msg);
		if (n != null)
			n.onNotify(info);
	}

}
