package limax.auany.local;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import limax.util.Trace;

public final class SimpleSqlOp {
	private SimpleSqlOp() {
	}

	public static boolean access(Connection conn, String username, String password) {
		try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM account WHERE username=? AND password =?")) {
			ps.setString(1, username);
			ps.setString(2, password);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		} catch (Throwable t) {
			if (Trace.isErrorEnabled())
				Trace.error("SimpleSqlOp", t);
		}
		return false;
	}
}
