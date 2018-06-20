package limax.node.js;

import java.nio.file.Paths;
import java.util.Arrays;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import limax.util.Trace;

public final class Main {

	public static void launchEngine(String mainmodule, Object[] parameters) throws Exception {
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
		engine.put("global", engine.getBindings(ScriptContext.ENGINE_SCOPE));
		Buffer.load(engine);
		EventLoop eventLoop = new EventLoop(engine);
		Require require = new Require(eventLoop);
		require.require("process");
		require.require("console");
		require.require("timer");
		require.require(mainmodule, parameters);
		eventLoop.launch();
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.out.println("usage: java -jar limax.jar node <module> [parameters]");
		} else {
			Trace.openNew(null, true, 0, 0, 0);
			launchEngine(Paths.get(args[0]).toAbsolutePath().toString(), Arrays.copyOfRange(args, 1, args.length));
		}
	}
}
