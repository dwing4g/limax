package limax.auany;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.w3c.dom.Element;

import limax.auany.appconfig.AppManager;
import limax.http.HttpHandler;
import limax.http.HttpServer;
import limax.util.ElementHelper;
import limax.util.Trace;
import limax.xmlconfig.ConfigParser;
import limax.xmlconfig.Service;

public final class XmlConfig {

	public interface DataMXBean {
		String getHttpServerIp();

		int getHttpServerPort();
	}

	public static final class HttpServerConfig implements ConfigParser {

		private static HttpServerConfig instance;

		public HttpServerConfig() {
			instance = this;
		}

		private final Map<String, HttpHandler> httphandlers = new HashMap<>();

		@Override
		public void parse(Element self) throws Exception {
			ElementHelper eh = new ElementHelper(self);
			String httpServerIp = eh.getString("httpServerIp", "0.0.0.0");
			int httpServerPort = eh.getInt("httpServerPort", 80);
			Service.addRunAfterEngineStartTask(() -> {
				try {
					final InetSocketAddress sa = new InetSocketAddress(httpServerIp, httpServerPort);
					final HttpServer server = HttpServer.create(sa);
					httphandlers.forEach((context, handler) -> server.createContext(context, handler));
					server.start();
					Service.addRunBeforeEngineStopTask(() -> {
						try {
							server.stop();
						} catch (IOException e) {
						}
					});
					httphandlers.clear();
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
			}, "limax.auany:type=XmlConfig,name=httpserver");
			eh.warnUnused("parserClass");
		}
	}

	private static BiConsumer<String, HttpHandler> getHttpService() throws Exception {
		final HttpServerConfig instance = HttpServerConfig.instance;
		if (null == instance)
			throw new Exception("Please config http server first");
		return instance.httphandlers::put;
	}

	public static final class OperationEnvironmentConfig implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			OperationEnvironment.initialize(self, getHttpService());
			Service.addRunAfterEngineStopTask(OperationEnvironment::unInitialize);
		}
	}

	public static final class LmkManagerConfig implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			LmkManager.initialize(self, getHttpService());
		}
	}

	public static final class HttpClientConfig implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			HttpClientManager.initialize(self, getHttpService());
			Service.addRunAfterEngineStopTask(HttpClientManager::unInitialize);
		}
	}

	public static final class FirewallConfig implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			Firewall.initialize(self, getHttpService());
		}
	}

	public static final class AppManagerConfig implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			AppManager.initialize(self, getHttpService());
		}
	}

	public static final class PlatManagerConfig implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			PlatManager.initialize(self, getHttpService());
		}
	}

	public static final class AccountManagerConfig implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			AccountManager.initialize(self, getHttpService());
			Service.addRunAfterEngineStopTask(AccountManager::unInitialize);
		}
	}

	public static final class PayManagerConfig implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			PayManager.initialize(self, getHttpService());
			Service.addRunAfterEngineStopTask(PayManager::unInitialize);
		}
	}
}
