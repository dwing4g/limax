package limax.zdb;

import java.io.IOException;
import java.util.Arrays;

import limax.codec.Octets;
import limax.edb.QueryData;

class StorageEdb implements StorageEngine {
	private final LoggerEdb edb;
	private final String tableName;

	public StorageEdb(LoggerEdb edb, String tableName) {
		this.edb = edb;
		this.tableName = tableName;
		try {
			this.edb.getDatabase().addTable(new String[] { tableName });
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public boolean exist(Octets key) {
		byte[] tkey = key.capacity() == key.size() ? key.array() : Arrays.copyOf(key.array(), key.size());
		try {
			return edb.getDatabase().exist(tableName, tkey);
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public Octets find(Octets key) {
		byte[] tkey = key.capacity() == key.size() ? key.array() : Arrays.copyOf(key.array(), key.size());
		try {
			byte[] tvalue = edb.getDatabase().find(tableName, tkey);
			return tvalue != null ? Octets.wrap(tvalue) : null;
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public void walk(IWalk iw) {
		try {
			edb.getDatabase().walk(tableName, new QueryData() {
				@Override
				public boolean update(byte[] key, byte[] value) {
					return iw.onRecord(key, value);
				}
			});
		} catch (IOException e) {
			throw new XError(e);
		}
	}

	@Override
	public void remove(Octets key) {
		byte[] tkey = key.capacity() == key.size() ? key.array() : Arrays.copyOf(key.array(), key.size());
		try {
			edb.getDatabase().remove(tableName, tkey);
		} catch (IOException e) {
			throw new XError(e);
		}
	}

	@Override
	public void replace(Octets key, Octets value) {
		byte[] tkey = key.capacity() == key.size() ? key.array() : Arrays.copyOf(key.array(), key.size());
		byte[] tvalue = value.capacity() == value.size() ? value.array() : Arrays.copyOf(value.array(), value.size());
		try {
			edb.getDatabase().replace(tableName, tkey, tvalue);
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public boolean insert(Octets key, Octets value) {
		byte[] tkey = key.capacity() == key.size() ? key.array() : Arrays.copyOf(key.array(), key.size());
		byte[] tvalue = value.capacity() == value.size() ? value.array() : Arrays.copyOf(value.array(), value.size());
		try {
			return edb.getDatabase().insert(tableName, tkey, tvalue);
		} catch (Exception e) {
			throw new XError(e);
		}
	}
}
