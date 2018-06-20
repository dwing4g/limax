package sqlchart;

public class ChartSeries {
	public final String name;
	public final String table;
	public final ValueDesc[] filters = null;
	public final Chart[] charts = null;

	public ChartSeries(String name, String table) {
		this.name = name;
		this.table = table;
	}
}
