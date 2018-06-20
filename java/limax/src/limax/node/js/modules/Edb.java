package limax.node.js.modules;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import limax.edb.DataBase;
import limax.edb.Environment;
import limax.edb.QueryData;
import limax.node.js.Buffer;
import limax.node.js.EventLoop;
import limax.node.js.EventLoop.Callback;
import limax.node.js.EventLoop.EventObject;
import limax.node.js.Module;

public final class Edb implements Module {
	private final EventLoop eventLoop;

	public Edb(EventLoop eventLoop) {
		this.eventLoop = eventLoop;
	}

	public class Instance {
		private final DataBase edb;
		private final EventObject evo;
		private volatile Runnable destroycb;
		private final AtomicInteger running = new AtomicInteger(0);

		Instance(String path) throws Exception {
			this.edb = new DataBase(new Environment(), Paths.get(path));
			this.evo = eventLoop.createEventObject();
		}

		public void destroy(Object callback) {
			if (destroycb != null)
				return;
			destroycb = () -> eventLoop.execute(callback, r -> {
				evo.queue();
				edb.close();
			});
			if (running.get() == 0)
				destroycb.run();
		}

		private boolean enter(Object callback) {
			if (destroycb != null) {
				eventLoop.createCallback(callback).call(new Exception("Instance destroyed"));
				return false;
			}
			running.incrementAndGet();
			return true;
		}

		private void leave() {
			if (running.decrementAndGet() == 0 && destroycb != null)
				destroycb.run();
		}

		public void addTable(Object[] tables, Object callback) {
			if (enter(callback))
				eventLoop.execute(callback, r -> {
					try {
						edb.addTable(Arrays.stream(tables).toArray(String[]::new));
					} finally {
						leave();
					}
				});
		}

		public void removeTable(Object[] tables, Object callback) {
			if (enter(callback))
				eventLoop.execute(callback, r -> {
					try {
						edb.removeTable(Arrays.stream(tables).toArray(String[]::new));
					} finally {
						leave();
					}
				});
		}

		public void insert(String table, Buffer key, Buffer value, Object callback) {
			if (enter(callback))
				eventLoop.execute(callback, r -> {
					try {
						r.add(edb.insert(table, key.toByteArray(), value.toByteArray()));
					} finally {
						leave();
					}
				});
		}

		public void replace(String table, Buffer key, Buffer value, Object callback) {
			if (enter(callback))
				eventLoop.execute(callback, r -> {
					try {
						edb.replace(table, key.toByteArray(), value.toByteArray());
					} finally {
						leave();
					}
				});
		}

		public void remove(String table, Buffer key, Object callback) {
			if (enter(callback))
				eventLoop.execute(callback, r -> {
					try {
						edb.remove(table, key.toByteArray());
					} finally {
						leave();
					}
				});
		}

		public void find(String table, Buffer key, Object callback) {
			if (enter(callback))
				eventLoop.execute(callback, r -> {
					try {
						byte[] data = edb.find(table, key.toByteArray());
						r.add(data != null ? new Buffer(data) : null);
					} finally {
						leave();
					}
				});
		}

		public void exist(String table, Buffer key, Object callback) {
			if (enter(callback))
				eventLoop.execute(callback, r -> {
					try {
						r.add(edb.exist(table, key.toByteArray()));
					} finally {
						leave();
					}
				});
		}

		public void walk(String table, Buffer key, Object callback) {
			if (enter(callback)) {
				Callback cb = eventLoop.createCallback(callback);
				eventLoop.execute(() -> {
					try {
						QueryData query = new QueryData() {
							@Override
							public boolean update(byte[] key, byte[] value) {
								cb.call(null, new Buffer(key), new Buffer(value));
								return true;
							}
						};
						if (key == null)
							edb.walk(table, query);
						else
							edb.walk(table, key.toByteArray(), query);
					} catch (Exception e) {
						cb.call(e);
					} finally {
						leave();
					}
				});
			}
		}
	}

	public Instance createEdb(String path) throws Exception {
		return new Instance(path);
	}
}
