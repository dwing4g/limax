package limax.zdb.tool;

import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.zdb.DBC;

public interface DataWalker {
	boolean onRecord(DataKeyValue row);

	public static void walk(DBC.Table table, DataWalker walker) {
		final SchemaKeyValue schema = Schemas.of(table.meta());
		table.walk((key, data) -> {
			DataKeyValue row = schema.create();
			try {
				row.unmarshal(OctetsStream.wrap(new Octets(key).append(data)));
			} catch (MarshalException e) {
				throw new RuntimeException(e);
			}
			return walker.onRecord(row);
		});
	}
}