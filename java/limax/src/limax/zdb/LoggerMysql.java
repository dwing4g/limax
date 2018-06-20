package limax.zdb;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import limax.util.Trace;

class LoggerMysql implements LoggerEngine {
	private final String url;
	private final Connection writeConnection;
	private final BlockingQueue<Connection> readPool = new LinkedBlockingDeque<Connection>();
	private final AtomicInteger readCurrentPoolSize = new AtomicInteger();
	private final int readPoolSize;

	public LoggerMysql(limax.xmlgen.Zdb meta) {
		try {
			this.url = meta.getDbHome();
			this.readPoolSize = meta.getJdbcPoolSize();
			this.writeConnection = DriverManager.getConnection(url);
			this.writeConnection.setAutoCommit(false);
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public void checkpoint() {
		try {
			writeConnection.commit();
		} catch (SQLException e) {
			try {
				writeConnection.rollback();
			} catch (SQLException e1) {
			}
			throw new XError(e);
		}
	}

	@Override
	public void close() {
		try {
			writeConnection.close();
		} catch (SQLException e) {
			if (Trace.isErrorEnabled())
				Trace.error("close connection", e);
		}
		while (true) {
			Connection cc = readPool.poll();
			if (cc == null)
				break;
			try {
				cc.close();
			} catch (SQLException e) {
				if (Trace.isErrorEnabled())
					Trace.error("close connection", e);
			}
		}
	}

	@Override
	public void backup(String path, boolean increment) throws IOException {
	}

	@Override
	public void dropTables(String[] tableNames) throws Exception {
		for (String tableName : tableNames) {
			try (Statement stmt = writeConnection.createStatement()) {
				stmt.execute("DROP TABLE IF EXISTS " + tableName);
			} catch (SQLException e) {
				throw new XError(e);
			}
		}
	}

	public Connection getReadConnection() {
		Connection connection = readPool.poll();
		if (null != connection)
			return connection;
		int readHoldSize = readCurrentPoolSize.incrementAndGet();
		if (readHoldSize > readPoolSize) {
			readCurrentPoolSize.decrementAndGet();
			try {
				return readPool.take();
			} catch (InterruptedException e) {
				throw new XError(e);
			}
		}
		try {
			return DriverManager.getConnection(url);
		} catch (SQLException e) {
			readCurrentPoolSize.decrementAndGet();
			throw new XError(e);
		}
	}

	public void freeReadConnection(Connection connection, boolean closeNow) {
		try {
			if (closeNow) {
				readCurrentPoolSize.decrementAndGet();
				connection.close();
			} else {
				readPool.put(connection);
			}
		} catch (Exception e) {
			if (Trace.isErrorEnabled())
				Trace.error("freeReadConnection", e);
		}
	}

	public Connection getWriteConnection() {
		return writeConnection;
	}

}
