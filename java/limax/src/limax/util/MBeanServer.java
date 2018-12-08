package limax.util;

import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;
import java.util.Collections;

import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;

public final class MBeanServer {

	private MBeanServer() {
	}

	public static Runnable start(String host, int serverPort, int rmiPort, String username, String password)
			throws Exception {
		LocateRegistry.createRegistry(rmiPort);
		JMXConnectorServer connectorserver = JMXConnectorServerFactory.newJMXConnectorServer(
				new JMXServiceURL(String.format("service:jmx:rmi://%s:%d/jndi/rmi://%s:%d/jmxrmi", host, serverPort,
						host, rmiPort)),
				Collections.singletonMap(JMXConnectorServer.AUTHENTICATOR, new JMXAuthenticator() {
					@Override
					public Subject authenticate(Object credentials) {
						String[] cred = (String[]) credentials;
						if ((cred != null && username.equals(cred[0]) && password.equals(cred[1]))
								|| (cred == null && (username == null || username.isEmpty())
										&& (password == null || password.isEmpty())))
							return new Subject();
						throw new SecurityException("Authentication failed!");
					}
				}), ManagementFactory.getPlatformMBeanServer());
		connectorserver.start();
		return () -> {
			try {
				connectorserver.stop();
			} catch (Exception e) {
			}
		};
	}
}
