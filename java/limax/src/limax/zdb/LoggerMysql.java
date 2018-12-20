package limax.zdb;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;

import limax.sql.RestartTransactionException;
import limax.sql.SQLConnectionConsumer;
import limax.sql.SQLExecutor;
import limax.sql.SQLPooledExecutor;

class LoggerMysql implements LoggerEngine {
	private final SQLPooledExecutor reader;
	private final SQLPooledExecutor writer;

	public LoggerMysql(limax.xmlgen.Zdb meta) {
		try {
			String url = meta.getDbHome();
			this.reader = new SQLPooledExecutor(url, meta.getJdbcPoolSize());
			this.writer = new SQLPooledExecutor(url, 1, true);
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public void checkpoint() {
		try {
			writer.execute(SQLExecutor.COMMIT);
		} catch (RestartTransactionException e) {
			throw e;
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public void close() {
		writer.shutdown();
		reader.shutdown();
	}

	@Override
	public void backup(String path, boolean increment) throws IOException {
	}

	@Override
	public void dropTables(String[] tableNames) throws Exception {
		try {
			reader.execute(conn -> {
				for (String tableName : tableNames) {
					try (Statement st = conn.createStatement()) {
						st.execute("DROP TABLE IF EXISTS " + tableName);
					} catch (SQLException e) {
						throw new XError(e);
					}
				}
			});
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	public void read(SQLConnectionConsumer consumer) throws Exception {
		reader.execute(consumer);
	}

	public void write(SQLConnectionConsumer consumer) throws Exception {
		writer.execute(consumer);
	}
}
