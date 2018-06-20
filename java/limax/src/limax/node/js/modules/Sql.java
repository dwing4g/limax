package limax.node.js.modules;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import limax.node.js.EventLoop;
import limax.node.js.EventLoop.Callback;
import limax.node.js.Module;
import limax.util.Pair;

public final class Sql implements Module {
	private final static long ConnectionFadeout = Long.getLong("limax.node.js.module.Sql.ConnectionFadeout", 60000);
	private final EventLoop eventLoop;

	public Sql(EventLoop eventLoop) {
		this.eventLoop = eventLoop;
	}

	@FunctionalInterface
	private interface ConnectionConsumer {
		void accept(Connection connection) throws Exception;
	}

	public class ConnectionPool {
		private final String url;
		private final LinkedBlockingQueue<Pair<Long, Connection>> connections = new LinkedBlockingQueue<>();
		private volatile Callback destroycb = null;
		private final AtomicInteger running = new AtomicInteger(0);

		ConnectionPool(String url) {
			this.url = url;
		}

		private void close(Connection c) {
			try {
				c.close();
			} catch (SQLException e) {
			}
		}

		private void _destroy() {
			connections.forEach(pair -> close(pair.getValue()));
			connections.clear();
			destroycb.call(new Object[] {});
		}

		public void destroy(Object callback) {
			if (destroycb != null)
				return;
			destroycb = eventLoop.createCallback(callback);
			if (running.get() == 0)
				_destroy();
		}

		private void runOnConnection(ConnectionConsumer consumer) throws Exception {
			Pair<Long, Connection> pair = connections.poll();
			Connection c = pair == null ? DriverManager.getConnection(url) : pair.getValue();
			try {
				consumer.accept(c);
			} finally {
				if (running.decrementAndGet() == 0 && destroycb != null)
					_destroy();
				else
					connections.offer(new Pair<Long, Connection>(System.currentTimeMillis(), c));
			}
		}

		public int getFadeout() {
			return (int) ConnectionFadeout;
		}

		public void maintance() {
			Pair<Long, Connection> pair;
			long now = System.currentTimeMillis();
			while ((pair = connections.peek()) != null && now - pair.getKey() > ConnectionFadeout) {
				Pair<Long, Connection> cpair = connections.poll();
				if (pair == cpair) {
					close(cpair.getValue());
				} else {
					connections.offer(new Pair<Long, Connection>(now, cpair.getValue()));
					return;
				}
			}
		}

		public void execute(String sql, Object[] args, Object callback) {
			Callback cb = eventLoop.createCallback(callback);
			if (destroycb != null) {
				cb.call(new Exception("Connection destroyed"));
				return;
			}
			running.incrementAndGet();
			eventLoop.execute(() -> {
				try {
					runOnConnection(c -> {
						try (PreparedStatement ps = c.prepareStatement(sql)) {
							for (int i = 0; i < args.length; i++)
								ps.setObject(i + 1, args[i]);
							if (sql.trim().substring(0, 6).toUpperCase().equals("SELECT"))
								try (ResultSet rs = ps.executeQuery()) {
									for (int columnCount = rs.getMetaData().getColumnCount() + 1; rs.next();) {
										Object[] row = new Object[columnCount];
										for (int i = 1; i < columnCount; i++)
											row[i] = rs.getObject(i);
										cb.call(row);
									}
									cb.call(new Object[] { null });
								}
							else
								cb.call(null, ps.executeUpdate());
						}
					});
				} catch (Exception e) {
					cb.call(e);
				}
			});
		}

	}

	public ConnectionPool createConnection(String url) {
		return new ConnectionPool(url);
	}
}
