package limax.key;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;

import limax.codec.CodecException;
import limax.codec.OctetsStream;
import limax.codec.SinkOctets;
import limax.codec.StreamSource;
import limax.http.DataSupplier;
import limax.http.HttpException;
import limax.http.HttpExchange;
import limax.http.HttpHandler;
import limax.http.HttpServer;
import limax.p2p.DHTAddress;
import limax.p2p.Neighbors;
import limax.p2p.NetworkID;
import limax.pkix.SSLContextAllocator;
import limax.util.Pair;

class P2pHandler implements HttpHandler {
	private static final int REPORT_SIZE = Integer.getInteger("limax.key.P2pHandler.REPORT_SIZE", 16);
	private static final int CONCURRENCY_LEVEL = Integer.getInteger("limax.key.P2pHandler.CONCURRENCY_LEVEL", 64);
	private static final int BASE_LIMIT = Integer.getInteger("limax.key.P2pHandler.BASE_LIMIT", 8);
	private static final int ANTICIPANTION = Integer.getInteger("limax.key.P2pHandler.ANTICIPANTION", 8);
	private static final long HTTPS_POST_LIMIT = Long.getLong("limax.key.P2pHandler.HTTPS_POST_MAX", 1048576);

	private static final HostnameVerifier hostnameVerifier = (hostname, session) -> verifySSLSession(session);
	private final SSLContextAllocator sslContextAllocator;
	private final MasterKeyContainer masterKeyContainer;
	private final ThreadPoolExecutor executor;
	private final Neighbors neighbors;

	P2pHandler(SSLContextAllocator sslContextAllocator, Neighbors neighbors, MasterKeyContainer masterKeyContainer,
			ThreadPoolExecutor executor) {
		this.sslContextAllocator = sslContextAllocator;
		this.masterKeyContainer = masterKeyContainer;
		this.executor = executor;
		this.neighbors = neighbors;
	}

	private static boolean verifySSLSession(SSLSession session) {
		try {
			X509Certificate peerCertificate = (X509Certificate) session.getPeerCertificates()[0];
			X509Certificate localCertificate = (X509Certificate) session.getLocalCertificates()[0];
			return KeyAllocator.getSubjectCN(peerCertificate).equals(KeyAllocator.getSubjectCN(localCertificate))
					&& peerCertificate.getIssuerX500Principal().equals(localCertificate.getIssuerX500Principal());
		} catch (Exception e) {
			return false;
		}
	}

	private class PrivateSSLSocketFactory extends SSLSocketFactory {

		private final SSLSocketFactory impl;
		private Socket socket;

		PrivateSSLSocketFactory() throws Exception {
			this.impl = sslContextAllocator.alloc().getSocketFactory();
		}

		@Override
		public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
			return socket = impl.createSocket(s, host, port, autoClose);
		}

		@Override
		public String[] getDefaultCipherSuites() {
			return impl.getDefaultCipherSuites();
		}

		@Override
		public String[] getSupportedCipherSuites() {
			return impl.getDefaultCipherSuites();
		}

