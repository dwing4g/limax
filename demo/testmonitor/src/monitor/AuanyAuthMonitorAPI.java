package monitor;

import limax.auanymonitor.AuthApp;
import limax.util.monitor.MonitorCollector;

public class AuanyAuthMonitorAPI {

	public static void main(String[] args) throws Exception {
		MonitorCollector mc = new MonitorCollector();

		mc.addHost("testhost", MonitorCollector.formatJMXServiceURLString("localhost", 10202, 10201), null, null);
		mc.addCollectorInstance((AuthApp.Collector) (host, appid, _newaccount, _auth) -> {
			System.out.println(
					"host = " + host + " appid = " + appid + " newaccount = " + _newaccount + " auth = " + _auth);
		});
		mc.addCollector("testhost", AuthApp.Collector.buildObjectNameQueryString(null), 1000);

		Thread.sleep(60000l);
		mc.stop();
	}
}
