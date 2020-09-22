package org.limaxproject.demo.chatroom.android.client.sdk;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import limax.codec.CodecException;
import limax.endpoint.EndpointListener;
import limax.endpoint.EndpointManager;
import limax.endpoint.variant.TemporaryViewHandler;
import limax.endpoint.variant.VariantManager;
import limax.endpoint.variant.VariantView;
import limax.net.Config;
import limax.net.Manager;
import limax.net.SizePolicyException;
import limax.net.Transport;
import limax.util.Trace;

class LimaxClientNotify implements EndpointListener {

	private final MainActivity activity;

	private EndpointManager epmanager;
	private VariantManager varmanager;
	private VariantView chatroomview;

	LimaxClientNotify(MainActivity activity) {
		this.activity = activity;
	}

	@Override
	public void onSocketConnected() {
		if(Trace.isInfoEnabled())
			Trace.info("onSocketConnected");
	}

	@Override
	public void onKeyExchangeDone() {
		if(Trace.isInfoEnabled())
			Trace.info("onKeyExchangeDone");
	}

	@Override
	public void onKeepAlived(int ms) {
		if(Trace.isInfoEnabled())
			Trace.info("onKeepAlived " + ms);
	}

	@Override
	public void onErrorOccured(int source, int code, Throwable exception) {
		activity.showMessage("onErrorOccured " + source + " " + code + " " + exception);
	}

	@Override
	public void onAbort(Transport transport) throws Exception {
		if(Trace.isInfoEnabled())
			Trace.info("onAbort " + transport);
		activity.connectAbort();
	}

	@Override
	public void onManagerInitialized(Manager manager, Config config) {
		if(Trace.isInfoEnabled())
			Trace.info("onManagerInitialized");
		epmanager = (EndpointManager)manager;
	}

	@Override
	public void onManagerUninitialized(Manager manager) {
		if(Trace.isInfoEnabled())
			Trace.info("onManagerUninitialized");
		epmanager = null;
		activity.showLoginFrame();
	}

	@Override
	public void onTransportAdded(Transport transport) throws Exception {
		if(Trace.isInfoEnabled())
			Trace.info("onTransportAdded");
		varmanager = VariantManager.getInstance(epmanager, MainActivity.pvid_chat);
		activity.showMainFrame();

		varmanager.setTemporaryViewHandler("chatviews.ChatRoom", new TemporaryViewHandler() {
			@Override
			public void onOpen(VariantView view, Collection<Long> sessionids) {
				chatroomview = view;
				if(Trace.isInfoEnabled())
					Trace.info("chatviews.ChatRoom onOpen" + view + " " + sessionids);
				activity.showChatRoom();
			}

			@Override
			public void onDetach(VariantView view, long sessionid, int reason) {
				if(Trace.isInfoEnabled())
					Trace.info("chatviews.ChatRoom onDetach" + view + " " + sessionid + " " + reason);
				activity.onChatRoomDetach(sessionid, reason);
			}

			@Override
			public void onClose(VariantView view) {
				if(Trace.isInfoEnabled())
					Trace.info("chatviews.ChatRoom onClose" + view);
				chatroomview = null;
				activity.showMainFrame();
			}

			@Override
			public void onAttach(VariantView view, long sessionid) {
				if(Trace.isInfoEnabled())
					Trace.info("chatviews.ChatRoom onAttach" + view + " " + sessionid);
				activity.onChatRoomAttach(sessionid);
			}
		});
	}

	@Override
	public void onTransportRemoved(Transport transport) throws Exception {
		if(Trace.isInfoEnabled())
			Trace.info("onTransportRemoved " + transport);
		varmanager = null;
	}

	public VariantManager getVariantManager() {
		return varmanager;
	}

	public VariantView getChatRoomView() {
		return chatroomview;
	}

	public long getSessionId(){
		return epmanager.getSessionId();
	}

	void sendMessage(VariantView view, String msg) {
		try {
			view.sendMessage(msg);
		} catch (Exception e) {
			if(Trace.isWarnEnabled())
				Trace.warn("send message " + view + " " + msg, e);
		}
	}

	void closeLogin() {
		epmanager.close();
	}




}
