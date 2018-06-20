package sqlchart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.eclipse.jetty.io.RuntimeIOException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Application {
	public static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	public static final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();
	private static Path configDirectory;

	public static void initialize(String configDir) throws IOException, SQLException {
		configDirectory = Paths.get(configDir);
		File[] files = configDirectory.toFile().listFiles();
		if (files == null)
			throw new IllegalArgumentException("configDir listFiles failed");

		for (File file : files) {
			if (file.isFile() && file.getName().endsWith(".json")) {
				try (Reader in = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
					DataSource ds = gson.fromJson(in, DataSource.class);
					if (null != dataSources.put(ds.name, ds))
						throw new IllegalStateException(ds + " name duplicated");
					ds.connectionPool = new SqlConnectionPool(ds.url, ds.poolSize);
				}
			}
		}
		int interval = Integer.getInteger("sqlchart.UpdateMetaInterval", 3);
		Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(Application::updateMeta, 0, interval,
				TimeUnit.SECONDS);
	}

	private static void updateOneMeta(DataSource ds) {
		Connection conn = ds.connectionPool.getConnection();
		try {
			ds.alive = conn.isValid(1);
			if (ds.alive) {
				DatabaseMetaData meta = conn.getMetaData();
				List<String> sqlTableNames = new ArrayList<>();
				try (ResultSet rs = meta.getTables(null, null, null, null)) {
					while (rs.next()) {
						sqlTableNames.add(rs.getString(3));
					}
				}

				List<Table> newTables = new ArrayList<>();
				Table[] oldTables = ds.tables;
				if (oldTables != null) {
					for (Table ot : oldTables) {
						ot.exist = false;
						newTables.add(ot);
					}
				}

				for (String tn : sqlTableNames) {
					Optional<Table> opt = newTables.stream().filter(v -> v.table.equals(tn)).findAny();
					Table t;
					if (opt.isPresent()) {
						t = opt.get();
					} else {
						t = new Table(tn);
						newTables.add(t);
					}

					t.exist = true;
					try (ResultSet rs = meta.getColumns(null, null, tn, null)) {
						List<Column> sqlCols = new ArrayList<>();
						while (rs.next()) {
							String col = rs.getString(4);
							DataType dt = DataType.of(rs.getInt(5));
							if (dt != null)
								sqlCols.add(new Column(col, dt));
						}
						t.columns = sqlCols.toArray(new Column[sqlCols.size()]);
					}
				}

				ds.tables = newTables.toArray(new Table[newTables.size()]);
			}
			save(ds);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			ds.connectionPool.freeConnection(conn);
		}
	}

	private static synchronized void updateMeta() {
		dataSources.values().forEach(sqlchart.Application::updateOneMeta);
	}

	public static DataSource get(String dsname) {
		DataSource ds = dataSources.get(dsname);
		return has(ds, "DataSource", dsname);
	}

	public static synchronized void add(String name) {
		if (name == null || name.isEmpty())
			throw new IllegalStateException("name empty");
		DataSource ds = new DataSource(name,
				"jdbc:mysql://localhost:3306/" + name + "?user=root&password=admin&autoReconnect=true", 3);
		notHas(dataSources.putIfAbsent(name, ds), "DataSource", ds.name);
		ds.connectionPool = new SqlConnectionPool(ds.url, ds.poolSize);
		updateOneMeta(ds);
		save(ds);
	}

	public static synchronized void update(DataSource ds) {
		DataSource oldds = has(dataSources.replace(ds.name, ds), "DataSource", ds.name);
		oldds.connectionPool.close();
		ds.connectionPool = new SqlConnectionPool(ds.url, ds.poolSize);
		updateOneMeta(ds);
		save(ds);
	}

	public static synchronized void delete(String name) {
		DataSource ds = has(dataSources.remove(name), "DataSource", name);
		ds.connectionPool.close();
		try {
			Files.delete(configDirectory.resolve(name + ".json"));
		} catch (IOException e) {
			throw new RuntimeIOException(e);
		}
	}

	private static void save(DataSource ds) {
		try (Writer out = new OutputStreamWriter(
				new FileOutputStream(configDirectory.resolve(ds.name + ".json").toFile()), "UTF-8")) {
			out.write(gson.toJson(ds));
		} catch (IOException e) {
			throw new RuntimeIOException(e);
		}
	}

	private static <T> T has(T obj, String tag, String name) {
		if (obj == null)
			throw new IllegalStateException(tag + "[" + name + "] not exist");
		return obj;
	}

	private static void notHas(Object obj, String tag, String name) {
		if (obj != null)
			throw new IllegalStateException(tag + "[" + name + "] already exist");
	}

}
