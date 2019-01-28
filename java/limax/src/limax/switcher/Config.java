package limax.switcher;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.w3c.dom.Element;

import limax.net.Engine;
import limax.net.Manager;
import limax.net.io.ServerContext;
import limax.util.Dispatcher;
import limax.util.ElementHelper;
import limax.util.Helper;
import limax.util.Trace;
import limax.util.XMLUtils;
import limax.xmlconfig.ConfigParser;
import limax.xmlconfig.ConfigParserCreator;
import limax.xmlconfig.ServerManagerConfigBuilder;
import limax.xmlconfig.Service;
import limax.xmlconfig.XmlConfigs;

public class Config {

	private Config() {
	}

	public static class SwitcherConfig implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			ElementHelper eh = new ElementHelper(self);
			SwitcherListener listener = SwitcherListener.getInstance();
			listener.setNeedCompress(eh.getBoolean("needcompress.s2c", true), eh.getBoolean("needcompress.c2s", true));
			listener.setDHGroups(Stream.concat(Stream.of(1),
					XMLUtils.getChildElements(self).stream().filter(e -> e.getNodeName().equalsIgnoreCase("dh"))
							.map(e -> new ElementHelper(e).getInt("group", 1)).filter(Helper::isDHGroupSupported))
					.collect(Collectors.toSet()));
			listener.setKey(eh.getString("key", ""));
			listener.setNativeIds(
					XMLUtils.getChildElements(self).stream().filter(e -> e.getNodeName().equalsIgnoreCase("native"))
							.map(e -> new ElementHelper(e).getInt("id", 0)).collect(Collectors.toList()));
			listener.setWsIds(
					XMLUtils.getChildElements(self).stream().filter(e -> e.getNodeName().equalsIgnoreCase("ws"))
							.map(e -> new ElementHelper(e).getInt("id", 0)).collect(Collectors.toList()));
			listener.setWssIds(
					XMLUtils.getChildElements(self).stream().filter(e -> e.getNodeName().equalsIgnoreCase("wss"))
							.map(e -> new ElementHelper(e).getInt("id", 0)).collect(Collectors.toList()));
			Service.addRunAfterEngineStartTask(
					() -> listener.setLoginCache(eh.getString("cacheGroup", ""), eh.getInt("cacheCapacity", 10000)));
		}
	}

	public interface TranspondMXBean {
		String getName();

		String getServerIp();

		int getServerPort();

		int getLocalPort();

		int getBufferSize();

		boolean isAutoStart();

		boolean isAsynchronous();

		void open();
	}

	public static class Transpond implements ConfigParser, TranspondMXBean {
		private String name = "";
		private String serverIp = "";
		private String localIp = "";
		private int serverPort = 0;
		private int localPort = 0;
		private int buffersize = 0;
		private boolean autostart = false;
		private boolean asynchronous = false;
		private Runnable openlisten = () -> {
		};

		@Override
		public void parse(Element self) throws Exception {
			ElementHelper eh = new ElementHelper(self);

			serverIp = eh.getString("serverIp");
			if (serverIp.isEmpty())
				throw new IllegalArgumentException("Transpond need attribute serverIp");
			serverPort = eh.getInt("serverPort");
			localIp = eh.getString("localIp");
			localPort = eh.getInt("localPort");
			name = eh.getString("name", serverIp + "-" + serverPort);
			autostart = eh.getBoolean("autostart", false);
			buffersize = eh.getInt("bufferSize", 16384);
			asynchronous = eh.getBoolean("asynchronous", false);
			SocketAddress serverAddress = new InetSocketAddress(serverIp, serverPort);
			SocketAddress localAddress = localIp.isEmpty() ? new InetSocketAddress(localPort)
					: new InetSocketAddress(localIp, localPort);

			Service.JMXRegister(this, "limax.switcher:type=Config,name=transponds-" + name);
			final Runnable open = () -> {
				try {
					ServerContext serverconfig = limax.util.transpond.Transpond.startTranspond(localAddress,
							serverAddress, buffersize, asynchronous);
					serverconfig.open();
					Service.addRunBeforeEngineStopTask(() -> {
						try {
							serverconfig.close();
						} catch (IOException e) {
							if (Trace.isWarnEnabled())
								Trace.warn("close transpond", e);
						}
					});
				} catch (Exception e) {
					if (Trace.isErrorEnabled())
						Trace.error("start transpond " + this, e);
				}
			};
			if (autostart)
				Service.addRunAfterEngineStartTask(open);
			else
				openlisten = open;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getServerIp() {
			return serverIp;
		}

		@Override
		public int getServerPort() {
			return serverPort;
		}

		@Override
		public int getLocalPort() {
			return localPort;
		}

		@Override
		public boolean isAutoStart() {
			return autostart;
		}

		@Override
		public String toString() {
			return "[name = " + name + " autostart = " + autostart + " serverIp = " + serverIp + " serverPort = "
					+ serverPort + " localPort = " + localPort + "]";
		}

		@Override
		public int getBufferSize() {
			return buffersize;
		}

		@Override
		public boolean isAsynchronous() {
			return asynchronous;
		}

		@Override
		public void open() {
			openlisten.run();
		}
	}

	public static class SwitcherManagerCreator implements ConfigParserCreator {

		@Override
		public ConfigParser createConfigParse(Element self) throws Exception {
			ServerManagerConfigBuilder builder = new XmlConfigs.ServerManagerConfigXmlBuilder()
					.defaultState(limax.switcher.states.SwitcherServer.getDefaultState());
			Service.addRunAfterEngineStartTask(() -> {
				try {
					Manager manager = new SwitcherManager(
							builder.dispatcher(new Dispatcher(Engine.getProtocolExecutor())).build());
					if (Trace.isInfoEnabled())
						Trace.info("SwitcherManager " + manager + " opened!");
				} catch (Exception e) {
					if (Trace.isErrorEnabled())
						Trace.error("Engine.getInstance().add " + builder.build().getName(), e);
					throw new RuntimeException(e);
				}
			});
			return (ConfigParser) builder;
		}
	}
}
