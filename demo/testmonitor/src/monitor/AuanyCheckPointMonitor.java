package monitor;

import java.util.Date;
import java.util.Map;

import javax.management.ObjectName;

import limax.util.monitor.Collector;
import limax.util.monitor.CollectorController;

public class AuanyCheckPointMonitor implements Collector {

	@Override
	public void onController(CollectorController controller) {
		try {
			controller.addHost("localauany", "service:jmx:rmi://localhost:10202/jndi/rmi://localhost:10201/jmxrmi",
					null, null);
			controller.addCollector("localauany", "limax.zdb:type=Zdb,name=Checkpoint", 30000l);
			Thread.sleep(60000l);
		} catch (Exception e) {
		} finally {
			controller.stop();
		}
	}

	@Override
	public void onRecord(String host, ObjectName objname, Map<String, Object> item) {
		System.out.println(new Date() + " " + host + " " + objname);
		item.forEach((k, v) -> System.out.println("\t" + k + " " + v));
	}
}
