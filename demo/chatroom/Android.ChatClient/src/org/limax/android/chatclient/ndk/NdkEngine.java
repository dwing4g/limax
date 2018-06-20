package org.limax.android.chatclient.ndk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import limax.endpoint.ViewChangedType;
import limax.endpoint.variant.Variant;

import org.limax.android.chatclient.LimaxEngine;
import org.limax.android.chatclient.LimaxEngineNotify;
import org.limax.android.chatclient.LimaxFieldArgs;
import org.limax.android.chatclient.LimaxFieldNotify;

public class NdkEngine implements LimaxEngine {

	static {
		
	}

	private Executor executor = null;
	volatile private Runnable IdleTask = null;
	final LimaxEngineNotify engineNotify;
	private final LimaxInterface.DataNotify datanofity;

	public NdkEngine(LimaxEngineNotify notify) {
		this.engineNotify = notify;
		datanofity = new LimaxInterface.DataNotify() {
			@Override
			public void onStatus(String status, String param) {
				engineNotify.onNotify(status, param);
			}

			@Override
			public void onNotify(final String view, final String field,
					final long sessionid, final int type, String value) {
				final StringPairKey key = new StringPairKey(view, field);
				Collection<LimaxFieldNotify> cn = notifymap.get(key);
				if (null == cn)
					return;
				final Variant var = Helper.variantFromJSonString(value);
				final LimaxFieldArgs args = new LimaxFieldArgs() {
					@Override
					public String getView() {
						return view;
					}

					@Override
					public Variant getValue() {
						return var;
					}

					@Override
					public ViewChangedType getType() {
						return ViewChangedType.values()[type];
					}

					@Override
					public long getSessionId() {
						return sessionid;
					}

					@Override
					public String getFieldName() {
						return field;
					}

					@Override
					public String toString() {
						return "LimaxFieldArgs view  = " + view + " field = "
								+ getFieldName() + " sessionid = "
								+ getSessionId() + " type = " + getType();
					}

				};
				for (LimaxFieldNotify n : new ArrayList<LimaxFieldNotify>(cn))
					n.onFieldNotify(args);
			}
		};
	}

	@Override
	public void startLogin(String username, String token, String platflag,
			String serverip, int port, Executor executor) {
		this.executor = executor;
		LimaxInterface.startLogin(username, token, platflag, serverip, port);

		final Object notify = new Object();
		IdleTask = new Runnable() {
			@Override
			public void run() {
				LimaxInterface.idleProcess(datanofity);
				synchronized (notify) {
					notify.notify();
				}
			}
		};

		new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					final Runnable task = IdleTask;
					if (null == task)
						break;
					synchronized (notify) {
						NdkEngine.this.executor.execute(task);
						try {
							notify.wait();
						} catch (InterruptedException e) {
							return;
						}
					}
				}

			}
		}).start();
	}

	@Override
	public void closeLogin() {
		LimaxInterface.closeLogin(datanofity);
		IdleTask = null;
	}

	@Override
	public void sendMessage(String view, String cmd) {
		LimaxInterface.sendMessage(view, cmd);
	}

	@Override
	public Variant getFieldValue(String view, String field) {
		final String jstr = LimaxInterface.getFieldValue(view, field);
		return Helper.variantFromJSonString(jstr);
	}

	@Override
	public Runnable registerNotify(String view, String field,
			final LimaxFieldNotify notify) {
		final StringPairKey key = new StringPairKey(view, field);
		Collection<LimaxFieldNotify> cn = notifymap.get(key);
		if (null == cn) {
			cn = new ArrayList<LimaxFieldNotify>();
			notifymap.put(key, cn);
		}
		final Collection<LimaxFieldNotify> ns = cn;
		ns.add(notify);
		return new Runnable() {
			@Override
			public void run() {
				ns.remove(notify);
			}
		};
	}

	@Override
	public long getSessionId() {
		return LimaxInterface.getSessionId();
	}

	private static class StringPairKey {

		final String view;
		final String field;

		public StringPairKey(String view, String field) {
			this.view = view;
			this.field = field;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof StringPairKey) {
				StringPairKey s = (StringPairKey) o;
				return view.equals(s.view) && field.endsWith(s.field);
			}
			return super.equals(o);
		}

		@Override
		public int hashCode() {
			return view.hashCode() | field.hashCode();
		}

	}

	private final Map<StringPairKey, Collection<LimaxFieldNotify>> notifymap = new HashMap<StringPairKey, Collection<LimaxFieldNotify>>();

}
