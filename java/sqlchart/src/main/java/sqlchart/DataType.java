package sqlchart;

import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

public enum DataType {
	INT, STRING, TIMESTAMP;

	private static Map<Integer, DataType> map = new HashMap<>();

	static {
		map.put(Types.TINYINT, INT);
		map.put(Types.SMALLINT, INT);
		map.put(Types.INTEGER, INT);
		map.put(Types.BIGINT, INT);

		map.put(Types.CHAR, STRING);
		map.put(Types.VARCHAR, STRING);

		map.put(Types.TIMESTAMP, TIMESTAMP);
	}

	public static DataType of(int t) {
		return map.get(t);
	}
}
