package limax.auany.logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import org.w3c.dom.Element;

import limax.auany.AccountLogger;
import limax.util.ConcurrentEnvironment;
import limax.util.Pair;
import limax.util.Trace;
import limax.util.XMLUtils;

public class AccountLoggerMysql implements AccountLogger {
	private static Connection conn;
	private static ThreadPoolExecutor executor;

	private Pair<String, String> splitUID(String uid) {
		int pos = uid.lastIndexOf('@');
		return pos == -1 ? new Pair<>(uid, "") : new Pair<>(uid.substring(0, pos), uid.substring(pos + 1));
	}

	@Override
	public void close() throws Exception {
		ConcurrentEnvironment.getInstance().shutdown("AccountLoggerMysql");
		conn.close();
	}

	@Override
	public void initialize(Element e) throws Exception {
		conn = DriverManager.getConnection(e.getAttribute("url"));
		conn.setAutoCommit(false);
		List<String> sqls = XMLUtils.getChildElements(e).stream().filter(node -> node.getNodeName().equals("sql"))
				.map(node -> XMLUtils.getCDataTextChildren(node)).collect(Collectors.toList());
		try (Statement st = conn.createStatement()) {
			for (String sql : sqls)
				st.execute(sql);
		}
		executor = ConcurrentEnvironment.getInstance().newFixedThreadPool("AccountLoggerMysql", 1);
	}

	@Override
	public void link(int appid, long sessionid, String uid) {
		executor.execute(() -> {
			Pair<String, String> pair = splitUID(uid);
			try (PreparedStatement ps = conn.prepareStatement("INSERT INTO `account` VALUES(?,?,?,?,?,?);")) {
				Timestamp now = new Timestamp(System.currentTimeMillis());
				ps.setLong(1, sessionid);
				ps.setInt(2, appid);
				ps.setString(3, pair.getKey());
				ps.setString(4, pair.getValue());
				ps.setTimestamp(5, now);
				ps.setTimestamp(6, now);
				ps.execute();
				conn.commit();
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("AccountLoggerMysql.link " + appid + " " + sessionid + " " + uid + "[" + e.getMessage()
							+ "]");
				try {
					conn.rollback();
				} catch (Exception e1) {
				}
			}
		});
	}

	@Override
	public void relink(int appid, long sessionid, String uidsrc, String uiddst) {
		executor.execute(() -> {
			Pair<String, String> pairsrc = splitUID(uidsrc);
			Pair<String, String> pairdst = splitUID(uiddst);
			try (PreparedStatement ps0 = conn.prepareStatement("INSERT INTO `account_change` VALUES(?,?,?,?,?,?,?)");
					PreparedStatement ps1 = conn
							.prepareStatement("UPDATE `account` SET `name`=?,`plat`=?,`mtime`=? WHERE `sessionid`=?")) {
				Timestamp now = new Timestamp(System.currentTimeMillis());
				ps0.setLong(1, sessionid);
				ps0.setInt(2, appid);
				ps0.setString(3, pairsrc.getKey());
				ps0.setString(4, pairsrc.getValue());
				ps0.setString(5, pairdst.getKey());
				ps0.setString(6, pairdst.getValue());
				ps0.setTimestamp(7, now);
				ps1.setString(1, pairdst.getKey());
				ps1.setString(2, pairdst.getValue());
				ps1.setTimestamp(3, now);
				ps1.setLong(4, sessionid);
				ps0.execute();
				ps1.execute();
				conn.commit();
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("AccountLoggerMysql.relink " + appid + " " + sessionid + " " + uidsrc + " " + uiddst
							+ "[" + e.getMessage() + "]");
				try {
					conn.rollback();
				} catch (Exception e1) {
				}
			}
		});
	}
}