		@Override
		public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
			return socket = impl.createSocket(host, port);
		}

		@Override
		public Socket createSocket(InetAddress host, int port) throws IOException {
			return socket = impl.createSocket(host, port);
		}

		@Override
		public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
				throws IOException, UnknownHostException {
			return socket = impl.createSocket(host, port, localHost, localPort);
		}

		@Override
		public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
				throws IOException {
			return socket = impl.createSocket(address, port, localAddress, localPort);
		}

		public InetSocketAddress getRemoteInetSocketAddress() {
			return (InetSocketAddress) socket.getRemoteSocketAddress();
		}

		public InetSocketAddress getLocalInetSocketAddress() {
			return (InetSocketAddress) socket.getLocalSocketAddress();
		}
	}

	private OctetsStream getInputOctetsStream(InputStream in) throws CodecException {
		OctetsStream os = new OctetsStream();
		new StreamSource(in, new SinkOctets(os)).flush();
		return os;
	}

	private OctetsStream getInputOctetsStream(HttpURLConnection connection) throws CodecException, IOException {
		try (InputStream in = connection.getInputStream()) {
			return getInputOctetsStream(in);
		}
	}

	@Override
	public void censor(HttpExchange exchange) {
		exchange.getFormData().postLimit(HTTPS_POST_LIMIT);
	}

	@Override
	public DataSupplier handle(HttpExchange exchange) {
		if (verifySSLSession(exchange.getSSLSession()))
			try {
				switch (exchange.getRequestURI().getQuery()) {
				case "search":
					return search(exchange);
				case "upload":
					return _upload(exchange);
				}
			} catch (Exception e) {
			}
		throw new HttpException(HttpURLConnection.HTTP_BAD_REQUEST, true);
	}

	void createContext(HttpServer server) {
		server.createContext("/p2p", this);
	}

	private HttpsURLConnection connect(InetSocketAddress inetSocketAddress, String query, SSLSocketFactory factory)
			throws IOException {
		HttpsURLConnection connection = (HttpsURLConnection) new URL("https",
				inetSocketAddress.getAddress().getHostAddress(), "/p2p?" + query).openConnection();
		connection.setConnectTimeout(KeyServer.NETWORK_TIMEOUT);
		connection.setReadTimeout(KeyServer.NETWORK_TIMEOUT);
		connection.setHostnameVerifier(hostnameVerifier);
		connection.setSSLSocketFactory(factory);
		connection.setRequestProperty("Content-Type", "application/octet-stream");
		connection.setDoOutput(true);
		connection.connect();
		return connection;
	}

	private DataSupplier _upload(HttpExchange exchange) throws Exception {
		masterKeyContainer.merge(new UploadRequest(OctetsStream.wrap(exchange.getFormData().getRaw())).getKeyPairs());
		return null;
	}

	private void upload(Collection<Long> timestamps, InetSocketAddress inetSocketAddress) throws Exception {
		UploadRequest upload = new UploadRequest(masterKeyContainer.collect(timestamps));
		if (upload.getKeyPairs().isEmpty())
			return;
		HttpsURLConnection connection = connect(inetSocketAddress, "upload",
				sslContextAllocator.alloc().getSocketFactory());
		try (OutputStream out = connection.getOutputStream()) {
			OctetsStream os = new OctetsStream().marshal(upload);
			out.write(os.array(), 0, os.size());
		}
		try (InputStream in = connection.getInputStream()) {
		}
	}

	private DataSupplier search(HttpExchange exchange) throws Exception {
		SearchRequest req = new SearchRequest(OctetsStream.wrap(exchange.getFormData().getRaw()));
		Set<Long> timestamps = new HashSet<>(req.getTimestamps());
		neighbors.addAll(new HashSet<>(Arrays.asList(
				new NetworkID(req.getLocalDHTAddress(), new InetSocketAddress(req.getLocalInetAddress(), 443)),
				new NetworkID(req.getLocalDHTAddress(),
						new InetSocketAddress(exchange.getPeerAddress().getAddress(), 443)))));
		OctetsStream os = new OctetsStream().marshal(new SearchResponse(neighbors.getLocalDHTAddress(),
				neighbors.search(req.getSearchFor(), REPORT_SIZE), masterKeyContainer.diff(timestamps), timestamps));
		exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
		return DataSupplier.from(os.getByteBuffer());
	}

	private Pair<Boolean, Collection<NetworkID>> search(DHTAddress searchFor, InetSocketAddress inetSocketAddress)
			throws Exception {
		PrivateSSLSocketFactory factory = new PrivateSSLSocketFactory();
		HttpsURLConnection connection = connect(inetSocketAddress, "search", factory);
		try (OutputStream out = connection.getOutputStream()) {
			OctetsStream os = new OctetsStream().marshal(new SearchRequest(searchFor, neighbors.getLocalDHTAddress(),
					factory.getLocalInetSocketAddress().getAddress(), masterKeyContainer.getTimestamps()));
			out.write(os.array(), 0, os.size());
		}
		SearchResponse res = new SearchResponse(getInputOctetsStream(connection));
		neighbors.add(new NetworkID(res.getLocalDHTAddress(), factory.getRemoteInetSocketAddress()));
		if (!res.getKeyPairs().isEmpty())
			masterKeyContainer.merge(res.getKeyPairs());
		if (!res.getTimestamps().isEmpty())
			executor.execute(() -> {
				try {
					upload(res.getTimestamps(), inetSocketAddress);
				} catch (Exception e) {
					neighbors.remove(inetSocketAddress);
				}
			});
		return new Pair<>(false, res.getCandidates());
	}

	Collection<NetworkID> build(DHTAddress address, Supplier<Collection<InetSocketAddress>> entranceAddressesSupplier) {
		return neighbors.searchNode(address, (a, b) -> search(a, b), neighbors.search(address, BASE_LIMIT),
				entranceAddressesSupplier, ANTICIPANTION, CONCURRENCY_LEVEL);
	}

	Collection<InetAddress> refresh() {
		build(neighbors.getLocalDHTAddress(), () -> neighbors.search());
		return build(new DHTAddress(), () -> Collections.emptyList()).stream().map(NetworkID::getInetSocketAddress)
				.map(InetSocketAddress::getAddress).collect(Collectors.toList());
	}

	void publish(long timestamp) {
		executor.execute(() -> {
			Collection<Long> timestamps = Arrays.asList(timestamp);
			Set<InetSocketAddress> destinations = new HashSet<>(neighbors.search());
			destinations.addAll(neighbors.getInetSocketAddresses());
			destinations.forEach(inetSocketAddress -> executor.execute(() -> {
				try {
					upload(timestamps, inetSocketAddress);
				} catch (Exception e) {
					neighbors.remove(inetSocketAddress);
				}
			}));
		});
	}
}
