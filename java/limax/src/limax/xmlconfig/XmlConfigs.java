package limax.xmlconfig;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.w3c.dom.Element;

import limax.net.ClientListener;
import limax.net.ClientManagerConfig;
import limax.net.Config;
import limax.net.Engine;
import limax.net.Listener;
import limax.net.Manager;
import limax.net.ServerListener;
import limax.net.ServerManagerConfig;
import limax.net.State;
import limax.net.Transport;
import limax.provider.GlobalId;
import limax.provider.globalid.GlobalIdListener;
import limax.util.Dispatcher;
import limax.util.ElementHelper;
import limax.util.Helper;
import limax.util.Limit;
import limax.util.MBeanServer;
import limax.util.Trace;
import limax.util.XMLUtils;

public class XmlConfigs {
	private static void launchManagerTask(ConfigBuilder builder, Listener listener, String name) {
		try {
			final Manager manager = Engine.add(builder.build(), listener);
			if (Trace.isInfoEnabled())
				Trace.info("Manager " + manager + " opened!");
		} catch (Exception e) {
			if (Trace.isErrorEnabled())
				Trace.error("Engine.getInstance().add " + name, e);
			throw new RuntimeException(e);
		}
	}

	public interface ManagerConfigMXBean {
		String getName();

		int getInputBufferSize();

		int getOutputBufferSize();

		boolean isCheckOutputBuffer();

		String getInputSecurity();

		String getOutputSecurity();

		boolean isInputCompress();

		boolean isOutputCompress();

		boolean isAsynchronous();
	}

	private XmlConfigs() {
	}

	private static int roundup(int size) {
		int capacity = 16;
		while (size > capacity)
			capacity <<= 1;
		return capacity;
	}

	public interface ServerManagerConfigMXBean extends ManagerConfigMXBean {
		String getLocalAddressString();

		int getBacklog();
	}

	public interface ClientManagerConfigMXBean extends ManagerConfigMXBean {
		String getRemoteaddrString();

		boolean isAutoreconnect();
	}

