package limax.net;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import limax.net.io.NetModel;
import limax.net.io.PollPolicy;
import limax.util.Closeable;
import limax.util.ConcurrentEnvironment;
import limax.util.Dispatcher;
import limax.util.HashExecutor;
import limax.util.Helper;

public class Engine {
	private final static int limitProtocolSize = Integer.getInteger("limax.net.Engine.limitProtocolSize", 1048576);
	private final static long intranetKeepAliveTimeout = Long.getLong("limax.net.Engine.intranetKeepAliveTimeout", 0);
	private final static ReentrantLock lock = new ReentrantLock();
	private final static Condition cond = lock.newCondition();
	private final static Set<Driver> drivers = new HashSet<Driver>();
	private final static Map<Closeable, Boolean> closeables = new IdentityHashMap<Closeable, Boolean>();
	private volatile static ScheduledExecutorService protocolScheduler;
	private volatile static HashExecutor protocolExecutor;
	private volatile static HashExecutor applicationExecutor;
	private volatile static Dispatcher engineExecutor;
	private static boolean closed = true;

	static {
		try {
			Class.forName("limax.net.ServerManagerImpl");
		} catch (ClassNotFoundException e) {
		}
		try {
			Class.forName("limax.net.ClientManagerImpl");
		} catch (ClassNotFoundException e) {
		}
	}

	private Engine() {
	}

	static int checkLimitProtocolSize(int type, int size) throws SizePolicyException {
		if (size > limitProtocolSize)
			throw new SizePolicyException("type = " + type + " size = " + size
					+ " exceed limax.net.Engine.limitProtocolSize = " + limitProtocolSize);
		return size;
	}

	public static void registerDriver(Driver driver) {
		lock.lock();
		try {
			Class<?> configClass = driver.getConfigClass();
			if (!configClass.isInterface())
				throw new RuntimeException("Config class " + configClass.getName() + " is not interface");
			Class<?> listenerClass = driver.getListenerClass();
			if (!listenerClass.isInterface())
				throw new RuntimeException("Listener class " + listenerClass.getName() + " is not interface");
			Driver old = testDriver(configClass, listenerClass);
			if (null != old)
				throw new RuntimeException("Register conflicted. Config class (" + configClass.getName() + "/"
						+ old.getConfigClass().getName() + ") and Listener class (" + listenerClass.getName() + "/"
						+ old.getListenerClass().getName() + ")");
			drivers.add(driver);
		} finally {
			lock.unlock();
		}
	}

	private static Driver testDriver(Class<?> configClass, Class<?> listenerClass) {
		for (final Driver driver : drivers)
			if (Helper.interfaceSet(configClass).contains(driver.getConfigClass())
					|| Helper.interfaceSet(listenerClass).contains(driver.getListenerClass())
					|| Helper.interfaceSet(driver.getConfigClass()).contains(configClass)
					|| Helper.interfaceSet(driver.getListenerClass()).contains(listenerClass))
				return driver;
		return null;
	}

	public static void open(int netProcessors, int protocolSchedulers, int applicationExecutors) throws Exception {
		open(1, netProcessors, protocolSchedulers, applicationExecutors);
	}

	public static void open(int nioCpus, int netProcessors, int protocolSchedulers, int applicationExecutors)
			throws Exception {
		if (netProcessors < 1 || protocolSchedulers < 1 || applicationExecutors < 1 || nioCpus < 1)
			throw new IllegalArgumentException("any engine parameter must > 0");
		lock.lock();
		try {
			if (!closed)
				throw new IllegalStateException("engine is not closed!");
			NetModel.initialize(PollPolicy.createFixedCpuPoll(nioCpus), netProcessors);
			ConcurrentEnvironment env = ConcurrentEnvironment.getInstance();
			engineExecutor = new Dispatcher(env.newThreadPool("limax.net.Engine.applicationExecutor", 0));
			applicationExecutor = env.newHashExecutor("limax.net.Engine.applicationExecutor", applicationExecutors);
			protocolScheduler = env.newScheduledThreadPool("limax.net.Engine.protocolScheduler", protocolSchedulers);
			protocolExecutor = env.newHashExecutor("limax.net.Engine.protocolScheduler", protocolSchedulers);
			closed = false;
		} finally {
			lock.unlock();
		}
	}

	public static void close() {
		lock.lock();
		try {
			if (closed)
				throw new IllegalStateException("engine is not running!");
			Collection<Closeable> all = new ArrayList<Closeable>();
			for (Closeable c : closeables.keySet())
				if (c instanceof Manager) {
					if (((Manager) c).getWrapperManager() == null)
						all.add(c);
				} else
					all.add(c);
			for (Closeable c : all)
				c.close();
			while (!closeables.isEmpty())
				try {
					cond.await();
				} catch (InterruptedException e) {
				}
		} finally {
			lock.unlock();
		}
		engineExecutor.await();
		ConcurrentEnvironment.getInstance().shutdown("limax.net.Engine.protocolScheduler",
				"limax.net.Engine.applicationExecutor");
		NetModel.unInitialize();
		lock.lock();
		try {
			closed = true;
		} finally {
			lock.unlock();
		}
	}

	public static void add(Closeable c) {
		lock.lock();
		try {
			closeables.put(c, true);
		} finally {
			lock.unlock();
		}
	}

	public static Manager add(Config config, Listener listener, Manager wrapper) throws Exception {
		lock.lock();
		try {
			if (closed)
				throw new IllegalStateException("engine is not running!");
			for (Driver driver : drivers) {
				Class<?> configClass = driver.getConfigClass();
				Class<?> listenerClass = driver.getListenerClass();
				if (configClass.isInstance(config) && listenerClass.isInstance(listener)) {
					Manager manager = driver.newInstance(config, listener, wrapper);
					closeables.put(manager, true);
					if (wrapper != null && !closeables.containsKey(wrapper))
						closeables.put(wrapper, true);
					return manager;
				}
			}
			throw new InstantiationException(
					config.getClass().getName() + " with " + listener.getClass().getName() + " driver not registered");
		} finally {
			lock.unlock();
		}
	}

	public static Manager add(Config config, Listener listener) throws Exception {
		return add(config, listener, null);
	}

	public static boolean remove(final Closeable c) {
		lock.lock();
		try {
			Boolean b = closeables.get(c);
			if (b == null)
				return true;
			if (!b) {
				closeables.remove(c);
				cond.signalAll();
				return false;
			}
			engineExecutor.execute(new Runnable() {
				@Override
				public void run() {
					lock.lock();
					try {
						Boolean b = closeables.get(c);
						if (b == null || !b)
							return;
						closeables.put(c, false);
					} finally {
						lock.unlock();
					}
					c.close();
				}
			}, null);
			while (closeables.containsKey(c))
				try {
					cond.await();
				} catch (InterruptedException e) {
					continue;
				}
			return true;
		} finally {
			lock.unlock();
		}
	}

	public static boolean contains(Closeable c) {
		lock.lock();
		try {
			return closeables.containsKey(c);
		} finally {
			lock.unlock();
		}
	}

	public static ScheduledExecutorService getProtocolScheduler() {
		return protocolScheduler;
	}

	public static HashExecutor getProtocolExecutor() {
		return protocolExecutor;
	}

	public static HashExecutor getApplicationExecutor() {
		return applicationExecutor;
	}

	public static long getIntranetKeepAliveTimeout() {
		return intranetKeepAliveTimeout;
	}
}
