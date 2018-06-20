package sqlchart;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class SqlConnectionPool {
	private String url;
	private int poolSize;
	private BlockingQueue<Connection> pool = new LinkedBlockingDeque<>();
	private AtomicInteger currentPoolSize = new AtomicInteger();

	public SqlConnectionPool(String url, int poolSize) {
		this.url = url;
		this.poolSize = poolSize;
	}

	public void close() {
		while (true) {
			Connection cc = pool.poll();
			if (cc == null)
				break;
			try {
				cc.close();
			} catch (SQLException ignore) {
			}
		}
	}

	public Connection getConnection() {
		Connection connection = pool.poll();
		if (null != connection)
			return connection;
		int readHoldSize = currentPoolSize.incrementAndGet();
		if (readHoldSize > poolSize) {
			currentPoolSize.decrementAndGet();
			try {
				return pool.take();
			} catch (InterruptedException ignore) {
			}
		}
		try {
			return DriverManager.getConnection(url);
		} catch (SQLException e) {
			currentPoolSize.decrementAndGet();
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public void freeConnection(Connection connection) {
		try {
			pool.put(connection);
		} catch (Exception ignore) {
		}
	}
}
