package monitor;

import java.util.Date;
import java.util.Map;

import javax.management.ObjectName;

import limax.util.monitor.Collector;
import limax.util.monitor.CollectorController;
import limax.util.monitor.MonitorCollector;

public class AuanyAuthMonitor implements Collector {

	@Override
	public void onController(CollectorController controller) {
		try {
			controller.addHost("localauany", "service:jmx:rmi://localhost:10202/jndi/rmi://localhost:10201/jmxrmi",
					null, null);
			controller.addCollector("localauany", "limax.auanymonitor.AuthProvider:*", 30000l);
			Thread.sleep(60000l);
		} catch (Exception e) {
		} finally {
			controller.stop();
		}
	}

	@Override
	public void onRecord(String host, ObjectName objname, Map<String, Object> item) {

		try {
			MonitorCollector.getKeyTypesByObjectName(objname).entrySet()
					.forEach(e -> System.out.println("	" + e.getKey() + " : " + e.getValue().getName()));
		} catch (Exception e) {
			e.printStackTrace();
		}

		final String className = objname.getDomain();
		final Map<String, String> keys = objname.getKeyPropertyList();
		System.out.println(new Date() + " " + host + " " + objname);
		System.out.println("\tclassName " + className);
		keys.forEach((k, v) -> System.out.println("\t" + k + " " + v));
		item.forEach((k, v) -> System.out.println("\t" + k + " " + v));
	}

}
