package sqlchart;

public class DataSource {
	public final String name;
	public final String url;
	public final int poolSize;
	public volatile boolean alive;
	public volatile Table[] tables;
	public final KeyDesc[] keyDescs = null;
	public final Descriptor[] descriptors = null;
	public final ChartSeries[] chartSeriess = null;

	public transient volatile SqlConnectionPool connectionPool;

	public DataSource(String name, String url, int poolSize) {
		this.name = name;
		this.url = url;
		this.poolSize = poolSize;
	}

	static Table findTable(Table[] tabs, String name) {
		Table[] volatileTmp = tabs;
		if (volatileTmp != null) {
			for (Table t : volatileTmp)
				if (t.table.equals(name))
					return t;
		}
		return null;
	}

}
