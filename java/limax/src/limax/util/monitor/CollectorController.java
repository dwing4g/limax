package limax.util.monitor;

import java.net.MalformedURLException;

import javax.management.MalformedObjectNameException;

public interface CollectorController {
	Runnable addHost(String host, String url, String username, String password) throws MalformedURLException;

	Runnable addCollector(String host, String pattern, long period) throws MalformedObjectNameException;

	void stop();
}
