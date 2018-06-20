package sqlchart;

public class KeyDesc {
	public final String col;
	public final String desc;
	public final String base = null;
	public final Sep[] seps = null;

	public KeyDesc(String col, String desc) {
		this.col = col;
		this.desc = desc;
	}
}
