package limax.executable;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import javax.management.Attribute;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

class Jmxc {

	private final JMXConnector connector;

	Jmxc(JMXConnector connector) {
		this.connector = connector;
	}

	public void close() throws IOException {
		connector.close();
	}

	public MBeanServerConnection mbeanServer() throws IOException {
		return connector.getMBeanServerConnection();
	}

	public Set<ObjectName> queryNames(String domain) throws Exception {
		return mbeanServer().queryNames(domain != null ? new ObjectName(domain + ":*") : null, null);
	}

	public Object invoke(String bean, String operation, Object[] params, String[] signature) throws Exception {
		return mbeanServer().invoke(new ObjectName(bean), operation, params, signature);
	}

	public void setAttribute(String bean, String attrName, Object val) throws Exception {
		mbeanServer().setAttribute(new ObjectName(bean), new Attribute(attrName, val));
	}

	public MBeanInfo getMBeanInfo(ObjectName objectName) throws Exception {
		return mbeanServer().getMBeanInfo(objectName);
	}

	public MBeanInfo getMBeanInfo(String objectName) throws Exception {
		return getMBeanInfo(new ObjectName(objectName));
	}

	public static Jmxc connect(Options options) throws Exception {
		return connect(options.getValue("-c"), options.getValue("-u"), options.getValue("-p"));
	}

	private final static String[][] _options_ = {
			{ "-u", "username", "username that can be used when making the connection.", null },
			{ "-p", "password", "password that can be used when making the connection.", null },
			{ "-c", "connection", "JMX URL (service:jmx:<protocol>://...).", "!" } };

	public static String[][] options() {
		return _options_;
	}

	public static Jmxc connect(String url, String username, String password) throws Exception {
		return new Jmxc(JMXConnectorFactory.connect(new JMXServiceURL(url), username == null || username.isEmpty()
				? null : Collections.singletonMap(JMXConnector.CREDENTIALS, new String[] { username, password })));
	}
}
