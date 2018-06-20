package limax.endpoint.script;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.script.Invocable;
import javax.script.ScriptEngine;

import limax.codec.Base64Encode;
import limax.codec.Octets;
import limax.endpoint.AuanyService;
import limax.endpoint.ProviderLoginDataManager;

public class JavaScriptHandle implements ScriptEngineHandle {
	private final DictionaryCache cache;
	private final ScriptEngine engine;
	private final Invocable invoker;
	private final Set<Integer> providers = new HashSet<Integer>();
	private volatile LmkDataReceiver lmkDataReceiver;

	@SuppressWarnings("unchecked")
	public JavaScriptHandle(ScriptEngine engine, Reader init, Collection<Integer> providers, DictionaryCache cache,
			TunnelReceiver ontunnel) throws Exception {
		try (Reader reader = new InputStreamReader(getClass().getResourceAsStream("map.js"))) {
			engine.eval(reader);
		}
		try (Reader reader = new InputStreamReader(getClass().getResourceAsStream("limax.js"))) {
			engine.eval(reader);
		}
		this.engine = engine;
		this.invoker = (Invocable) engine;
		engine.eval(
				"function sender(obj){ limax(0, function(s) { var r = obj.send(s); return r ? r : undefined; }); }");
		engine.eval(
				"function ontunnel(obj){ ontunnel = function(pvid, label, data) { obj.onTunnel(pvid, label, data); } }");
		engine.put("cache", this.cache = cache != null ? cache : new SimpleDictionaryCache());
		invoker.invokeFunction("ontunnel", new TunnelReceiver() {
			@Override
			public void onTunnel(int providerid, int label, String data) {
				if (providerid == AuanyService.providerId) {
					try {
						if (lmkDataReceiver != null)
							lmkDataReceiver.onLmkData(data, new Runnable() {
								@Override
								public void run() {
									try {
										tunnel(AuanyService.providerId, -1, "");
									} catch (Exception e) {
									}
								}
							});
					} catch (Exception e) {
					}
				} else if (ontunnel != null)
					ontunnel.onTunnel(providerid, label, data);
			}
		});
		engine.eval(init);
		if (providers == null || providers.isEmpty()) {
			Object o = engine.get("providers");
			if (!(o instanceof Map))
				throw new RuntimeException("init script must set var providers = [pvid0,..];");
			for (Object v : ((Map<Integer, Object>) o).values())
				this.providers.add((Integer) v);
		} else {
			for (Integer v : providers)
				this.providers.add(v);
		}
	}

	public JavaScriptHandle(ScriptEngine engine, Reader init, Collection<Integer> providers, DictionaryCache cache)
			throws Exception {
		this(engine, init, providers, cache, null);
	}

	public JavaScriptHandle(ScriptEngine engine, Reader init, DictionaryCache cache) throws Exception {
		this(engine, init, null, cache, null);
	}

	public JavaScriptHandle(ScriptEngine engine, Reader init, Collection<Integer> providers) throws Exception {
		this(engine, init, providers, null, null);
	}

	public JavaScriptHandle(ScriptEngine engine, Reader init) throws Exception {
		this(engine, init, null, null, null);
	}

	@Override
	public Set<Integer> getProviders() {
		return providers;
	}

	@Override
	public int action(int t, Object p) throws Exception {
		synchronized (engine) {
			return (int) invoker.invokeFunction("limax", t, p);
		}
	}

	@Override
	public void registerScriptSender(ScriptSender sender) throws Exception {
		synchronized (engine) {
			invoker.invokeFunction("sender", sender);
		}
	}

	@Override
	public void registerLmkDataReceiver(LmkDataReceiver receiver) {
		this.lmkDataReceiver = receiver;
	}

	@Override
	public void registerProviderLoginManager(ProviderLoginDataManager pldm) throws Exception {
		StringBuilder sb = new StringBuilder("var __logindatas = {};\n");
		for (int pvid : pldm.getProviderIds()) {
			String data = new String(Base64Encode.transform(pldm.getData(pvid).getBytes()));
			sb.append("__logindatas[" + pvid + "] = { data : '" + data + "', ");
			sb.append(pldm.isSafe(pvid) ? "label : " + pldm.getLabel(pvid) + "};\n" : "base64 : 1 };\n");
		}
		sb.append("limax(__logindatas);\n");
		synchronized (engine) {
			engine.eval(sb.toString());
		}
	}

	@Override
	public DictionaryCache getDictionaryCache() {
		return cache;
	}

	@Override
	public void tunnel(int providerid, int label, String data) throws Exception {
		synchronized (engine) {
			invoker.invokeFunction("limax", providerid, label, data);
		}
	}

	@Override
	public void tunnel(int providerid, int label, Octets data) throws Exception {
		tunnel(providerid, label, new String(Base64Encode.transform(data.getBytes())));
	}
}
