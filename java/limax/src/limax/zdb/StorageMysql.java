package limax.zdb;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import limax.codec.Octets;

class StorageMysql implements StorageEngine {
	private final LoggerMysql logger;
	private final String tableName;

	public StorageMysql(LoggerMysql logger, String tableName) {
		this.logger = logger;
		this.tableName = tableName;
		Connection connection = logger.getWriteConnection();
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("CREATE TABLE IF NOT EXISTS " + tableName
					+ "(id VARBINARY(767) NOT NULL PRIMARY KEY, value MEDIUMBLOB NOT NULL)ENGINE=INNODB");
		} catch (SQLException e) {
			throw new XError(e);
		}
	}

	@Override
	public void replace(Octets key, Octets value) {
		Connection connection = logger.getWriteConnection();
		try (PreparedStatement stmt = connection.prepareStatement("REPLACE INTO " + tableName + " VALUES(?, ?)")) {
			stmt.setBlob(1, new ByteArrayInputStream(key.array(), 0, key.size()));
			stmt.setBlob(2, new ByteArrayInputStream(value.array(), 0, value.size()));
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new XError(e);
		}
	}

	@Override
	public boolean insert(Octets key, Octets value) {
		Connection connection = logger.getWriteConnection();
		try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO " + tableName + " VALUES(?, ?)")) {
			stmt.setBlob(1, new ByteArrayInputStream(key.array(), 0, key.size()));
			stmt.setBlob(2, new ByteArrayInputStream(value.array(), 0, value.size()));
			stmt.executeUpdate();
		} catch (SQLException e) {
			return false;
		}
		return true;
	}

	@Override
	public void remove(Octets key) {
		Connection connection = logger.getWriteConnection();
		try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM " + tableName + " WHERE id=?")) {
			stmt.setBlob(1, new ByteArrayInputStream(key.array(), 0, key.size()));
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new XError(e);
		}
	}

	@Override
	public boolean exist(Octets key) {
		Connection connection = logger.getReadConnection();
		try (PreparedStatement stmt = connection.prepareStatement("SELECT 1 FROM " + tableName + " WHERE id=?")) {
			stmt.setBlob(1, new ByteArrayInputStream(key.array(), 0, key.size()));
			try (ResultSet rs = stmt.executeQuery()) {
				return rs.next();
			}
		} catch (SQLException e) {
			throw new XError(e);
		} finally {
			logger.freeReadConnection(connection, false);
		}
	}

	@Override
	public Octets find(Octets key) {
		Connection connection = logger.getReadConnection();
		try (PreparedStatement stmt = connection.prepareStatement("SELECT value FROM " + tableName + " WHERE id=?")) {
			stmt.setBlob(1, new ByteArrayInputStream(key.array(), 0, key.size()));
			try (ResultSet rs = stmt.executeQuery()) {
				return rs.next() ? Octets.wrap(rs.getBytes(1)) : null;
			}
		} catch (SQLException e) {
			throw new XError(e);
		} finally {
			logger.freeReadConnection(connection, false);
		}
	}

	@Override
	public void walk(IWalk iw) {
		Connection connection = logger.getReadConnection();
		boolean closeNow = false;
		try (PreparedStatement stmt = connection.prepareStatement("SELECT id, value FROM " + tableName,
				ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
			stmt.setFetchSize(5000);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					if (false == iw.onRecord(rs.getBytes(1), rs.getBytes(2))) {
						closeNow = true;
						break;
					}
				}
			}
		} catch (SQLException e) {
			throw new XError(e);
		} finally {
			logger.freeReadConnection(connection, closeNow);
		}
	}
}
