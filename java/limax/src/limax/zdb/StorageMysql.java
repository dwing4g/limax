package limax.zdb;

import java.io.ByteArrayInputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import limax.codec.Octets;
import limax.sql.RestartTransactionException;

class StorageMysql implements StorageEngine {
	private final LoggerMysql logger;
	private final String tableName;

	public StorageMysql(LoggerMysql logger, String tableName) {
		this.logger = logger;
		this.tableName = tableName;
		try {
			logger.read(conn -> {
				try (Statement st = conn.createStatement()) {
					st.execute("CREATE TABLE IF NOT EXISTS " + tableName
							+ "(id VARBINARY(767) NOT NULL PRIMARY KEY, value MEDIUMBLOB NOT NULL)ENGINE=INNODB");
				}
			});
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public void replace(Octets key, Octets value) {
		try {
			logger.write(conn -> {
				try (PreparedStatement ps = conn.prepareStatement("REPLACE INTO " + tableName + " VALUES(?, ?)")) {
					ps.setBlob(1, new ByteArrayInputStream(key.array(), 0, key.size()));
					ps.setBlob(2, new ByteArrayInputStream(value.array(), 0, value.size()));
					ps.executeUpdate();
				}
			});
		} catch (RestartTransactionException e) {
			throw e;
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public boolean insert(Octets key, Octets value) {
		try {
			logger.write(conn -> {
				try (PreparedStatement ps = conn.prepareStatement("INSERT INTO " + tableName + " VALUES(?, ?)")) {
					ps.setBlob(1, new ByteArrayInputStream(key.array(), 0, key.size()));
					ps.setBlob(2, new ByteArrayInputStream(value.array(), 0, value.size()));
					ps.executeUpdate();
				}
			});
		} catch (RestartTransactionException e) {
			throw e;
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	@Override
	public void remove(Octets key) {
		try {
			logger.write(conn -> {
				try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + tableName + " WHERE id=?")) {
					ps.setBlob(1, new ByteArrayInputStream(key.array(), 0, key.size()));
					ps.executeUpdate();
				}
			});
		} catch (RestartTransactionException e) {
			throw e;
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public boolean exist(Octets key) {
		try {
			boolean[] r = new boolean[1];
			logger.read(conn -> {
				try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM " + tableName + " WHERE id=?")) {
					ps.setBlob(1, new ByteArrayInputStream(key.array(), 0, key.size()));
					try (ResultSet rs = ps.executeQuery()) {
						r[0] = rs.next();
					}
				}
			});
			return r[0];
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public Octets find(Octets key) {
		try {
			byte[][] r = new byte[1][];
			logger.read(conn -> {
				try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM " + tableName + " WHERE id=?")) {
					ps.setBlob(1, new ByteArrayInputStream(key.array(), 0, key.size()));
					try (ResultSet rs = ps.executeQuery()) {
						if (rs.next())
							r[0] = rs.getBytes(1);
					}
				}
			});
			return r[0] != null ? Octets.wrap(r[0]) : null;
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public void walk(IWalk iw) {
		try {
			logger.read(conn -> {
				try (PreparedStatement ps = conn.prepareStatement("SELECT id, value FROM " + tableName,
						ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
					ps.setFetchSize(5000);
					try (ResultSet rs = ps.executeQuery()) {
						while (rs.next() && iw.onRecord(rs.getBytes(1), rs.getBytes(2)))
							;
					}
				}
			});
		} catch (Exception e) {
			throw new XError(e);
		}
	}
}
