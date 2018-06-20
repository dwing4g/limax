package limax.util;

import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;

import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

public final class MBeanServer {

	private MBeanServer() {
	}

	public static synchronized Runnable start(String host, int serverPort, int rmiPort) throws JMXException {
		JMXConnectorServer connectorserver;
		try {
			LocateRegistry.createRegistry(rmiPort);
			final JMXServiceURL url = new JMXServiceURL(
					String.format("service:jmx:rmi://%s:%d/jndi/rmi://%s:%d/jmxrmi", host, serverPort, host, rmiPort));
			connectorserver = JMXConnectorServerFactory.newJMXConnectorServer(url, null,
					ManagementFactory.getPlatformMBeanServer());
			connectorserver.start();
			return () -> {
				try {
					connectorserver.stop();
				} catch (Exception e) {
				}
			};
		} catch (Exception e) {
			throw new JMXException(e);
		}
	}
}
