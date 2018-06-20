package sqlchart;

import com.google.gson.Gson;
import spark.utils.Assert;

public class Test {

	public static void main(String[] args) throws Exception {
		DataSource ds = new DataSource("test",
				"jdbc:mysql://localhost:3306/test?user=root&password=admin&autoReconnect=true", 3);
		Table t = new Table("_online");
		ds.tables = new Table[] { t };

		Gson gson = new Gson();
		String json = gson.toJson(ds);
		String json2 = gson.toJson(gson.fromJson(json, DataSource.class));

		Assert.isTrue(json.equals(json2), "");
	}
}