	public static class ServerManagerConfigXmlBuilder extends ServerManagerConfigBuilder implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			final ElementHelper eh = new ElementHelper(self);
			String name = eh.getString("name");
			name(name);
			inputBufferSize(roundup(eh.getInt("inputBufferSize", 16384)));
			outputBufferSize(roundup(eh.getInt("outputBufferSize", 16384)));
			checkOutputBuffer(eh.getBoolean("checkOutputBuffer", false));
			inputSecurity(eh.getHexBytes("inputSecurity"));
			outputSecurity(eh.getHexBytes("outputSecurity"));
			inputCompress(eh.getBoolean("inputCompress", false));
			outputCompress(eh.getBoolean("outputCompress", false));
			String listenaddr = eh.getString("localIp");
			int listenport = eh.getInt("localPort");
			InetSocketAddress localAddress = listenaddr.isEmpty() ? new InetSocketAddress(listenport)
					: new InetSocketAddress(listenaddr, listenport);
			localAddress(localAddress);
			backlog(eh.getInt("backlog", 32));
			limit(Limit.get(eh.getString("limit")));
			autoListen(eh.getBoolean("autoStartListen", true));
			boolean enabled = eh.getBoolean("webSocketEnabled", false);
			webSocketEnabled(enabled);
			asynchronous(eh.getBoolean("asynchronous", false));
			if (enabled) {
				String keyStoreFile = eh.getString("keyStore", "");
				if (keyStoreFile.length() > 0)
					try {
						char[] password = eh.getString("password").toCharArray();
						KeyStore keyStore = KeyStore.getInstance("PKCS12");
						try (InputStream in = new FileInputStream(keyStoreFile)) {
							keyStore.load(in, password);
						}
						KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
						keyManagerFactory.init(keyStore, password);
						SSLContext sslContext = SSLContext.getInstance("TLS");
						sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
						sslContext(sslContext);
					} catch (Exception e) {
						if (Trace.isFatalEnabled())
							Trace.fatal("load SSLContext fail [" + keyStoreFile + "]", e);
					}
			}
			if (!name.isEmpty())
				Service.JMXRegister(new ServerManagerConfigMXBean() {
					private final ServerManagerConfig config = build();

					@Override
					public String getName() {
						return config.getName();
					}

					@Override
					public int getInputBufferSize() {
						return config.getInputBufferSize();
					}

					@Override
					public int getOutputBufferSize() {
						return config.getOutputBufferSize();
					}

					@Override
					public boolean isCheckOutputBuffer() {
						return config.isCheckOutputBuffer();
					}

					@Override
					public String getInputSecurity() {
						return config.getInputSecurityBytes() == null ? ""
								: Helper.toHexString(config.getInputSecurityBytes());
					}

					@Override
					public String getOutputSecurity() {
						return config.getOutputSecurityBytes() == null ? ""
								: Helper.toHexString(config.getOutputSecurityBytes());
					}

					@Override
					public boolean isInputCompress() {
						return config.isInputCompress();
					}

					@Override
					public boolean isOutputCompress() {
						return config.isOutputCompress();
					}

					@Override
					public String getLocalAddressString() {
						return config.getLocalAddress().toString();
					}

					@Override
					public int getBacklog() {
						return config.getBacklog();
					}

					@Override
					public boolean isAsynchronous() {
						return config.isAsynchronous();
					}
				}, "limax.xmlconfig:type=XmlConfigs,name=server-" + name + "_"
						+ localAddress.toString().replace(':', '_'));
		}
	}

	public static class ClientManagerConfigXmlBuilder extends ClientManagerConfigBuilder implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			final ElementHelper eh = new ElementHelper(self);
			String name = eh.getString("name");
			name(name);
			inputBufferSize(roundup(eh.getInt("inputBufferSize", 16384)));
			outputBufferSize(roundup(eh.getInt("outputBufferSize", 16384)));
			checkOutputBuffer(eh.getBoolean("checkOutputBuffer", false));
			inputSecurity(eh.getHexBytes("inputSecurity"));
			outputSecurity(eh.getHexBytes("outputSecurity"));
			inputCompress(eh.getBoolean("inputCompress", false));
			outputCompress(eh.getBoolean("outputCompress", false));
			connectTimeout(eh.getLong("connectTimeout", 5000));
			final String remoteIp = eh.getString("remoteIp", "127.0.0.1");
			final int remotePort = eh.getInt("remotePort");
			final InetSocketAddress remoteaddr = new InetSocketAddress(remoteIp, remotePort);
			peerAddress(remoteaddr);
			autoReconnect(eh.getBoolean("autoReconnect", false));
			asynchronous(eh.getBoolean("asynchronous", false));
			if (!name.isEmpty())
				Service.JMXRegister(new ClientManagerConfigMXBean() {
					private final ClientManagerConfig config = build();

					@Override
					public String getName() {
						return config.getName();
					}

					@Override
					public int getInputBufferSize() {
						return config.getInputBufferSize();
					}

					@Override
					public int getOutputBufferSize() {
						return config.getOutputBufferSize();
					}

					@Override
					public boolean isCheckOutputBuffer() {
						return config.isCheckOutputBuffer();
					}

					@Override
					public String getInputSecurity() {
						return config.getInputSecurityBytes() == null ? ""
								: Helper.toHexString(config.getInputSecurityBytes());
					}

					@Override
					public String getOutputSecurity() {
						return config.getOutputSecurityBytes() == null ? ""
								: Helper.toHexString(config.getOutputSecurityBytes());
					}

					@Override
					public boolean isInputCompress() {
						return config.isInputCompress();
					}

					@Override
					public boolean isOutputCompress() {
						return config.isOutputCompress();
					}

					@Override
					public String getRemoteaddrString() {
						return config.getPeerAddress().toString();
					}

					@Override
					public boolean isAutoreconnect() {
						return config.isAutoReconnect();
					}

					@Override
					public boolean isAsynchronous() {
						return config.isAsynchronous();
					}
				}, "limax.xmlconfig:type=XmlConfigs,name=client-" + name + "_"
						+ remoteaddr.toString().replace(':', '_'));
		}
	}

	public final static class ManagerConfigParserCreator implements ConfigParserCreator {
		private final static ClientListener defaultClientListener = new ClientListener() {
			@Override
			public void onManagerInitialized(Manager manager, Config config) {
			}

			@Override
			public void onManagerUninitialized(Manager manager) {
			}

			@Override
			public void onTransportAdded(Transport transport) {
			}

			@Override
			public void onTransportRemoved(Transport transport) {
			}

			@Override
			public void onAbort(Transport transport) {
			}
		};

		private final static ServerListener defaultServerListener = new ServerListener() {
			@Override
			public void onManagerInitialized(Manager manager, Config config) {
			}

			@Override
			public void onManagerUninitialized(Manager manager) {
			}

			@Override
			public void onTransportAdded(Transport transport) {
			}

			@Override
			public void onTransportRemoved(Transport transport) {
			}
		};

		private static ConfigParser hollowConfigParser() {
			return e -> {
			};
		}

		@Override
		public ConfigParser createConfigParse(Element self) throws Exception {
			ElementHelper eh = new ElementHelper(self);
			if (!eh.getBoolean("enable", true))
				return hollowConfigParser();
			final String name = eh.getString("name");
			if (name.isEmpty())
				throw new IllegalArgumentException("Manager need a name");
			final String clsname = eh.getString("className");
			final Listener listener;
			final String type = eh.getString("type").toLowerCase();
			if (clsname.isEmpty()) {
				if (type.equalsIgnoreCase("client"))
					listener = defaultClientListener;
				else if (type.equalsIgnoreCase("server"))
					listener = defaultServerListener;
				else
					throw new IllegalArgumentException("Manager name = " + name + " unknown type = " + type);
			} else {
				Class<?> cls = Class.forName(clsname);
				String singleton = eh.getString("classSingleton");
				listener = (Listener) (singleton.isEmpty() ? cls.getDeclaredConstructor().newInstance()
						: cls.getMethod(singleton).invoke(null));
			}
			if (type.equalsIgnoreCase("client")) {
				ClientManagerConfigBuilder builder = new ClientManagerConfigXmlBuilder()
						.defaultState(getDefaultState(self, listener));
				Service.addRunAfterEngineStartTask(() -> launchManagerTask(
						builder.dispatcher(new Dispatcher(Engine.getProtocolExecutor())), listener, name));
				return (ConfigParser) builder;
			}
			if (type.equalsIgnoreCase("server")) {
				ServerManagerConfigBuilder builder = new ServerManagerConfigXmlBuilder()
						.defaultState(getDefaultState(self, listener));
				Service.addRunAfterEngineStartTask(() -> launchManagerTask(
						builder.dispatcher(new Dispatcher(Engine.getProtocolExecutor())), listener, name));
				return (ConfigParser) builder;
			}
			throw new IllegalArgumentException("Manager name = " + name + " class not match = " + listener.getClass());
		}

		public static State getDefaultState(Element self, Listener listener) throws Exception {
			ElementHelper eh = new ElementHelper(self);
			String classname = eh.getString("defaultStateClass");
			if (classname.isEmpty())
				classname = eh.getString("className");
			else
				listener = null;
			return (State) Class.forName(classname).getMethod("getDefaultState").invoke(listener);
		}
	}

	public final static class GlobalIdConfigParserCreator implements ConfigParserCreator {
		@Override
		public ConfigParser createConfigParse(Element self) throws Exception {
			ElementHelper eh = new ElementHelper(self);
			GlobalId.setTimeout(eh.getLong("timeout", 2000));
			final ClientManagerConfigBuilder builder = new ClientManagerConfigXmlBuilder()
					.defaultState(GlobalIdListener.getInstance().getDefaultState());
			Service.addRunAfterEngineStartTask(
					() -> launchManagerTask(builder.dispatcher(new Dispatcher(Engine.getProtocolExecutor())),
							GlobalIdListener.getInstance(), eh.getString("name")));
			return (ConfigParser) builder;
		}
	}

	public interface ThreadPoolSizeDataMXBean {
		int getNioCpus();

		int getNetProcessors();

		int getProtocolSchedulers();

		int getApplicationExecutors();
	}

	public final static class ThreadPoolSizeData implements ThreadPoolSizeDataMXBean, ConfigParser {

		private int nioCpus = 1;
		private int netProcessors = 4;
		private int protocolSchedulers = 4;
		private int applicationExecutors = 16;

		@Override
		public void parse(Element self) throws Exception {
			final ElementHelper eh = new ElementHelper(self);
			nioCpus = eh.getInt("nioCpus", 1);
			netProcessors = eh.getInt("netProcessors", 4);
			protocolSchedulers = eh.getInt("protocolSchedulers", 4);
			applicationExecutors = eh.getInt("applicationExecutors", 16);
			Service.JMXRegister(this, "limax.xmlconfig:type=XmlConfigs,name=threadpoolsize");
		}

		@Override
		public int getNioCpus() {
			return nioCpus;
		}

		@Override
		public int getNetProcessors() {
			return netProcessors;
		}

		@Override
		public int getProtocolSchedulers() {
			return protocolSchedulers;
		}

		@Override
		public int getApplicationExecutors() {
			return applicationExecutors;
		}
	}

	public interface JMXServerMXBean {
		int getRmiport();

		int getServerport();
	}

	public static final class JMXServer implements ConfigParser, JMXServerMXBean {

		private int rmiPort = 0;
		private int serverPort = 0;
		private String host = "localhost";

		@Override
		public void parse(Element self) throws Exception {
			ElementHelper eh = new ElementHelper(self);
			rmiPort = eh.getInt("rmiPort", 0);
			serverPort = eh.getInt("serverPort", 0);
			host = eh.getString("host", host);
			if (rmiPort > 0 && serverPort > 0)
				Service.addRunAfterEngineStopTask(MBeanServer.start(host, serverPort, rmiPort, eh.getString("username"),
						eh.getString("password")));
			Service.JMXRegister(this, "limax.xmlconfig:type=XmlConfigs,name=jmxserver");
		}

		@Override
		public int getRmiport() {
			return rmiPort;
		}

		@Override
		public int getServerport() {
			return serverPort;
		}
	}

	public static final class NodeService implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			ElementHelper eh = new ElementHelper(self);
			String module = eh.getString("module");
			String[] parameters = XMLUtils.getChildElements(self).stream()
					.filter(e -> e.getNodeName().equalsIgnoreCase("parameter"))
					.map(e -> new ElementHelper(e).getString("value")).toArray(n -> new String[n]);
			Service.addRunAfterEngineStartTask(() -> {
				Thread thread = new Thread(() -> {
					try {
						limax.node.js.Main.launchEngine(module, parameters);
					} catch (Exception e) {
						if (Trace.isErrorEnabled())
							Trace.error("Load Node module: " + module, e);
					}
				});
				thread.setDaemon(true);
				thread.start();
			});
		}
	}
}
