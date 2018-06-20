package org.limax.android.chatclient.java;

import java.util.Collection;
import java.util.concurrent.Executor;

import limax.endpoint.Endpoint;
import limax.endpoint.EndpointListener;
import limax.endpoint.EndpointManager;
import limax.endpoint.LoginConfig;
import limax.endpoint.ViewChangedType;
import limax.endpoint.ViewVisitor;
import limax.endpoint.variant.TemporaryViewHandler;
import limax.endpoint.variant.Variant;
import limax.endpoint.variant.VariantManager;
import limax.endpoint.variant.VariantView;
import limax.endpoint.variant.VariantViewChangedEvent;
import limax.endpoint.variant.VariantViewChangedListener;
import limax.net.Config;
import limax.net.Manager;
import limax.net.Transport;
import limax.util.Trace;

import org.limax.android.chatclient.LimaxEngine;
import org.limax.android.chatclient.LimaxEngineNotify;
import org.limax.android.chatclient.LimaxFieldArgs;
import org.limax.android.chatclient.LimaxFieldNotify;

public class JavaEngine implements LimaxEngine {

	public static final short pvid_chat = 100;
	private static final String chatRoomViewName = "chatviews.ChatRoom";

	private final LimaxEngineNotify engineNotify;
	private volatile EndpointManager manager = null;
	private volatile VariantView chatroomview = null;

	public JavaEngine(LimaxEngineNotify notify) {
		engineNotify = notify;
		try {
			Endpoint.openEngine();
		} catch (Exception e) {
			Trace.error("Endpoint.openEngine", e);
		}
	}

	private static void doCloseEngine() {
		Endpoint.closeEngine(new Runnable() {
			@Override
			public void run() {
				if (Trace.isInfoEnabled())
					Trace.info("close Engine done!");
			}
		});

	}

	@Override
	public void startLogin(String username, String token, String platflag, String serverip, int port,
			Executor executor) {

		Endpoint.start(
				Endpoint.createEndpointConfigBuilder(serverip, port, LoginConfig.plainLogin(username, token, platflag))
						.executor(executor).variantProviderIds(pvid_chat).build(),
				new EndpointListener() {

					@Override
					public void onTransportRemoved(Transport transport) throws Exception {
						engineNotify.onNotify("onTransportRemoved", transport.toString());
					}

					@Override
					public void onTransportAdded(Transport transport) throws Exception {

						final VariantManager vm = VariantManager.getInstance(manager, pvid_chat);
						vm.setTemporaryViewHandler("chatviews.ChatRoom", new TemporaryViewHandler() {

							@Override
							public void onOpen(VariantView view, Collection<Long> sessionids) {
								chatroomview = view;
								engineNotify.onNotify("onChatRoomOpen", "");
							}

							@Override
							public void onDetach(VariantView view, long sessionid, int reason) {
								engineNotify.onNotify("onChatRoomDetach", "" + sessionid + "," + reason);
							}

							@Override
							public void onClose(VariantView view) {
								engineNotify.onNotify("onChatRoomClose", "");
								chatroomview = null;
							}

							@Override
							public void onAttach(VariantView arg0, long sessionid) {
								engineNotify.onNotify("onChatRoomAttach", Long.toString(sessionid));
							}
						});

						engineNotify.onNotify("onTransportAdded", transport.toString());
					}

					@Override
					public void onManagerUninitialized(Manager manager) {
						engineNotify.onNotify("onManagerUninitialized", manager.toString());
						JavaEngine.this.manager = null;
						doCloseEngine();
					}

					@Override
					public void onManagerInitialized(Manager manager, Config cfg) {
						JavaEngine.this.manager = (EndpointManager) manager;
						engineNotify.onNotify("onManagerInitialized", manager.toString());
					}

					@Override
					public void onAbort(Transport transport) throws Exception {
						engineNotify.onNotify("onAbort", transport.toString());
					}

					@Override
					public void onSocketConnected() {
						engineNotify.onNotify("onSocketConnected", "");
					}

					@Override
					public void onKeyExchangeDone() {
						engineNotify.onNotify("onKeyExchangeDone", "");
					}

					@Override
					public void onKeepAlived(int time) {
						engineNotify.onNotify("onKeepAlived", Integer.toString(time));
					}

					@Override
					public void onErrorOccured(int source, int code, Throwable e) {
						String info = "source = " + source + " code = " + code + " exception = " + e.toString();
						if (Trace.isInfoEnabled())
							Trace.info("onErrorOccured info = " + info, e);
						engineNotify.onNotify("onErrorOccured", info);
					}
				});
	}

	@Override
	public void closeLogin() {
		EndpointManager mng = manager;
		if (null != mng)
			mng.close();
		else
			doCloseEngine();
	}

	private VariantView getVariantViewByName(String viewname) {
		if (viewname.equals(chatRoomViewName))
			return chatroomview;
		final VariantManager vm = VariantManager.getInstance(manager, pvid_chat);
		return vm.getSessionOrGlobalView(viewname);
	}

	@Override
	public void sendMessage(String view, String cmd) {
		final VariantView vv = getVariantViewByName(view);
		if (null != vv) {
			try {
				vv.sendMessage(cmd);
			} catch (Exception e) {
				Trace.error("view " + view + " sendMessage", e);
			}
		} else {
			Trace.error("view " + view + " unexist");
		}
	}

	private static class VariantVisitor implements ViewVisitor<Variant> {
		Variant value = Variant.Null;

		@Override
		public void accept(Variant v) {
			value = v;
		}
	}

	@Override
	public Variant getFieldValue(String view, String field) {
		final VariantView vv = getVariantViewByName(view);
		if (null == vv)
			return Variant.Null;
		VariantVisitor value = new VariantVisitor();
		vv.visitField(field, value);
		return value.value;
	}

	@Override
	public Runnable registerNotify(final String view, String field, final LimaxFieldNotify notify) {
		final VariantView vv = getVariantViewByName(view);
		if (null == vv)
			throw new RuntimeException("unfound view " + view);
		return vv.registerListener(field, new VariantViewChangedListener() {
			@Override
			public void onViewChanged(final VariantViewChangedEvent event) {
				notify.onFieldNotify(new LimaxFieldArgs() {

					@Override
					public String getView() {
						return view;
					}

					@Override
					public Variant getValue() {
						return event.getValue();
					}

					@Override
					public ViewChangedType getType() {
						return event.getType();
					}

					@Override
					public long getSessionId() {
						return event.getSessionId();
					}

					@Override
					public String getFieldName() {
						return event.getFieldName();
					}

					@Override
					public String toString() {
						return "LimaxFieldArgs view  = " + view + " field = " + getFieldName() + " sessionid = "
								+ getSessionId() + " type = " + getType();
					}
				});
			}
		});
	}

	@Override
	public long getSessionId() {
		return manager.getSessionId();
	}

}
