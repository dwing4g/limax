package limax.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransactionRollbackException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SQLPooledExecutor implements SQLExecutor {
	private final Consumer<Exception> logger;
	private final Supplier<Connection> connectionSupplier;
	private final SQLExecutor impl;
	private final Lock rlock;
	private final Lock wlock;
	private LinkedBlockingQueue<Connection> pool = new LinkedBlockingQueue<>();

	public SQLPooledExecutor(String url, int size, boolean threadAffinity, Consumer<Exception> logger) {
		this.logger = logger;
		this.connectionSupplier = () -> {
			while (true) {
				try {
					Connection conn = DriverManager.getConnection(url);
					if (threadAffinity)
						conn.setAutoCommit(false);
					return conn;
				} catch (SQLRecoverableException e) {
					this.logger.accept(e);
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
			}
		};
		this.impl = threadAffinity ? new ThreadScopeExecutor() : new FunctionScopeExecutor();
		ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
		this.rlock = lock.readLock();
		this.wlock = lock.writeLock();
		for (int i = 0; i < size; i++)
			this.pool.offer(connectionSupplier.get());
	}

	public SQLPooledExecutor(String url, int size, boolean threadAffinity) {
		this(url, size, threadAffinity, e -> {
		});
	}

	public SQLPooledExecutor(String url, int size, Consumer<Exception> logger) {
		this(url, size, false, logger);
	}

	public SQLPooledExecutor(String url, int size) {
		this(url, size, false);
	}

	@Override
	public void execute(SQLConnectionConsumer consumer) throws Exception {
		impl.execute(consumer);
	}

	private class FunctionScopeExecutor implements SQLExecutor {
		@SuppressWarnings("resource")
		@Override
		public void execute(SQLConnectionConsumer consumer) throws Exception {
			Connection conn = acquire();
			try {
				while (true)
					try {
						consumer.accept(conn);
						conn.setAutoCommit(true);
						break;
					} catch (SQLNonTransientConnectionException | SQLRecoverableException e) {
						logger.accept(e);
						conn = renew(conn);
					} catch (SQLTransactionRollbackException e) {
						logger.accept(e);
						try {
							conn.setAutoCommit(true);
						} catch (Exception e1) {
							conn = renew(conn);
						}
					} catch (Throwable e) {
						try {
							if (!conn.getAutoCommit()) {
								conn.rollback();
								conn.setAutoCommit(true);
							}
						} catch (Exception e1) {
							conn = renew(conn);
							e.addSuppressed(e1);
						}
						throw e;
					}
			} finally {
				release(conn);
			}
		}
	}

	private class ThreadScopeExecutor implements SQLExecutor {
		private final ThreadLocal<Connection> threadLocal = new ThreadLocal<>();

		@SuppressWarnings("resource")
		@Override
		public void execute(SQLConnectionConsumer consumer) throws Exception {
			Connection conn = threadLocal.get();
			if (conn == null)
				threadLocal.set(conn = acquire());
			boolean reclaim = consumer == COMMIT || consumer == ROLLBACK;
			try {
				while (true)
					try {
						consumer.accept(conn);
						break;
					} catch (SQLNonTransientConnectionException | SQLRecoverableException e) {
						logger.accept(e);
						reclaim = true;
						conn = renew(conn);
						throw new RestartTransactionException(e);
					} catch (SQLTransactionRollbackException e) {
						logger.accept(e);
						reclaim = true;
						throw new RestartTransactionException(e);
					} catch (Throwable e) {
						reclaim = true;
						try {
							conn.rollback();
						} catch (Exception e1) {
							conn = renew(conn);
							e.addSuppressed(e1);
						}
						throw e;
					}
			} finally {
				if (reclaim) {
					threadLocal.set(null);
					release(conn);
				}
			}
		}
	}

	private static void close(Connection conn) {
		try {
			conn.close();
		} catch (SQLException e) {
		}
	}

	private Connection renew(Connection conn) {
		close(conn);
		return connectionSupplier.get();
	}

	private Connection acquire() throws InterruptedException, SQLException {
		rlock.lock();
		try {
			if (pool == null)
				throw new SQLException("pool is shutdown");
			return pool.take();
		} finally {
			rlock.unlock();
		}
	}

	private void release(Connection conn) {
		rlock.lock();
		try {
			if (pool == null)
				close(conn);
			else
				pool.offer(conn);
		} finally {
			rlock.unlock();
		}
	}

	public void shutdown() {
		wlock.lock();
		try {
			if (pool == null)
				return;
			pool.forEach(SQLPooledExecutor::close);
			pool = null;
		} finally {
			wlock.unlock();
		}
	}
}
