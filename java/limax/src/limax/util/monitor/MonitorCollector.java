package limax.util.monitor;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import limax.sql.SQLExecutor;

public class MonitorCollector implements CollectorController {
	private interface RecordHandler {
		void fireOnRecord(String host, Map<String, String> keys, Map<String, Object> item) throws Exception;
	}

	private static class DBCollector {
		private final SQLExecutor executor;
		private final Map<String, Method> map = new ConcurrentHashMap<>();

		DBCollector(SQLExecutor executor) {
			this.executor = executor;
		}

		void record(String classname, String host, Map<String, String> keys, Map<String, Object> item)
				throws Exception {
			Method m = map.get(classname);
			String sqlcreate;
			if (m == null) {
				Class<?> clazz = Class.forName(classname + "$Collector");
				sqlcreate = (String) clazz.getMethod("getCreateTableString").invoke(null);
				map.put(classname, m = clazz.getMethod("createInsertStatement", Connection.class, String.class,
						Map.class, Map.class));
			} else
				sqlcreate = null;
			Method method = m;
			executor.execute(conn -> {
				if (sqlcreate != null) {
					try (Statement st = conn.createStatement()) {
						st.execute(sqlcreate);
					} catch (Exception e) {
					}
				}
				try (PreparedStatement ps = (PreparedStatement) method.invoke(null, conn, host, keys, item)) {
					ps.execute();
				}
			});
		}
	}

	private final CollectorController controller;
	private final Map<String, RecordHandler> collectorMap = new ConcurrentHashMap<>();
	private final BiConsumer<String, Exception> onexception;
	private final AtomicReference<DBCollector> dbc = new AtomicReference<>();

	public MonitorCollector() {
		this.onexception = (h, e) -> {
		};
		this.controller = start();
	}

	public MonitorCollector(BiConsumer<String, Exception> onexception) {
		this.onexception = onexception;
		this.controller = start();
	}

	private static String unquote(String s) {
		if (s.charAt(0) != '"')
			return s;
		StringBuilder sb = new StringBuilder();
		char c[] = s.toCharArray();
		for (int i = 1, j = c.length - 1; i < j; i++) {
			if (c[i] == '\\')
				if (c[++i] == 'n') {
					sb.append('\n');
					continue;
				}
			sb.append(c[i]);
		}
		return sb.toString();
	}

	private Map<String, String> objectName2KeyMap(ObjectName objname) {
		return objname.getKeyPropertyList().entrySet().stream()
				.collect(Collectors.toMap(e -> e.getKey(), e -> unquote(e.getValue())));
	}

	private CollectorController start() {
		return Monitor.start(new Collector() {

			@Override
			public void onController(CollectorController controller) {
			}

			@Override
			public void onRecord(String host, ObjectName objname, Map<String, Object> item) {
				try {
					String classname = objname.getDomain();
					RecordHandler handler = collectorMap.get(classname);
					DBCollector dbc = MonitorCollector.this.dbc.get();
					if (handler != null || dbc != null) {
						Map<String, String> keys = objectName2KeyMap(objname);
						if (handler != null) {
							try {
								handler.fireOnRecord(host, keys, item);
							} catch (Exception e) {
								onException(host, e);
							}
						}
						if (dbc != null)
							dbc.record(classname, host, keys, item);
					} else
						onException(host,
								new NullPointerException("Absent collector instance. ObjectName = " + objname));
				} catch (Exception e) {
					onException(host, e);
				}
			}

			@Override
			public void onException(String host, Exception e) {
				onexception.accept(host, e);
			}
		});
	}

	@Override
	public Runnable addHost(String host, String url, String username, String password) throws MalformedURLException {
		return controller.addHost(host, url, username, password);
	}

	@Override
	public Runnable addCollector(String host, String pattern, long period) throws MalformedObjectNameException {
		return controller.addCollector(host, pattern, period);
	}

	@Override
	public void stop() {
		controller.stop();
	}

	private static void extractInterface(Set<Class<?>> set, Class<?> clazz) {
		for (Class<?> c : clazz.getInterfaces())
			if (set.add(c))
				extractInterface(set, c);
	}

	private static Set<Class<?>> extractInterface(Class<?> clazz) {
		Set<Class<?>> set = new HashSet<>();
		extractInterface(set, clazz);
		return set;
	}

	public Runnable addCollectorInstance(Object collector) throws Exception {
		Class<?> clazz = extractInterface(collector.getClass()).stream().filter(i -> i.getName().endsWith("$Collector"))
				.reduce((a, b) -> Void.class).filter(c -> c != Void.class)
				.orElseThrow(() -> new IllegalArgumentException("Bad collector instance"));
		Method methodSorted = clazz.getMethod("sortAsRecord", Map.class, Map.class);
		Method methodRecord = Arrays.stream(clazz.getMethods()).filter(m -> m.getName().equals("onRecord"))
				.reduce((a, b) -> methodSorted).filter(c -> c != methodSorted)
				.orElseThrow(() -> new IllegalArgumentException("Bad collector instance"));
		String className = clazz.getName().replace("$Collector", "");
		RecordHandler handler = (host, keys, item) -> methodRecord.invoke(collector, Stream
				.concat(Stream.of(host), Arrays.stream((Object[]) methodSorted.invoke(null, keys, item))).toArray());
		if (collectorMap.putIfAbsent(className, handler) != null)
			throw new IllegalArgumentException("Collector instance of " + clazz.getName() + " has already been added.");
		return () -> collectorMap.remove(className, handler);
	}

	public Runnable addSQLExecutor(SQLExecutor executor) {
		DBCollector dbc = new DBCollector(executor);
		if (!this.dbc.compareAndSet(null, dbc))
			throw new IllegalArgumentException("DBConnection has already been added.");
		return () -> this.dbc.compareAndSet(dbc, null);
	}

	public static String formatJMXServiceURLString(String host, int serverPort, int rmiPort) {
		return String.format("service:jmx:rmi://%1$s:%2$d/jndi/rmi://%1$s:%3$d/jmxrmi", host, serverPort, rmiPort);
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Class<?>> getKeyTypesByObjectName(ObjectName objname) throws Exception {
		return (Map<String, Class<?>>) Class.forName(objname.getDomain() + "$Collector").getMethod("getKeyTypes")
				.invoke(null);
	}
}
