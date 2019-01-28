package limax.auany.logger;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.w3c.dom.Element;

import limax.auany.PayDelivery;
import limax.auany.PayLogger;
import limax.auany.PayOrder;
import limax.auany.paygws.AppStore.Request;
import limax.sql.SQLConnectionConsumer;
import limax.sql.SQLPooledExecutor;
import limax.util.ConcurrentEnvironment;
import limax.util.ElementHelper;
import limax.util.HashExecutor;
import limax.util.Trace;
import limax.util.XMLUtils;

public class PayLoggerMysql implements PayLogger {
	public final static int ST_CREATE = 0;
	public final static int ST_OK = 1;
	public final static int ST_FAIL = 2;
	public final static int ST_EXPIRE = 3;
	public final static int ST_DEAD = 4;

	private static HashExecutor executor;
	private static SQLPooledExecutor sqlExecutor;

	@Override
	public void close() throws Exception {
		ConcurrentEnvironment.getInstance().shutdown("PayLoggerMysql");
		sqlExecutor.shutdown();
	}

	@Override
	public void initialize(Element e) throws Exception {
		List<String> sqls = XMLUtils.getChildElements(e).stream().filter(node -> node.getNodeName().equals("sql"))
				.map(node -> XMLUtils.getCDataTextChildren(node)).collect(Collectors.toList());
		int pool = new ElementHelper(e).getInt("pool", 3);
		sqlExecutor = new SQLPooledExecutor(new ElementHelper(e).getString("url"), pool);
		sqlExecutor.execute(conn -> {
			try (Statement st = conn.createStatement()) {
				for (String sql : sqls)
					st.execute(sql);
			}
		});
		ConcurrentEnvironment.getInstance().newFixedThreadPool("PayLoggerMysql", pool);
		executor = ConcurrentEnvironment.getInstance().newHashExecutor("PayLoggerMysql", pool);
	}

	private void execute(Object key, SQLConnectionConsumer consumer, Function<Exception, String> e2s) {
		executor.execute(key, () -> {
			try {
				sqlExecutor.execute(consumer);
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error(e2s.apply(e));
			}
		});
	}

