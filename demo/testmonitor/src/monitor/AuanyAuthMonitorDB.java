package monitor;

import java.sql.Connection;
import java.sql.DriverManager;

import limax.auanymonitor.AuthApp;
import limax.util.monitor.MonitorCollector;

public class AuanyAuthMonitorDB {

	public static void main(String[] args) throws Exception {
		String url = "jdbc:mysql://localhost:3306/test?user=root&password=admin&autoReconnect=true";
		MonitorCollector mc = new MonitorCollector((host, exception) -> exception.printStackTrace());
		try (Connection conn = DriverManager.getConnection(url)) {
			try {
				mc.addHost("testhost", MonitorCollector.formatJMXServiceURLString("localhost", 10202, 10201), null,
						null);
				mc.addSQLExecutor(sql -> {
					synchronized (conn) {
						sql.accept(conn);
					}
				});
				mc.addCollector("testhost", AuthApp.Collector.buildObjectNameQueryString(null), 1000);
				Thread.sleep(60000l);
			} finally {
				mc.stop();
			}
		}
	}
}
