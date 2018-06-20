package limax.auany.logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import org.w3c.dom.Element;

import limax.auany.PayDelivery;
import limax.auany.PayLogger;
import limax.auany.PayOrder;
import limax.auany.paygws.AppStore.Request;
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

	private static LinkedBlockingQueue<Connection> conns = new LinkedBlockingQueue<>();
	private static HashExecutor executor;

	@Override
	public void close() throws Exception {
		ConcurrentEnvironment.getInstance().shutdown("PayLoggerMysql");
		for (Connection conn; (conn = conns.poll()) != null;)
			conn.close();
	}

	@Override
	public void initialize(Element e) throws Exception {
		String url = e.getAttribute("url");
		Connection conn = DriverManager.getConnection(url);
		conn.setAutoCommit(false);
		List<String> sqls = XMLUtils.getChildElements(e).stream().filter(node -> node.getNodeName().equals("sql"))
				.map(node -> XMLUtils.getCDataTextChildren(node)).collect(Collectors.toList());
		try (Statement st = conn.createStatement()) {
			for (String sql : sqls)
				st.execute(sql);
		}
		int pool = new ElementHelper(e).getInt("pool", 3);
		conns.add(conn);
		for (int i = 1; i < pool; i++) {
			conn = DriverManager.getConnection(url);
			conn.setAutoCommit(false);
			conns.add(conn);
		}
		ConcurrentEnvironment.getInstance().newFixedThreadPool("PayLoggerMysql", pool);
		executor = ConcurrentEnvironment.getInstance().newHashExecutor("PayLoggerMysql", pool);
	}

	@FunctionalInterface
	private interface ConnectionConsumer {
		void accept(Connection conn) throws Exception;
	}

	private void execute(Object key, ConnectionConsumer consumer) {
		executor.execute(key, () -> {
			Connection conn;
			while (true) {
				try {
					conn = conns.take();
					break;
				} catch (InterruptedException e) {
				}
			}
			try {
				consumer.accept(conn);
				conn.commit();
			} catch (Exception e) {
				try {
					conn.rollback();
				} catch (Exception e1) {
				}
			}
			conns.offer(conn);
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
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLoggerMysql.logCreate " + order + "[" + e.getMessage() + "]");
				throw e;
			}
		});
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
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLoggerMysql.logFake " + serial + "," + gateway + "," + expect + "[" + e.getMessage()
							+ "]");
				throw e;
			}
		});
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
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLoggerMysql.logExpire " + order.getSerial() + "[" + e.getMessage() + "]");
				throw e;
			}
		});
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
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLoggerMysql.logOk " + order.getSerial() + "[" + e.getMessage() + "]");
				throw e;
			}
		});
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
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLoggerMysql.logFail " + order.getSerial() + "," + gatewayMessage + "["
							+ e.getMessage() + "]");
				throw e;
			}
		});
	}

	@Override
	public void logDead(PayDelivery pd) {
		execute(pd.getSerial(), conn -> {
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
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLoggerMysql.logDead " + pd.getSerial() + "[" + e.getMessage() + "]");
				throw e;
			}
		});
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
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLoggerMysql.logAppStoreCreate " + req + "," + gateway + "[" + e.getMessage() + "]");
				throw e;
			}
		});
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
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLoggerMysql.logAppStoreSucceed " + req.getSerial() + "[" + e.getMessage() + "]");
				throw e;
			}
		});
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
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLoggerMysql.logAppStoreFail " + req.getSerial() + "[" + e.getMessage() + "]");
				throw e;
			}
		});
	}

	@Override
	public void logAppStoreReceiptReplay(Request req) {
		execute(req.getSerial(), conn -> {
			try (PreparedStatement ps = conn.prepareStatement("INSERT INTO `appstore_replay` VALUES(?,?)")) {
				ps.setLong(1, req.getTid());
				ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
				ps.execute();
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLoggerMysql.logAppStoreReceiptReplay " + req.getTid() + "[" + e.getMessage() + "]");
				throw e;
			}
		});
	}
}
