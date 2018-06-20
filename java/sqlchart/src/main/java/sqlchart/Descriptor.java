package sqlchart;

public class Descriptor {
	public final String table;
	public final String desc;
	public final KeyDesc[] keys = null;
	public final ValueDesc[] values = null;

	public Descriptor(String table, String desc) {
		this.table = table;
		this.desc = desc;
	}
}
