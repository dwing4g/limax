package limax.net;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import limax.net.io.NetModel;
import limax.net.io.NetTask;
import limax.util.Dispatcher;
import limax.util.Dispatcher.Dispatchable;
import limax.util.Trace;

class ClientManagerImpl extends AbstractManager implements ClientManager {

	static {
		Engine.registerDriver(new Driver() {
			@Override
			public Class<? extends Config> getConfigClass() {
				return ClientManagerConfig.class;
			}

			@Override
			public Class<? extends Listener> getListenerClass() {
				return ClientListener.class;
			}

			@Override
			public Manager newInstance(Config config, Listener listener, Manager wrapper) throws Exception {
				return new ClientManagerImpl((ClientManagerConfig) config, (ClientListener) listener, wrapper);
			}
		});
	}

	private enum State {
		INIT, CONNECTING, EXCHANGE, CLOSE
	};

	private final ClientManagerConfig config;
	private final ClientListener listener;
	private final Manager wrapper;
	private final Dispatcher dispatcher;

	private volatile AbstractTransport transport = null;

	private final static int SHRINKTIME_MIN = 1;
	private final static int SHRINKTIME_MAX = 60 * 3;
	private int shrinktime = SHRINKTIME_MIN;
	private final ReentrantLock lock = new ReentrantLock();
	private final Condition cond = lock.newCondition();
	private boolean autoReconnect;
	private State state;
	private Future<?> future;

	private void doConnect() {
		try {
			StateTransportImpl transport = new StateTransportImpl(this);
			NetTask nettask = NetModel.createClientTask(config.getInputBufferSize(), config.getOutputBufferSize(), null,
					transport, config.isAsynchronous());
			nettask.resetAlarm(config.getConnectTimeout());
			state = State.CONNECTING;
			NetModel.addClient(config.getPeerAddress(), nettask);
		} catch (Throwable t) {
			if (Trace.isErrorEnabled())
				Trace.error(this + " doConnect", t);
			_close();
		}
	}

	private ClientManagerImpl(ClientManagerConfig config, final ClientListener listener, Manager wrapper)
			throws Exception {
		this.config = config;
		this.listener = listener;
		this.wrapper = wrapper;
		this.dispatcher = config.getDispatcher();
		Throwable t = dispatcher.run(new Dispatchable() {
			@Override
			public void run() {
				listener.onManagerInitialized(ClientManagerImpl.this, ClientManagerImpl.this.config);
			}
		});
		if (t != null)
			if (t instanceof Exception)
				throw (Exception) t;
			else
				throw new Exception(t);
		autoReconnect = config.isAutoReconnect();
		state = State.INIT;
		doConnect();
	}

	@Override
	public final void dispatch(Runnable r, Object hit) {
		dispatcher.execute(r, hit);
	}

	private boolean scheduleReconnect() {
		if (!autoReconnect)
			return false;
		state = State.INIT;
		future = Engine.getProtocolScheduler().scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				try {
					lock.lock();
					try {
						future.cancel(false);
						future = null;
						if (state == State.INIT)
							doConnect();
					} finally {
						lock.unlock();
					}
				} catch (Exception e) {
					if (Trace.isErrorEnabled())
						Trace.error("ClientManagerImpl.doReconnect", e);
				}
			}
		}, shrinktime, Long.MAX_VALUE, TimeUnit.SECONDS);
		shrinktime *= 2;
		if (shrinktime > SHRINKTIME_MAX)
			shrinktime = SHRINKTIME_MAX;
		return true;
	}

	void connectAbort(final StateTransport transport) {
		lock.lock();
		try {
			if (state != State.CONNECTING)
				return;
			Throwable t = dispatcher.run(new Dispatchable() {
				@Override
				public void run() throws Throwable {
					listener.onAbort(transport);
				}
			});
			if (t == null) {
				if (!scheduleReconnect())
					_close();
			} else {
				if (Trace.isErrorEnabled())
					Trace.error(this + " connectAbort", t);
				_close();
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	void addProtocolTransport(final AbstractTransport transport) {
		lock.lock();
		try {
			if (state != State.CONNECTING) {
				transport.close();
				return;
			}
			Throwable t = dispatcher.run(new Dispatchable() {
				@Override
				public void run() throws Throwable {
					listener.onTransportAdded(transport);
				}
			});
			if (t == null) {
				this.transport = transport;
				state = State.EXCHANGE;
				shrinktime = SHRINKTIME_MIN;
			} else {
				if (Trace.isErrorEnabled())
					Trace.error(this + " addProtocolTransport " + transport, t);
				transport.close();
				_close();
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	void removeProtocolTransport(final AbstractTransport transport) {
		lock.lock();
		try {
			if (state != State.EXCHANGE)
				return;
			if (this.transport != null) {
				transport.close();
				this.transport = null;
			}
			Throwable t = dispatcher.run(new Dispatchable() {
				@Override
				public void run() throws Throwable {
					listener.onTransportRemoved(transport);
				}
			});
			if (t == null) {
				if (!scheduleReconnect())
					_close();
			} else {
				if (Trace.isErrorEnabled())
					Trace.error(this + " removeProtocolTransport " + transport, t);
				_close();
			}
			cond.signal();
		} finally {
			lock.unlock();
		}
	}

	private void _close() {
		state = State.CLOSE;
		Engine.remove(this);
	}

	@Override
	public void close() {
		if (Engine.remove(this))
			return;
		lock.lock();
		try {
			autoReconnect = false;
			switch (state) {
			case EXCHANGE:
				AbstractTransport _trasport = transport;
				transport = null;
				_trasport.close();
				while (state != State.CLOSE)
					try {
						cond.await();
					} catch (InterruptedException e) {
					}
				break;
			case CONNECTING:
				Throwable t = dispatcher.run(new Dispatchable() {
					@Override
					public void run() throws Throwable {
						listener.onAbort(null);
					}
				});
				if (t != null && Trace.isErrorEnabled())
					Trace.error(this + " connectAbort", t);
				break;
			case INIT:
				if (future != null)
					future.cancel(false);
			case CLOSE:
			}
			super.close();
			dispatcher.await();
			dispatcher.run(new Dispatchable() {
				@Override
				public void run() throws Throwable {
					listener.onManagerUninitialized(ClientManagerImpl.this);
				}
			});
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void close(Transport transport) {
		if (this.transport == transport)
			close();
	}

	@Override
	public Config getConfig() {
		return config;
	}

	@Override
	public Transport getTransport() {
		return transport;
	}

	@Override
	public Listener getListener() {
		return listener;
	}

	@Override
	public String toString() {
		return config.getName();
	}

	@Override
	public Manager getWrapperManager() {
		return wrapper;
	}

}
