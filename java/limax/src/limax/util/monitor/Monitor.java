package limax.util.monitor;

import java.net.MalformedURLException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.management.Attribute;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import limax.util.ConcurrentEnvironment;

public final class Monitor {
	private final static AtomicLong serial = new AtomicLong();
	private final String poolname = "Monitor-Scheduler-" + serial.getAndIncrement();
	private ScheduledThreadPoolExecutor scheduler;
	private Map<String, Host> hosts;
	private Collector collector;

	private Monitor() {
	}

	private final CollectorController controller = new CollectorController() {

		@Override
		public synchronized Runnable addHost(String host, String url, String username, String password)
				throws MalformedURLException {
			if (hosts == null)
				throw new IllegalStateException("Monitor is stopped");
			Host hostobj = new Host(host, url, username, password);
			if (hosts.putIfAbsent(host, hostobj) != null)
				throw new IllegalArgumentException("Duplicate host " + host);
			scheduler.setCorePoolSize(hosts.size() + 1);
			return () -> {
				synchronized (this) {
					if (hosts != null && hosts.remove(host, hostobj)) {
						hostobj.join();
						scheduler.setCorePoolSize(hosts.size() + 1);
					}
				}
			};
		}

		@Override
		public synchronized Runnable addCollector(String host, String pattern, long period)
				throws MalformedObjectNameException {
			if (hosts == null)
				throw new IllegalStateException("Monitor is stopped");
			Host h = hosts.get(host);
			if (h == null)
				throw new IllegalArgumentException("Host " + host + " not exists");
			Future<?> future = h.addQuery(pattern, period);
			return () -> {
				try {
					synchronized (this) {
						hosts.get(host).removeQuery(future);
					}
				} catch (Exception e) {
				}
			};
		}

		@Override
		public synchronized void stop() {
			if (hosts == null)
				throw new IllegalStateException("Monitor is stopped");
			hosts.values().forEach(host -> host.shutdown());
			hosts.values().forEach(host -> host.join());
			ConcurrentEnvironment.getInstance().shutdown(poolname);
			hosts.values().forEach(host -> host.close());
			hosts = null;
		}
	};

	private class Host {
		private final String host;
		private final JMXServiceURL url;
		private final Map<String, ?> env;
		private final Queue<Runnable> tasks = new ArrayDeque<Runnable>();
		private final Queue<Future<?>> futures = new ConcurrentLinkedQueue<>();
		private Runnable active;
		private volatile JMXConnector c;

		Host(String host, String url, String username, String password) throws MalformedURLException {
			this.host = host;
			this.url = new JMXServiceURL(url);
			this.env = username == null ? null
					: Collections.singletonMap(JMXConnector.CREDENTIALS, new String[] { username, password });
		}

		void close() {
			try {
				c.close();
			} catch (Exception e) {
			} finally {
				c = null;
			}
		}

		private MBeanServerConnection mbeanServer() {
			if (c == null) {
				try {
					c = JMXConnectorFactory.connect(url, env);
				} catch (Exception e) {
					return null;
				}
			}
			try {
				return c.getMBeanServerConnection();
			} catch (Exception e) {
				close();
				return null;
			}
		}

		private synchronized void execute(Runnable r) {
			tasks.offer(() -> {
				try {
					r.run();
				} finally {
					scheduleNext();
				}
			});
			if (active == null)
				scheduleNext();
		}

		private synchronized void scheduleNext() {
			if ((active = tasks.poll()) != null)
				scheduler.execute(active);
			else
				notify();
		}

		synchronized void join() {
			while (!tasks.isEmpty())
				try {
					wait();
				} catch (InterruptedException e) {
				}
		}

		Future<?> addQuery(String pattern, long period) throws MalformedObjectNameException {
			ObjectName objname = new ObjectName(pattern);
			Future<?> future = scheduler.scheduleAtFixedRate(() -> execute(() -> collect(objname)), 0, period,
					TimeUnit.MILLISECONDS);
			futures.add(future);
			return future;
		}

		void removeQuery(Future<?> future) {
			futures.remove(future);
			future.cancel(false);
		}

		void shutdown() {
			futures.forEach(future -> future.cancel(false));
		}

		void collect(ObjectName pattern) {
			MBeanServerConnection mbs = mbeanServer();
			if (mbs == null)
				return;
			try {
				mbs.queryNames(pattern, null).forEach(objname -> {
					try {
						collector.onRecord(host,
								objname, mbs
										.getAttributes(objname,
												Arrays.stream(mbs.getMBeanInfo(objname).getAttributes())
														.map(i -> i.getName()).toArray(String[]::new))
										.asList().stream()
										.collect(Collectors.toMap(Attribute::getName, Attribute::getValue)));
					} catch (Exception e) {
						collector.onException(host, e);
					}
				});
			} catch (Exception e) {
				close();
				collector.onException(host, e);
			}
		}
	}

	private CollectorController run(Collector collector) {
		synchronized (controller) {
			this.collector = collector;
			scheduler = ConcurrentEnvironment.getInstance().newScheduledThreadPool(poolname, 1);
			hosts = new HashMap<>();
			collector.onController(controller);
			return controller;
		}
	}

	public static CollectorController start(Collector collector) {
		return new Monitor().run(collector);
	}
}
