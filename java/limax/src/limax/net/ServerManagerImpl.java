package limax.net;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import limax.codec.CodecException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.net.io.NetModel;
import limax.net.io.NetTask;
import limax.net.io.ServerContext;
import limax.util.Dispatcher;
import limax.util.Trace;

class ServerManagerImpl extends AbstractManager
		implements ServerManager, SupportTypedDataBroadcast, SupportWebSocketBroadcast {
	static {
		Engine.registerDriver(new Driver() {
			@Override
			public Class<? extends Config> getConfigClass() {
				return ServerManagerConfig.class;
			}

			@Override
			public Class<? extends Listener> getListenerClass() {
				return ServerListener.class;
			}

			@Override
			public Manager newInstance(Config config, Listener listener, Manager wrapper) throws Exception {
				return new ServerManagerImpl((ServerManagerConfig) config, (ServerListener) listener, wrapper);
			}
		});
	}

	private final ServerManagerConfig config;
	private final ServerListener listener;
	private final Manager wrapper;
	private final Dispatcher dispatcher;

	private final Set<AbstractTransport> transports = new HashSet<AbstractTransport>();
	private final ReentrantLock lock = new ReentrantLock();
	private final Condition cond = lock.newCondition();

	private final ServerContext context;

	private ServerManagerImpl(final ServerManagerConfig config, ServerListener listener, Manager wrapper)
			throws Exception {
		this.config = config;
		this.listener = listener;
		final boolean webSocketEnabled = config.isWebSocketEnabled();
		this.context = NetModel.addServer(config.getLocalAddress(), config.getBacklog(), config.getInputBufferSize(),
				config.getOutputBufferSize(), config.getSSLContext(), 0, new ServerContext.NetTaskConstructor() {
					@Override
					public NetTask newInstance(ServerContext context) {
						return webSocketEnabled
								? WebSocketServer.createServerTask(context,
										new WebSocketTransportImpl(ServerManagerImpl.this))
								: NetModel.createServerTask(context, new StateTransportImpl(ServerManagerImpl.this));
					}

					@Override
					public String getServiceName() {
						return ServerManagerImpl.this.getClass().getName() + " "
								+ ServerManagerImpl.this.config.getName();
					}
				}, false, config.isAsynchronous());
		this.wrapper = wrapper;
		this.dispatcher = config.getDispatcher();
		listener.onManagerInitialized(this, this.config);
		if (config.isAutoListen())
			context.open();
	}

	@Override
	public final void dispatch(Runnable r, Object hit) {
		dispatcher.execute(r, hit);
	}

	@Override
	void addProtocolTransport(final AbstractTransport transport) {
		lock.lock();
		try {
			if (config.getTransportLimit().request()) {
				try {
					listener.onTransportAdded(transport);
					transports.add(transport);
				} catch (Throwable t) {
					config.getTransportLimit().reclaim();
					if (Trace.isErrorEnabled())
						Trace.error(this + " addProtocolTransport " + transport, t);
					transport.close();
				}
			} else {
				if (Trace.isWarnEnabled())
					Trace.warn(this + " addProtocolTransport " + transport + " close overflow " + transports.size());
				transport.close();
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	void removeProtocolTransport(final AbstractTransport transport) {
		lock.lock();
		try {
			if (!transports.remove(transport))
				return;
			try {
				listener.onTransportRemoved(transport);
			} catch (Throwable t) {
				if (Trace.isErrorEnabled())
					Trace.error(this + " removeProtocolTransport " + transport, t);
			} finally {
				config.getTransportLimit().reclaim();
				if (transports.isEmpty())
					cond.signal();
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Config getConfig() {
		return config;
	}

	@Override
	public void close() {
		if (Engine.remove(this))
			return;
		lock.lock();
		try {
			try {
				context.close();
			} catch (IOException e) {
			}
			for (AbstractTransport transport : transports)
				transport.close();
			while (!transports.isEmpty())
				try {
					cond.await();
				} catch (InterruptedException e) {
				}
			super.close();
			dispatcher.await();
			listener.onManagerUninitialized(this);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void close(Transport transport) {
		lock.lock();
		try {
			if (transports.contains(transport))
				((AbstractTransport) transport).close();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean isListening() {
		lock.lock();
		try {
			return context.isOpen();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void openListen() throws IOException {
		lock.lock();
		try {
			context.open();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void closeListen() throws IOException {
		lock.lock();
		try {
			context.close();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Listener getListener() {
		return listener;
	}

	private Collection<Transport> getTransports() {
		lock.lock();
		try {
			return new ArrayList<Transport>(transports);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void broadcast(int type, Octets data)
			throws InstantiationException, ClassCastException, SizePolicyException, CodecException {
		final Octets octets = new OctetsStream().marshal(type).marshal(data);
		for (final Transport transport : getTransports()) {
			try {
				if (transport instanceof SupportStateCheck)
					((SupportStateCheck) transport).check(type, octets.size());
				((StateTransportImpl) transport).sendData(octets);
			} catch (ClassCastException e) {
				throw e;
			} catch (Exception e) {
			}
		}
	}

	@Override
	public void broadcast(String data) throws CodecException, ClassCastException {
		for (final Transport transport : getTransports()) {
			try {
				((SupportWebSocketTransfer) transport).send(data);
			} catch (ClassCastException e) {
				throw e;
			} catch (Exception e) {
			}
		}
	}

	@Override
	public void broadcast(byte[] data) throws CodecException, ClassCastException {
		for (final Transport transport : getTransports()) {
			try {
				((SupportWebSocketTransfer) transport).send(data);
			} catch (ClassCastException e) {
				throw e;
			} catch (Exception e) {
			}
		}
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
