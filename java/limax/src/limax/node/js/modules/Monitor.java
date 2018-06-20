package limax.node.js.modules;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.management.Attribute;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import limax.node.js.EventLoop;
import limax.node.js.EventLoop.EventObject;
import limax.node.js.Module;
import limax.util.monitor.MonitorCollector;

public class Monitor implements Module {
	private final EventLoop eventLoop;

	public Monitor(EventLoop eventLoop) {
		this.eventLoop = eventLoop;
	}

	public class Host {
		private final Map<String, ?> env;
		private final String url;
		private JMXConnector connector;
		private MBeanServerConnection mbserver;
		private EventObject evo;
		private final AtomicInteger running = new AtomicInteger(0);
		private volatile Runnable destroycb;

		Host(String host, int serverPort, int rmiPort, String username, String password) {
			this.url = MonitorCollector.formatJMXServiceURLString(host, serverPort, rmiPort);
			this.env = username == null ? null
					: Collections.singletonMap(JMXConnector.CREDENTIALS, new String[] { username, password });
			this.evo = eventLoop.createEventObject();
		}

		public void query(String name, Object callback) {
			if (destroycb != null) {
				eventLoop.createCallback(callback).call(new Exception("Host destroyed"));
				return;
			}
			running.incrementAndGet();
			eventLoop.execute(callback, r -> {
				synchronized (this) {
					try {
						ObjectName objname = new ObjectName(name);
						if (connector == null) {
							connector = JMXConnectorFactory.connect(new JMXServiceURL(url), env);
							mbserver = connector.getMBeanServerConnection();
						}
						r.add(mbserver
								.getAttributes(objname,
										Arrays.stream(mbserver.getMBeanInfo(objname).getAttributes())
												.map(i -> i.getName()).toArray(String[]::new))
								.asList().stream().collect(Collectors.toMap(Attribute::getName, Attribute::getValue)));
					} catch (Exception e) {
						if (connector != null) {
							connector.close();
							connector = null;
						}
						throw e;
					} finally {
						if (running.decrementAndGet() == 0 && destroycb != null)
							destroycb.run();
					}
				}
			});
		}

		public void destroy(Object callback) {
			if (destroycb != null)
				return;
			destroycb = () -> eventLoop.execute(callback, r -> {
				evo.queue();
				connector.close();
			});
			if (running.get() == 0)
				destroycb.run();
		}

		public void ref() {
			evo.ref();
		}

		public void unref() {
			evo.unref();
		}
	}

	public Host connect(String host, int serverPort, int rmiPort, String username, String password) {
		return new Host(host, serverPort, rmiPort, username, password);
	}
}
