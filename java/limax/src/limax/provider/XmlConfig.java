package limax.provider;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.w3c.dom.Element;

import limax.codec.ByteArrayCodecFunction;
import limax.codec.CodecException;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.key.KeyAllocator;
import limax.key.ed.Compressor;
import limax.key.ed.KeyProtector;
import limax.key.ed.Transformer;
import limax.net.Config;
import limax.net.Engine;
import limax.net.Manager;
import limax.net.ManagerConfig;
import limax.net.State;
import limax.net.Transport;
import limax.pkix.SSLContextAllocator;
import limax.provider.states.ProviderClient;
import limax.util.Dispatcher;
import limax.util.ElementHelper;
import limax.util.Enable;
import limax.util.Pair;
import limax.util.Trace;
import limax.util.XMLUtils;
import limax.xmlconfig.ClientManagerConfigBuilder;
import limax.xmlconfig.ConfigParser;
import limax.xmlconfig.ConfigParserCreator;
import limax.xmlconfig.ServerManagerConfigBuilder;
import limax.xmlconfig.Service;
import limax.xmlconfig.XmlConfigs;
import limax.zdb.Zdb;

public final class XmlConfig {

	private XmlConfig() {
	}

	public interface ProviderDataMXBean {
		String getName();

		int getProviderId();

		String getViewManagerClassName();

		String getAllowUseVariant();

		String getAllowUseScript();

		long getSessionTimeout();
	}

	public final static class ProviderData implements ProviderDataMXBean, ConfigParser {
		private String name = "default";
		private int pvid = 0;
		private String pvkey = "";
		private String viewManagerClassName;
		private Enable allowUseVariant = Enable.Default;
		private Enable allowUseScript = Enable.Default;
		private long sessionTimeout = 0;
		private final State state = new State();

		@Override
		public void parse(Element self) throws Exception {
			final ElementHelper eh = new ElementHelper(self);
			name = eh.getString("name", name);
			pvid = eh.getInt("pvid");
			if (pvid < 1 || pvid > 0xffffff)
				throw new IllegalArgumentException("pvid = " + pvid + " not in range[1, 0xFFFFFF]");
			pvkey = eh.getString("key");
			viewManagerClassName = eh.getString("viewManagerClass");
			allowUseVariant = Enable.parse(eh.getString("useVariant"));
			allowUseScript = Enable.parse(eh.getString("useScript"));
			sessionTimeout = eh.getLong("sessionTimeout", 0);
			state.merge(ProviderClient.getDefaultState());
			String clsname = eh.getString("additionalStateClass");
			if (!clsname.isEmpty())
				state.merge((State) Class.forName(clsname).getMethod("getDefaultState").invoke(null));
		}

		@Override
		public int getProviderId() {
			return pvid;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getViewManagerClassName() {
			return viewManagerClassName;
		}

		@Override
		public String getAllowUseVariant() {
			return allowUseVariant.toString();
		}

		@Override
		public String getAllowUseScript() {
			return allowUseScript.toString();
		}

		@Override
		public long getSessionTimeout() {
			return sessionTimeout;
		}
	}

	private static class TunnelConfig implements ConfigParser {
		private Map<URI, TunnelCodec> encoders;
		private URI defaultGroup;
		private TunnelCodec decoder;

