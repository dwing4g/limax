package monitor;

import java.sql.Connection;
import java.sql.DriverManager;

import limax.util.monitor.MonitorCollector;

public class MyAppDB {

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
				mc.addCollector("testhost", testmonitor.AuthProvider.Collector.buildObjectNameQueryString(null, null),
						1000);
				mc.addCollector("testhost", testmonitor.Online.Collector.buildObjectNameQueryString(null, null, null),
						1000);
				Thread.sleep(100);
			} finally {
				mc.stop();
			}
		}
	}
}
