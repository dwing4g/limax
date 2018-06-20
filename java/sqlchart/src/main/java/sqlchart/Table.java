package sqlchart;

public class Table {
	public final String table;
	public volatile boolean exist;
	public String[] keys;
	public volatile Column[] columns;

	public Table(String table) {
		this.table = table;
	}

	public DataType findType(String col) {
		Column[] volatileTmp = columns;
		if (volatileTmp != null) {
			for (Column c : volatileTmp) {
				if (c.col.equals(col))
					return c.type;
			}
		}
		return null;
	}
}