		@Override
		public void parse(Element self) throws Exception {
			ElementHelper eh = new ElementHelper(self);
			Compressor compressor = Compressor.valueOf(eh.getString("compressor", "NONE"));
			KeyProtector keyProtector = KeyProtector.valueOf(eh.getString("keyProtector", "HMACSHA256"));
			long defaultExpire = eh.getLong("defaultExpire", 3600000L);
			Map<URI, Long> expires = XMLUtils.getChildElements(self).stream()
					.filter(e -> e.getNodeName().equals("Expire")).map(ElementHelper::new)
					.collect(Collectors.toMap(e -> URI.create(e.getString("group")), e -> e.getLong("value")));
			String defaultGroup = eh.getString("defaultGroup", null);
			Optional<Element> optional = XMLUtils.getChildElements(self).stream()
					.filter(e -> e.getNodeName().equals("PKIX")).findFirst();
			Set<URI> groups;
			Transformer transformer;
			if (optional.isPresent()) {
				eh = new ElementHelper(optional.get());
				String passphrase = eh.getString("passphrase", null);
				SSLContextAllocator sslContextAllocator = new SSLContextAllocator(URI.create(eh.getString("location")),
						passphrase == null ? prompt -> System.console().readPassword(prompt)
								: prompt -> passphrase.toCharArray());
				String trustsPath = eh.getString("trustsPath", null);
				if (trustsPath != null)
					sslContextAllocator.getTrustManager().addTrust(Paths.get(trustsPath));
				sslContextAllocator.getTrustManager()
						.setRevocationCheckerOptions(eh.getString("revocationCheckerOptions"));
				KeyAllocator keyAllocator = new KeyAllocator(sslContextAllocator);
				String httpsHost = eh.getString("httpsHost", null);
				if (httpsHost != null)
					keyAllocator.setHost(httpsHost);
				transformer = new Transformer(keyAllocator);
				groups = keyAllocator.getURIs().keySet();
			} else {
				Map<URI, byte[]> keyAllocator = XMLUtils.getChildElements(self).stream()
						.filter(e -> e.getNodeName().equals("SharedKey")).map(ElementHelper::new)
						.map(e -> new Pair<>(URI.create(e.getString("group")),
								e.getString("key").getBytes(StandardCharsets.UTF_8)))
						.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
				transformer = new Transformer(keyAllocator);
				groups = keyAllocator.keySet();
			}
			if (defaultGroup == null) {
				this.defaultGroup = groups.iterator().next();
			} else {
				this.defaultGroup = URI.create(defaultGroup);
				if (!groups.contains(this.defaultGroup))
					throw new RuntimeException("defaultGroup " + this.defaultGroup + " not configured.");
			}
			this.encoders = groups.stream().collect(Collectors.toMap(group -> group, group -> {
				ByteArrayCodecFunction encodeFunction = transformer.getEncoder(group, keyProtector, compressor);
				Long _expire = expires.get(group);
				long expire = _expire == null ? defaultExpire : _expire;
				return (label, data) -> {
					try {
						return Octets.wrap(encodeFunction.apply(new OctetsStream().marshal(label)
								.marshal(System.currentTimeMillis() + expire).marshal(data).getBytes()));
					} catch (CodecException e) {
						throw new TunnelException(TunnelException.Type.CODEC, e);
					}
				};
			}));
			ByteArrayCodecFunction decodeFunction = transformer.getDecoder();
			this.decoder = (label, data) -> {
				try {
					OctetsStream os = OctetsStream.wrap(Octets.wrap(decodeFunction.apply(data.getBytes())));
					if (os.unmarshal_int() != label)
						throw new TunnelException(TunnelException.Type.LABEL);
					if (System.currentTimeMillis() > os.unmarshal_long())
						throw new TunnelException(TunnelException.Type.EXPIRE);
					return os.unmarshal_Octets();
				} catch (MarshalException | CodecException e) {
					throw new TunnelException(TunnelException.Type.CODEC, e);
				}
			};
		}

		TunnelCodec getTunnelEncoder(URI group) {
			TunnelCodec encoder = encoders.get(group == null ? this.defaultGroup : group);
			if (encoder == null)
				throw new RuntimeException("tunnel group " + group + " not config.");
			return encoder;
		}

		TunnelCodec getTunnelDecoder() {
			return decoder;
		}
	}

	private static class ProviderManagerConfigImpl implements ProviderManagerConfig {
		private final State providerstate;

		final List<ClientManagerConfigBuilder> cb = new ArrayList<>();
		final List<ServerManagerConfigBuilder> sb = new ArrayList<>();
		final ProviderData providerdata = new ProviderData();
		final TunnelConfig tunnelConfig = new TunnelConfig();

		public ProviderManagerConfigImpl(State providerstate) {
			this.providerstate = providerstate;
		}

		void addClientManagerConfigBuilder(Element e) throws Exception {
			XmlConfigs.ClientManagerConfigXmlBuilder b = new XmlConfigs.ClientManagerConfigXmlBuilder();
			b.parse(e);
			cb.add(b.name("Provider " + providerdata.name + " client manager").defaultState(providerdata.state)
					.dispatcher(new Dispatcher(Engine.getProtocolExecutor())).autoReconnect(true));
		}

		void addServerManagerConfigBuilder(Element e) throws Exception {
			XmlConfigs.ServerManagerConfigXmlBuilder b = new XmlConfigs.ServerManagerConfigXmlBuilder();
			b.parse(e);
			sb.add(b.name("Provider " + providerdata.name + " server manager").defaultState(providerdata.state)
					.dispatcher(new Dispatcher(Engine.getProtocolExecutor())));
		}

		void addTunnelConfig(Element e) throws Exception {
			tunnelConfig.parse(e);
		}

		@Override
		public State getProviderState() {
			return providerstate;
		}

		@Override
		public int getProviderId() {
			return providerdata.pvid;
		}

		@Override
		public String getProviderKey() {
			return providerdata.pvkey;
		}