	@Override
	public void logCreate(PayOrder order) {
		execute(order.getSerial(), conn -> {
			try (PreparedStatement ps = conn.prepareStatement("INSERT INTO `order` VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
				Timestamp now = new Timestamp(System.currentTimeMillis());
				ps.setLong(1, order.getSerial());
				ps.setLong(2, order.getSessionId());
				ps.setInt(3, order.getGateway());
				ps.setInt(4, order.getPayId());
				ps.setInt(5, order.getProduct());
				ps.setInt(6, order.getPrice());
				ps.setInt(7, order.getQuantity());
				ps.setTimestamp(8, now);
				ps.setTimestamp(9, now);
				ps.setInt(10, ST_CREATE);
				ps.setString(11, "");
				ps.execute();
			}
		}, e -> "PayLoggerMysql.logCreate " + order + "[" + e.getMessage() + "]");
	}

	@Override
	public void logFake(long serial, int gateway, int expect) {
		execute(serial, conn -> {
			try (PreparedStatement ps = conn.prepareStatement("INSERT INTO `order_fake` VALUES(?,?,?,?)")) {
				ps.setLong(1, serial);
				ps.setInt(2, gateway);
				ps.setInt(3, expect);
				ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
				ps.execute();
			}
		}, e -> "PayLoggerMysql.logFake " + serial + "," + gateway + "," + expect + "[" + e.getMessage() + "]");
	}

	@Override
	public void logExpire(PayOrder order) {
		execute(order.getSerial(), conn -> {
			try (PreparedStatement ps = conn
					.prepareStatement("UPDATE `order` SET `status`=?,`mtime`=? WHERE `serial`=?")) {
				ps.setInt(1, ST_EXPIRE);
				ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
				ps.setLong(3, order.getSerial());
				ps.execute();
			}
		}, e -> "PayLoggerMysql.logExpire " + order.getSerial() + "[" + e.getMessage() + "]");
	}

	@Override
	public void logOk(PayOrder order) {
		execute(order.getSerial(), conn -> {
			try (PreparedStatement ps = conn
					.prepareStatement("UPDATE `order` SET `status`=?,`mtime`=? WHERE `serial`=?")) {
				ps.setInt(1, ST_OK);
				ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
				ps.setLong(3, order.getSerial());
				ps.execute();
			}
		}, e -> "PayLoggerMysql.logOk " + order.getSerial() + "[" + e.getMessage() + "]");
	}

	@Override
	public void logFail(PayOrder order, String gatewayMessage) {
		execute(order.getSerial(), conn -> {
			try (PreparedStatement ps = conn
					.prepareStatement("UPDATE `order` SET `status`=?,`mtime`=?,`message`=? WHERE `serial`=?")) {
				ps.setInt(1, ST_FAIL);
				ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
				ps.setString(3, gatewayMessage);
				ps.setLong(4, order.getSerial());
				ps.execute();
			}
		}, e -> "PayLoggerMysql.logFail " + order.getSerial() + "," + gatewayMessage + "[" + e.getMessage() + "]");
	}

	@Override
	public void logDead(PayDelivery pd) {
		execute(pd.getSerial(), conn -> {
			conn.setAutoCommit(false);
			try (PreparedStatement ps0 = conn
					.prepareStatement("UPDATE `order` SET `status`=?,`mtime`=? WHERE `serial`=?");
					PreparedStatement ps1 = conn
							.prepareStatement("UPDATE `appstore_order` SET `status`=?,`mtime`=? WHERE `serial`=?")) {
				Timestamp now = new Timestamp(System.currentTimeMillis());
				ps0.setInt(1, ST_DEAD);
				ps1.setInt(1, ST_DEAD);
				ps0.setTimestamp(2, now);
				ps1.setTimestamp(2, now);
				ps0.setLong(3, pd.getSerial());
				ps1.setLong(3, pd.getSerial());
				ps0.execute();
				ps1.execute();
			}
		}, e -> "PayLoggerMysql.logDead " + pd.getSerial() + "[" + e.getMessage() + "]");
	}

	@Override
	public void logAppStoreCreate(Request req, int gateway) {
		execute(req.getSerial(), conn -> {
			try (PreparedStatement ps = conn
					.prepareStatement("INSERT INTO appstore_order VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
				Timestamp now = new Timestamp(System.currentTimeMillis());
				ps.setLong(1, req.getSerial());
				ps.setLong(2, req.getTid());
				ps.setLong(3, req.getSessionId());
				ps.setInt(4, gateway);
				ps.setInt(5, req.getPayId());
				ps.setInt(6, req.getProduct());
				ps.setInt(7, req.getQuantity());
				ps.setTimestamp(8, now);
				ps.setTimestamp(9, now);
				ps.setInt(10, ST_CREATE);
				ps.setInt(11, 0);
				ps.execute();
			}
		}, e -> "PayLoggerMysql.logAppStoreCreate " + req + "," + gateway + "[" + e.getMessage() + "]");
	}

	@Override
	public void logAppStoreSucceed(Request req) {
		execute(req.getSerial(), conn -> {
			try (PreparedStatement ps = conn
					.prepareStatement("UPDATE `appstore_order` SET `status`=?,`mtime`=? WHERE `serial`=?")) {
				ps.setInt(1, ST_OK);
				ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
				ps.setLong(3, req.getSerial());
				ps.execute();
			}
		}, e -> "PayLoggerMysql.logAppStoreSucceed " + req.getSerial() + "[" + e.getMessage() + "]");
	}

	@Override
	public void logAppStoreFail(Request req, int status) {
		execute(req.getSerial(), conn -> {
			try (PreparedStatement ps = conn.prepareStatement(
					"UPDATE `appstore_order` SET `status`=?,`mtime`=?,`status_appstore`=? WHERE serial=?")) {
				ps.setInt(1, ST_FAIL);
				ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
				ps.setInt(3, status);
				ps.setLong(4, req.getSerial());
				ps.execute();
			}
		}, e -> "PayLoggerMysql.logAppStoreFail " + req.getSerial() + "[" + e.getMessage() + "]");
	}

	@Override
	public void logAppStoreReceiptReplay(Request req) {
		execute(req.getSerial(), conn -> {
			try (PreparedStatement ps = conn.prepareStatement("INSERT INTO `appstore_replay` VALUES(?,?)")) {
				ps.setLong(1, req.getTid());
				ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
				ps.execute();
			}
		}, e -> "PayLoggerMysql.logAppStoreReceiptReplay " + req.getTid() + "[" + e.getMessage() + "]");
	}
}
