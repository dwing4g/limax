package limax.node.js.modules;

import java.util.Map;
import java.util.WeakHashMap;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import limax.node.js.EventLoop;
import limax.node.js.Module;

public final class Vm implements Module {
	private final ScriptEngine engine;
	private final Map<Bindings, ScriptContext> map = new WeakHashMap<>();

	public Vm(EventLoop eventLoop) {
		this.engine = eventLoop.getEngine();
	}

	public class Script {
		private final String code;

		Script(String code) {
			this.code = code;
		}

		public Object runInContext(Bindings contextifiedSandbox) throws ScriptException {
			ScriptContext ctx = map.get(contextifiedSandbox);
			if (ctx == null)
				throw new ScriptException("sandbox argument must have been converted to a context.");
			ScriptContext save = engine.getContext();
			try {
				engine.setContext(ctx);
				Object obj = engine.eval(code);
				Bindings tmpglobal = (Bindings) contextifiedSandbox.get("nashorn.global");
				if (tmpglobal != null)
					contextifiedSandbox.putAll(tmpglobal);
				return obj;
			} finally {
				engine.setContext(save);
			}

		}

		public Object runInNewContext(Bindings sandbox) throws ScriptException {
			return runInContext(createContext(sandbox));
		}

		public Object runInThisContext() throws ScriptException {
			return engine.eval(code);
		}
	}

	public Bindings createContext(Bindings sandbox) {
		if (sandbox == null)
			sandbox = engine.createBindings();
		ScriptContext ctx = new SimpleScriptContext();
		ctx.setBindings(sandbox, ScriptContext.ENGINE_SCOPE);
		ctx.setBindings(engine.getBindings(ScriptContext.GLOBAL_SCOPE), ScriptContext.GLOBAL_SCOPE);
		map.put(sandbox, ctx);
		return sandbox;
	}

	public boolean isContext(Bindings sandbox) {
		return map.containsKey(sandbox);
	}

	public Script createScript(String code) {
		return new Script(code);
	}
}
