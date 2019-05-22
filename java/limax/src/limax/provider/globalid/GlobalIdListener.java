package limax.provider.globalid;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import limax.net.ClientListener;
import limax.net.Config;
import limax.net.Engine;
import limax.net.Manager;
import limax.net.State;
import limax.net.StateTransport;
import limax.net.Transport;
import limax.provider.GlobalId;
import limax.provider.providerglobalid.EndorseNames;
import limax.provider.providerglobalid.RequestId;
import limax.provider.providerglobalid.RequestName;
import limax.provider.states.GlobalIdClient;
import limax.providerglobalid.Group;
import limax.providerglobalid.GroupName;
import limax.providerglobalid.NameRequest;
import limax.providerglobalid.NameResponse;
import limax.providerglobalid.NamesEndorse;
import limax.util.Trace;
import limax.zdb.Transaction;
import limax.zdb.XDeadlock;

public final class GlobalIdListener implements ClientListener {
	private Future<?> keepAliveFuture;
	private static final GlobalIdListener instance = new GlobalIdListener();

	public static GlobalIdListener getInstance() {
		return instance;
	}

	private volatile Transport transport;
	private final Queue<Runnable> queue = new ArrayDeque<>();

	private GlobalIdListener() {
	}

	private static Transport getTransport() {
		Transport t = instance.transport;
		if (t != null)
			return t;
		throw new GlobalId.Exception("NO_GLOBALID_SERVICE");
	}

	private static void flushTransport() {
		Transport t = instance.transport;
		instance.transport = null;
		if (t != null)
			((StateTransport) t).resetAlarm(0);
	}

	private void _runOnValidation(Runnable r) {
		synchronized (queue) {
			if (transport == null)
				queue.add(r);
			else
				Engine.getApplicationExecutor().execute(r);
		}
	}

	public static void runOnValidation(Runnable r) {
		instance._runOnValidation(r);
	}

	@Override
	public void onManagerInitialized(Manager manager, Config config) {
		long keepAliveTimeout = Engine.getIntranetKeepAliveTimeout();
		if (keepAliveTimeout > 0)
			keepAliveFuture = Engine.getProtocolScheduler().scheduleWithFixedDelay(() -> {
				try {
					new limax.provider.providerglobalid.KeepAlive(keepAliveTimeout).send(transport);
				} catch (Exception e) {
				}
			}, 0, keepAliveTimeout / 2, TimeUnit.MILLISECONDS);
	}

	@Override
	public void onManagerUninitialized(Manager manager) {
		if (keepAliveFuture != null)
			keepAliveFuture.cancel(true);
	}

	@Override
	public void onTransportAdded(Transport transport) {
		if (Trace.isInfoEnabled())
			Trace.info("GlobalIdClientManager onConnected " + transport);
		synchronized (queue) {
			this.transport = transport;
			for (Runnable r; (r = queue.poll()) != null;)
				Engine.getApplicationExecutor().execute(r);
		}
	}

	@Override
	public void onTransportRemoved(Transport transport) {
		if (Trace.isErrorEnabled())
			Trace.error("GlobalIdClientManager onDisconnect");
		this.transport = null;
	}

	@Override
	public void onAbort(Transport transport) {
		if (Trace.isErrorEnabled())
			Trace.error("GlobalIdClientManager onConnectAbort");
	}

	public static Long requestId(String group) {
		try {
			return new RequestId(new Group(group)) {
				public long getTimeout() {
					return GlobalId.getTimeout();
				}
			}.submit(getTransport()).get().val;
		} catch (GlobalId.Exception t) {
			throw t;
		} catch (Throwable t) {
			flushTransport();
			throw new GlobalId.Exception(t);
		}
	}

	private static class EndorseTask {
		private final Map<GroupName, Long> idmap = new HashMap<>();
		private int tid = 0;

		boolean execute(GroupName gn, int type) throws Exception {
			Long serial = idmap.get(gn);
			NameResponse response;
			try {
				response = new RequestName(serial == null ? new NameRequest(gn, type, tid)
						: new NameRequest(new GroupName("", ""), -type, serial)) {
					public long getTimeout() {
						return GlobalId.getTimeout();
					}
				}.submit(getTransport()).get();
			} catch (ExecutionException e) {
				flushTransport();
				throw e;
			}
			if (response.status == NameResponse.DEADLOCK)
				throw new XDeadlock();
			idmap.put(gn, response.serial);
			tid = (int) response.serial;
			return response.status == NameResponse.OK;
		}

		int tid() {
			return tid;
		}
	}

	private static ThreadLocal<EndorseTask> current = new ThreadLocal<EndorseTask>();

	private static EndorseTask currentEndorseTask() {
		EndorseTask r = current.get();
		if (r == null) {
			current.set(r = new EndorseTask());
			Transaction.addSavepointTask(() -> endorse(NamesEndorse.COMMIT), () -> endorse(NamesEndorse.ROLLBACK));
		}
		return r;
	}

	public static boolean create(GroupName gn) {
		try {
			return currentEndorseTask().execute(gn, NameRequest.CREATE);
		} catch (GlobalId.Exception t) {
			throw t;
		} catch (Exception t) {
			throw new GlobalId.Exception(t);
		}
	}

	public static boolean delete(GroupName gn) {
		try {
			return currentEndorseTask().execute(gn, NameRequest.DELETE);
		} catch (GlobalId.Exception t) {
			throw t;
		} catch (Exception t) {
			throw new GlobalId.Exception(t);
		}
	}

	public static boolean exist(GroupName gn) {
		try {
			return currentEndorseTask().execute(gn, NameRequest.TEST);
		} catch (GlobalId.Exception t) {
			throw t;
		} catch (Exception t) {
			throw new GlobalId.Exception(t);
		}
	}

	private static void endorse(int type) {
		try {
			EndorseTask task = current.get();
			if (task == null)
				return;
			int tid = task.tid();
			if (tid != 0)
				new EndorseNames(new NamesEndorse(type, tid)) {
					public long getTimeout() {
						return GlobalId.getTimeout();
					}
				}.submit(getTransport()).get();
		} catch (GlobalId.Exception t) {
			throw t;
		} catch (Exception t) {
			flushTransport();
			if (type == NamesEndorse.COMMIT)
				throw new GlobalId.Exception(t);
		} finally {
			current.set(null);
		}
	}

	public State getDefaultState() {
		return GlobalIdClient.getDefaultState();
	}
}
