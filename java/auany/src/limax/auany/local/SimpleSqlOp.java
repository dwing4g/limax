package limax.auany.local;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class SimpleSqlOp {
	private SimpleSqlOp() {
	}

	public static boolean access(Connection conn, String username, String password) throws Exception {
		try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM account WHERE username=? AND password =?")) {
			ps.setString(1, username);
			ps.setString(2, password);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}
}