		@Override
		public Map<Integer, Integer> getProviderProtocolInfos() {
			return providerstate.getSizePolicy().entrySet().stream()
					.collect(Collectors.toMap(e -> e.getKey() | (providerdata.pvid << 8), e -> e.getValue()));
		}

		@Override
		public List<ManagerConfig> getManagerConfigs() {
			return Stream
					.concat(cb.stream().map(c -> c.dispatcher(new Dispatcher(Engine.getProtocolExecutor())).build()),
							sb.stream().map(c -> c.dispatcher(new Dispatcher(Engine.getProtocolExecutor())).build()))
					.collect(Collectors.toList());
		}

		@Override
		public String getName() {
			return providerdata.name;
		}

		@Override
		public TunnelCodec getTunnelEncoder(URI group) {
			return tunnelConfig.getTunnelEncoder(group);
		}

		@Override
		public TunnelCodec getTunnelDecoder() {
			return tunnelConfig.getTunnelDecoder();
		}

		@Override
		public String getViewManagerClassName() {
			return providerdata.getViewManagerClassName();
		}

		@Override
		public Enable getAllowUseVariant() {
			return providerdata.allowUseVariant;
		}

		@Override
		public Enable getAllowUseScript() {
			return providerdata.allowUseScript;
		}

		@Override
		public long getSessionTimeout() {
			return providerdata.getSessionTimeout();
		}
	}

	public final static class ProviderDataCreator implements ConfigParserCreator {
		static {
			try {
				Class.forName("limax.provider.ProviderManagerImpl");
			} catch (ClassNotFoundException e) {
			}
		}

		@Override
		public ConfigParser createConfigParse(Element self) throws Exception {
			ElementHelper eh = new ElementHelper(self);
			String clsname = eh.getString("className");
			ProviderListener listener;
			if (clsname.isEmpty()) {
				listener = new ProviderListener() {
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
					public void onTransportDuplicate(Transport transport) throws Exception {
						transport.getManager().close(transport);
					}
				};
			} else {
				Class<?> cls = Class.forName(clsname);
				String singleton = eh.getString("classSingleton");
				listener = (ProviderListener) (singleton.isEmpty() ? cls.getDeclaredConstructor().newInstance()
						: cls.getMethod(singleton).invoke(null));
			}

			ProviderManagerConfigImpl config = new ProviderManagerConfigImpl(
					XmlConfigs.ManagerConfigParserCreator.getDefaultState(self, listener));

			Service.addRunAfterEngineStartTask(() -> {
				try {
					Engine.add(config, listener);
					Trace.info("ProviderManager " + listener + " opened!");
				} catch (Exception e) {
					if (Trace.isErrorEnabled())
						Trace.error("Engine.getInstance().add " + listener, e);
					throw new RuntimeException(e);
				}
			});

			return _self -> {
				config.providerdata.parse(_self);
				Service.JMXRegister(config.providerdata, "limax.provider:type=XmlConfig,name=" + config.getName());
				for (Element e : XMLUtils.getChildElements(_self).stream()
						.filter(e -> e.getNodeName().equals("Manager"))
						.filter(e -> new ElementHelper(e).getBoolean("enable", true)).collect(Collectors.toList())) {
					String type = new ElementHelper(e).getString("type");
					if (type.equalsIgnoreCase("client"))
						config.addClientManagerConfigBuilder(e);
					else if (type.equalsIgnoreCase("server"))
						config.addServerManagerConfigBuilder(e);
					else
						throw new IllegalArgumentException("Provider " + config.providerdata.name
								+ " manager's type must be 'server' or 'client'.");
				}
				Optional<Element> optionalTunnelElement = XMLUtils.getChildElements(_self).stream()
						.filter(e -> e.getNodeName().equals("Tunnel")).findFirst();
				if (optionalTunnelElement.isPresent())
					config.addTunnelConfig(optionalTunnelElement.get());
			};
		}
	}

	public final static class StartZdb implements ConfigParser {
		@Override
		public void parse(Element self) throws Exception {
			limax.xmlgen.Zdb meta = limax.xmlgen.Zdb.loadFromClass();
			meta.initialize(self);
			for (Element e : XMLUtils.getChildElements(self)) {
				switch (e.getNodeName()) {
				case "Procedure":
					meta.getProcedure().initialize(e);
					break;
				case "Table":
					meta.getTable(new ElementHelper(e).getString("name")).initialize(e);
					break;
				default:
					Trace.error("unknown tag Zdb." + e.getNodeName());
					break;
				}
			}
			Service.addRunBeforeEngineStartTask(() -> Zdb.getInstance().start(meta));
			Service.addRunAfterEngineStopTask(() -> Zdb.getInstance().stop());
		}
	}
}
