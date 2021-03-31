package limax.auany.appconfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import org.w3c.dom.Element;

import limax.auany.HttpHelper;
import limax.auany.json.AppInfo;
import limax.auany.json.SwitcherInfo;
import limax.http.HttpHandler;
import limax.net.Engine;
import limax.util.ElementHelper;
import limax.util.MBeans;
import limax.util.Trace;
import limax.xmlconfig.Service;

public class AppManager {
	private volatile static HttpHelper.Cache cache;
	private volatile static Context current;

	private AppManager() {
	}

	public static void updateJSON(int pvid, String json) {
		App.updateJSON(current, pvid, json);
	}

	public static int getMaxSubordinates(int appid) {
		return App.getMaxSubordinates(current, appid);
	}

	public static SwitcherInfo randomSwitcher(ServiceType type, int appid) {
		return App.randomSwitcher(current, type, appid);
	}

	public static Integer checkAppId(Set<Integer> pvids) {
		return App.checkAppId(current, pvids);
	}

	public static boolean verifyProviderKey(int pvid, String key) {
		return App.verifyProviderKey(current, pvid, key);
	}

	public static void providerUp(int pvid, boolean paySupport, Set<Integer> switcherIds) {
		App.updateProvider(current, pvid, paySupport ? Provider.Status.ON_PAY : Provider.Status.ON, switcherIds);
	}

	public static void providerDown(Integer pvid, Set<Integer> switcherIds) {
		App.updateProvider(current, pvid, Provider.Status.OFF, switcherIds);
	}

	public static long initProvider(int pvid, String json) {
		return App.initProvider(current, pvid, json);
	}

	public static String updateSwitcher(String host, String key, ArrayList<Integer> nativeIds, ArrayList<Integer> wsIds,
			ArrayList<Integer> wssIds) {
		return App.updateSwitcher(current, host, key, nativeIds, wsIds, wssIds);
	}

	static void flushCache(int appid) {
		cache.remove(new AppKey(ServiceType.NATIVE, appid));
		cache.remove(new AppKey(ServiceType.WS, appid));
		cache.remove(new AppKey(ServiceType.WSS, appid));
	}

	private static Future<?> future;
	private static boolean closed = false;
	private final static ReentrantLock lock = new ReentrantLock();

	private static void doPatch(Path path) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(baos, "UTF-8"));
		try {
			Context ctx = current.merge(path);
			String message = ctx.getErrorMessage();
			if (message.isEmpty()) {
				ctx.save(path.resolveSibling("appconfig.xml"));
				current = ctx;
				pw.println("---------- success ----------");
			} else {
				pw.println("---------- merge error ----------");
				pw.println(message);
			}
		} catch (Exception e) {
			pw.println("---------- exception ----------");
			e.printStackTrace(pw);
		} finally {
			pw.flush();
			Path respath = path.resolveSibling(path.getFileName() + ".result");
			Files.move(path, respath, StandardCopyOption.REPLACE_EXISTING);
			Files.write(respath, baos.toByteArray(), StandardOpenOption.APPEND);
		}
	}

	private static void patchTask(Path path, long period, long size) {
		lock.lock();
		try {
			if (closed)
				return;
			future = Engine.getProtocolScheduler().schedule(() -> {
				try {
					if (Files.size(path) == size)
						doPatch(path);
				} catch (Exception e) {
					if (Trace.isErrorEnabled())
						Trace.error("AppManager.PatchTask", e);
				} finally {
					installPatchTask(path, period);
				}
			}, 1000, TimeUnit.MILLISECONDS);
		} finally {
			lock.unlock();
		}
	}

	private static void installPatchTask(Path path, long period) {
		lock.lock();
		try {
			if (closed)
				return;
			future = Engine.getProtocolScheduler().schedule(() -> {
				try {
					if (Files.isWritable(path))
						patchTask(path, period, Files.size(path));
				} catch (Exception e) {
					if (Trace.isErrorEnabled())
						Trace.error("AppManager.PatchTask", e);
				} finally {
					installPatchTask(path, period);
				}
			}, period, TimeUnit.MILLISECONDS);
		} finally {
			lock.unlock();
		}
	}

	public static AppInfo getAppInfo(int appid, ServiceType type) {
		final Context current = AppManager.current;
		if (null == current)
			return null;
		App app = current.appMap.get(appid);
		if (null == app)
			return null;
		return app.createInfo(type);
	}

	public static void initialize(Element self, BiConsumer<String, HttpHandler> httphandlers) throws Exception {
		ElementHelper eh = new ElementHelper(self);
		current = App.load((Element) self.getElementsByTagName("appconfig").item(0), new Context());
		String message = current.getErrorMessage();
		if (!message.isEmpty())
			throw new Exception(message);
		httphandlers.accept("/app",
				HttpHelper.createHttpHandler(cache = HttpHelper.makeJSONCache(HttpHelper.uri2AppKey("/app"),
						appkey -> current.appMap.get(appkey.getAppId()).createInfo(appkey.getType()))));
		Path patchPath = Service.getConfigParentFile().toPath().resolve(eh.getString("configPatch", "appnew.xml"));
		long patchPeriod = eh.getLong("patchCheckPeriod", 30000l);
		MBeans.register(MBeans.root(), new AppManagerMXBean() {
			@Override
			public void setServiceOptional(int appid, int serviceid, String optional) {
				App.setServiceOptional(current, appid, serviceid, optional);
			}
		}, "limax.auany:type=appconfig,name=AppManager");
		Service.addRunAfterEngineStartTask(() -> installPatchTask(patchPath, patchPeriod));
		Service.addRunBeforeEngineStopTask(() -> {
			lock.lock();
			try {
				closed = true;
				if (future != null)
					future.cancel(false);
			} finally {
				lock.unlock();
			}
		});
		eh.warnUnused("parserClass");
	}

}
