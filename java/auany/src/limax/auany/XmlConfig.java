package limax.auany;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import limax.auany.appconfig.AppManager;
import limax.util.ConcurrentEnvironment;
import limax.util.ElementHelper;
import limax.util.Trace;
import limax.xmlconfig.ConfigParser;
import limax.xmlconfig.Service;

public class XmlConfig {
	public interface DataMXBean {
		String getHttpServerIp();

		int getHttpServerPort();

		int getHttpServerPool();
	}

	public static final class LoadConfig implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			ElementHelper eh = new ElementHelper(self);
			String httpServerIp = eh.getString("httpServerIp", "0.0.0.0");
			int httpServerPort = eh.getInt("httpServerPort", 80);
			int httpServerPool = eh.getInt("httpServerPool", 16);
			Map<String, HttpHandler> httphandlers = new HashMap<>();
			Service.addRunAfterEngineStartTask(() -> {
				try {
					final InetSocketAddress sa = new InetSocketAddress(httpServerIp, httpServerPort);
					final HttpServer server = HttpServer.create(sa, 0);
					server.setExecutor(ConcurrentEnvironment.getInstance().newThreadPool("limax.auany.httpServer",
							httpServerPool));
					httphandlers.forEach((context, handler) -> server.createContext(context, handler));
					server.start();
					Service.addRunBeforeEngineStopTask(() -> {
						server.stop(0);
						ConcurrentEnvironment.getInstance().shutdown("limax.auany.httpServer");
					});
				} catch (Exception e) {
					if (Trace.isErrorEnabled())
						Trace.error("start httpServer", e);
				}
			});
			Service.JMXRegister(new DataMXBean() {
				@Override
				public String getHttpServerIp() {
					return httpServerIp;
				}

				@Override
				public int getHttpServerPort() {
					return httpServerPort;
				}

				@Override
				public int getHttpServerPool() {
					return httpServerPool;
				}
			}, "limax.auany:type=XmlConfig,name=httpserver");
			OperationEnvironment.initialize(self, httphandlers);
			LmkManager.initialize(self, httphandlers);
			HttpClientManager.initialize(self, httphandlers);
			Firewall.initialize(self, httphandlers);
			AppManager.initialize(self, httphandlers);
			PlatManager.initialize(self, httphandlers);
			AccountManager.initialize(self, httphandlers);
			PayManager.initialize(self, httphandlers);
			Service.addRunAfterEngineStopTask(() -> {
				PayManager.unInitialize();
				AccountManager.unInitialize();
				HttpClientManager.unInitialize();
				OperationEnvironment.unInitialize();
			});
		}
	}
}
