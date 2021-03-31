package limax.auany;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.net.ssl.SSLContext;

import org.w3c.dom.Element;

import limax.auany.appconfig.AppManager;
import limax.http.HttpHandler;
import limax.http.HttpServer;
import limax.pkix.KeyInfo;
import limax.util.ElementHelper;
import limax.util.Trace;
import limax.util.XMLUtils;
import limax.xmlconfig.ConfigParser;
import limax.xmlconfig.Service;

public final class XmlConfig {

	public interface DataMXBean {
		String getHttpServerIp();

		int getHttpServerPort();

		int getHttpsServerPort();
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

			int _httpsServerPort = eh.getInt("httpsServerPort", 0);
			final URI pkcs12url;
			final char[] passphrase;
			if (_httpsServerPort > 0) {
				final String pkcs12file = eh.getString("pkcs12file", "");
				if (pkcs12file.isEmpty()) {
					_httpsServerPort = 0;
					pkcs12url = null;
					passphrase = null;
				} else {
					pkcs12url = new URI("pkcs12:" + Paths.get(pkcs12file).toUri().getSchemeSpecificPart());
					passphrase = eh.getString("passphrase", "").toCharArray();
				}
			} else {
				pkcs12url = null;
				passphrase = null;
			}
			final int httpsServerPort = _httpsServerPort;

			for (Element e : XMLUtils.getChildElements(self)) {
				final ElementHelper sub = new ElementHelper(e);
				final String path = sub.getString("path");
				final String classname = sub.getString("class");
				try {
					final Class<?> cls = Class.forName(classname);
					final HttpHandler handler = (HttpHandler) cls.newInstance();
					httphandlers.put(path, handler);
				} catch (Exception ex) {
					Trace.warn("HttpServerConfig load " + path, ex);
				}
				sub.warnUnused();
			}

			Service.addRunAfterEngineStartTask(() -> {
				try {
					if (httpServerPort > 0) {
						final HttpServer server = HttpServer
								.create(new InetSocketAddress(httpServerIp, httpServerPort));
						httphandlers.forEach((context, handler) -> server.createContext(context, handler));
						server.start();
						Service.addRunBeforeEngineStopTask(() -> {
							try {
								server.stop();
							} catch (IOException e) {
							}
						});
					}
					if (httpsServerPort > 0) {
						final SSLContext sslContext = KeyInfo.load(pkcs12url, prompt -> passphrase)
								.createSSLContext(null, false, null);
						final HttpServer server = HttpServer
								.create(new InetSocketAddress(httpServerIp, httpsServerPort), sslContext);
						httphandlers.forEach((context, handler) -> server.createContext(context, handler));
						server.start();
						Service.addRunBeforeEngineStopTask(() -> {
							try {
								server.stop();
							} catch (IOException e) {
							}
						});
					}
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

				@Override
				public int getHttpsServerPort() {
					return httpsServerPort;
				}
			}, "limax.auany:type=XmlConfig,name=httpserver");
			eh.warnUnused("parserClass", "pkcs12file", "passphrase");
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
