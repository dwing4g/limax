package sqlchart;

import spark.utils.Assert;

public class Chart {
	public final ChartType type;
	public final String[] keys;
	public final String[] values;

	public Chart(ChartType type, String[] keys, String[] values) {
		this.type = type;
		this.keys = keys;
		this.values = values;
	}

	void validate() {
		Assert.state(values != null && values.length > 0, "values empty");
		Assert.state(keys != null && keys.length < 3, "keys.length > 2");
	}
}
