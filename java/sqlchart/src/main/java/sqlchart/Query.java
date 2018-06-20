package sqlchart;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Query {
	public String table;
	public Map<String, String> filters;
	public String[] keys;
	public String[] values;

	public List<List<Object>> doQuery(DataSource ds) {
		Table tab = DataSource.findTable(ds.tables, table);
		if (tab == null)
			throw new IllegalStateException("Table[" + table + "] not exist");

		StringBuilder sb = new StringBuilder();
		sb.append("SELECT ").append(String.join(", ", keys));
		for (String val : values) {
			sb.append(", ").append("SUM").append("(").append(val).append(")");
		}
		sb.append(" FROM ").append(table);

		if (filters != null && !filters.isEmpty()) {
			List<String> wheres = new ArrayList<>();
			filters.forEach((k, v) -> {
				switch (tab.findType(k)) {
				case INT:
					wheres.add(k + "=" + v);
					break;
				case STRING:
					wheres.add(k + "=\"" + v + "\"");
					break;
				case TIMESTAMP:
					String[] sep = v.split(",");
					String b = sep[0].trim();
					String e = sep[1].trim();
					if (!b.isEmpty())
						wheres.add(k + ">" + formatDateTime(b));
					if (!e.isEmpty())
						wheres.add(k + "<" + formatDateTime(e));
					break;
				}
			});
			sb.append(" WHERE ").append(String.join(" AND ", wheres));
		}

		sb.append(" GROUP BY ").append(String.join(", ", keys));

		int colSize = keys.length + values.length;
		List<List<Object>> result = new ArrayList<>();
		String selectSql = sb.toString();
		Connection connection = ds.connectionPool.getConnection();
		try (PreparedStatement stmt = connection.prepareStatement(selectSql)) {
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					List<Object> row = new ArrayList<>(colSize);
					for (int i = 1; i <= colSize; i++) {
						row.add(rs.getObject(i));
					}
					result.add(row);
				}
				return result;
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			ds.connectionPool.freeConnection(connection);
		}
	}

	private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH:mm");

	private static String formatDateTime(String orig) {
		if (orig.startsWith("-")) {
			int daysBefore = Integer.parseInt(orig);
			return df.format(new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(daysBefore)));
		} else {
			return orig;
		}
	}

}
