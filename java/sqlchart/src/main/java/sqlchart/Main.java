package sqlchart;

import com.google.gson.Gson;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static spark.Spark.*;

public class Main {

	private static void usage(String reason) {
		System.out.println(reason);
		System.out.println("Usage: java -jar monitor.jar [options]");
		System.out.println("	-host       default 0.0.0.0");
		System.out.println("	-port       default 80");
		System.out.println("	-configDir  default .");
		Runtime.getRuntime().exit(1);
	}

	public static void main(String[] args) throws Exception {
		String host = "0.0.0.0";
		int port = 80;
		String configDir = ".";

		for (int i = 0; i < args.length; ++i) {
			switch (args[i]) {
			case "-host":
				host = args[++i];
				break;
			case "-port":
				port = Integer.parseInt(args[++i]);
				break;
			case "-configDir":
				configDir = args[++i];
				break;
			default:
				usage("unknown args " + args[i]);
				break;
			}
		}

		Application.initialize(configDir);
		Gson gson = new Gson();

		ipAddress(host);
		port(port);

		staticFileLocation("/public");
		get("/", (request, response) -> {
			response.redirect("/index.html");
			return null;
		});

		exception(Exception.class, (e, request, response) -> {
			e.printStackTrace();
			response.status(404);
			response.type("application/json");
			response.body(gson.toJson(new ResponseCode(e)));
		});

		get("/meta", (request, response) -> {
			response.type("application/json");
			return Application.get(request.queryParams("ds"));
		} , Application.gson::toJson);

		get("/query", (request, response) -> {
			response.type("application/json");
			DataSource ds = Application.get(request.queryParams("ds"));
			Query q = gson.fromJson(request.queryParams("q"), Query.class);
			QueryResult res = new QueryResult();
			res.result = q.doQuery(ds);
			return res;
		} , gson::toJson);

		get("/datasource", (request, response) -> {
			response.type("application/json");
			DataSourceResult res = new DataSourceResult();
			res.dataSources.addAll(Application.dataSources.keySet());
			return res;
		} , gson::toJson);

		post("/datasource", (request, response) -> {
			String dsname = request.queryParams("ds");
			Application.add(dsname);
			response.type("application/json");
			return new ResponseCode("add " + dsname + " ok");
		} , gson::toJson);

		put("/datasource", (request, response) -> {
			response.type("application/json");
			DataSource ds = gson.fromJson(request.body(), DataSource.class);
			Application.update(ds);
			return new ResponseCode("update " + ds.name + " ok");
		} , Application.gson::toJson);

		delete("/datasource", (request, response) -> {
			String dsname = request.queryParams("ds");
			Application.delete(dsname);
			response.type("application/json");
			return new ResponseCode("delete " + dsname + " ok");
		} , gson::toJson);

	}

	static class DataSourceResult {
		final List<String> dataSources = new ArrayList<>();
	}

	static class QueryResult {
		List<List<Object>> result;
	}

	static class ResponseCode {
		public String message;
		public String stack;

		ResponseCode(String message) {
			this.message = message;
		}

		ResponseCode(Exception e) {
			this.message = e.getMessage();
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			this.stack = sw.toString();
		}
	}

}
