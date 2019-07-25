package limax.auany.paygws;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.w3c.dom.Element;

import limax.auany.FileBundle;
import limax.auany.HttpClientManager;
import limax.auany.PayDelivery;
import limax.auany.PayGateway;
import limax.auany.PayManager;
import limax.auany.PayOrder;
import limax.auany.ReplayProtector;
import limax.codec.JSON;
import limax.defines.ErrorCodes;
import limax.defines.ErrorSource;
import limax.endpoint.AuanyService.Result;
import limax.http.HttpHandler;
import limax.util.ConcurrentEnvironment;
import limax.util.ElementHelper;
import limax.util.Trace;

public final class AppStore implements PayGateway {
	private URL url;
	private Pattern pattern;
	private final static Implement impl = new Implement();
	private static long receiptExpire;

	@Override
	public void initialize(Element e, BiConsumer<String, HttpHandler> httphandlers) throws Exception {
		ElementHelper eh = new ElementHelper(e);
		url = new URL(eh.getString("url", "https://buy.itunes.apple.com/verifyReceipt"));
		pattern = Pattern.compile(eh.getString("productPattern", "[^\\d]+([\\d]+)$"));
		impl.initialize(url.getHost(), (Element) e.getParentNode());
	}

	@Override
	public void unInitialize() {
		impl.unInitialize();
	}

	@Override
	public void onPay(long sessionid, int gateway, int payid, int product, int price, int quantity, String receipt,
			Result onresult) throws Exception {
		JSON json = JSON
				.parse(new String(Base64.getDecoder().decode(JSON.parse(receipt).get("purchase-info").toString()),
						StandardCharsets.UTF_8));
		String message = "";
		if (System.currentTimeMillis() - json.get("purchase-date-ms").longValue() < receiptExpire) {
			Matcher matcher = pattern.matcher(json.get("product-id").toString());
			if (matcher.matches())
				impl.verify(gateway, new Request(json.get("transaction-id").longValue(), sessionid, payid,
						Integer.parseInt(matcher.group(1)), json.get("quantity").intValue(), url, receipt));
			else
				message = "product-id match fail.";
		} else
			message = "receipt exipred.";
		onresult.apply(ErrorSource.LIMAX, ErrorCodes.SUCCEED, message);
	}

	public static class Request {
		private final long serial;
		private final long transaction_id;
		private final long sessionid;
		private final int payid;
		private final int product;
		private final int quantity;
		private final URL url;
		private final String receipt;
		private Future<?> future;

		Request(long transaction_id, long sessionid, int payid, int product, int quantity, URL url, String receipt) {
			this.serial = PayOrder.serialAlloc();
			this.transaction_id = transaction_id;
			this.sessionid = sessionid;
			this.payid = payid;
			this.product = product;
			this.quantity = quantity;
			this.url = url;
			this.receipt = receipt;
		}

		Request(ByteBuffer bb) throws MalformedURLException {
			this.serial = bb.getLong();
			this.transaction_id = bb.getLong();
			this.sessionid = bb.getLong();
			this.payid = bb.getInt();
			this.product = bb.getInt();
			this.quantity = bb.getInt();
			byte[] b = new byte[bb.getInt()];
			bb.get(b);
			this.url = new URL(new String(b));
			b = new byte[bb.getInt()];
			bb.get(b);
			this.receipt = new String(b);
		}

		@Override
		public String toString() {
			return "[AppStoreRequest:" + serial + "," + transaction_id + "," + sessionid + "," + payid + "," + product
					+ "," + "," + quantity + "]";
		}

		ByteBuffer pack() {
			byte[] _url = url.toString().getBytes();
			byte[] _receipt = receipt.getBytes();
			ByteBuffer bb = ByteBuffer.allocate(44 + _url.length + _receipt.length);
			bb.putLong(serial).putLong(transaction_id).putLong(sessionid).putInt(payid).putInt(product).putInt(quantity)
					.putInt(_url.length).put(_url).putInt(_receipt.length).put(_receipt).flip();
			return bb;
		}

		JSON verify(int maxContentLength, int timeout) throws Exception {
			return JSON.parse(HttpClientManager.getService()
					.makePostRequest(url, "{\"receipt-data\":\""
							+ Base64.getEncoder().encodeToString(receipt.getBytes(StandardCharsets.UTF_8)) + "\"}",
							maxContentLength, timeout)
					.submit().get().getContent());
		}

		void update(Future<?> future) {
			if (this.future != null)
				this.future.cancel(false);
			this.future = future;
		}

		public long getTid() {
			return transaction_id;
		}

		public long getSerial() {
			return serial;
		}

		public String getOrder() {
			return Long.toString(serial, Character.MAX_RADIX);
		}

		public long getSessionId() {
			return sessionid;
		}

		public int getPayId() {
			return payid;
		}

		public int getProduct() {
			return product;
		}

		public int getQuantity() {
			return quantity;
		}

	}

	private static class Implement {
		private final AtomicInteger ref = new AtomicInteger();
		private Path receiptQueueHome;
		private Path receiptFailHome;
		private ReplayProtector rp;
		private ScheduledExecutorService scheduler;
		private int maxOutstanding;
		private int maxQueueCapacity;
		private int maxContentLength;
		private int timeout;
		private long retryDelay;

		private void queue(Request request) {
			request.update(scheduler.scheduleWithFixedDelay(() -> {
				try {
					JSON json = request.verify(maxContentLength, timeout);
					int status = json.get("status").intValue();
					Path path = receiptQueueHome.resolve("receipt." + request.transaction_id);
					if (status == 0) {
						Runnable r;
						try (FileBundle.File file = FileBundle.open(path)) {
							ByteBuffer bb = ByteBuffer.allocate(4);
							bb.putInt(0).flip();
							FileBundle.begin();
							try {
								if (rp.accept(request.transaction_id, receiptExpire)) {
									r = PayDelivery.start(request.serial, request.sessionid, request.payid,
											request.product, -1, request.quantity);
									file.write(bb, 0);
								} else
									r = null;
							} finally {
								FileBundle.end();
							}
						}
						if (r != null) {
							r.run();
							PayManager.getLogger().logAppStoreSucceed(request);
						} else {
							PayManager.getLogger().logAppStoreReceiptReplay(request);
						}
						Files.deleteIfExists(path);
					} else {
						PayManager.getLogger().logAppStoreFail(request, status);
						Files.move(path, receiptFailHome.resolve(path.getFileName()),
								StandardCopyOption.REPLACE_EXISTING);
					}
					request.update(null);
				} catch (Exception e) {
					if (Trace.isInfoEnabled())
						Trace.info("AppStore Verify receipt retry", e);
				}
			}, 0, retryDelay, TimeUnit.MILLISECONDS));
		}

		void verify(int gateway, Request request) throws Exception {
			ByteBuffer bb = request.pack();
			try (FileBundle.File file = FileBundle
					.open(receiptQueueHome.resolve("receipt." + request.transaction_id))) {
				file.write(bb, 4);
				file.force(false);
				bb.clear();
				bb.putInt(-1).flip();
				file.write(bb, 0);
				file.force(true);
			}
			PayManager.getLogger().logAppStoreCreate(request, gateway);
			queue(request);
		}

		void initialize(String host, Element e) throws Exception {
			if (ref.getAndIncrement() > 0) {
				HttpClientManager.getService().initHost(host, maxOutstanding, maxQueueCapacity);
				return;
			}
			ElementHelper eh = new ElementHelper((Element) e.getElementsByTagName("appstore").item(0));
			Path home = Paths.get(eh.getString("home", "appstore"));
			receiptExpire = eh.getLong("receiptExpire", 604800000l);
			Files.createDirectories(receiptQueueHome = home.resolve("queue"));
			Files.createDirectories(receiptFailHome = home.resolve("fail"));
			Path rpHome = home.resolve("replayprotector");
			Files.createDirectories(rpHome);
			rp = new ReplayProtector(rpHome, eh.getInt("receiptReplayProtectorConcurrentBits", 3));
			timeout = eh.getInt("connectTimeout", 15000);
			maxOutstanding = eh.getInt("maxOutstanding", 5);
			maxQueueCapacity = eh.getInt("maxQueueCapacity", 32);
			maxContentLength = eh.getInt("maxContentLength", 16384);
			HttpClientManager.getService().initHost(host, maxOutstanding, maxQueueCapacity);
			retryDelay = eh.getLong("retryDelay", 300000l);
			scheduler = ConcurrentEnvironment.getInstance().newScheduledThreadPool("AppStoreScheduler",
					eh.getInt("receiptVerifyScheduler", 4));
			List<Path> receiptPaths;
			try (Stream<Path> stream = Files.list(receiptQueueHome)) {
				receiptPaths = stream.filter(p -> p.getFileName().toString().startsWith("receipt."))
						.collect(Collectors.toList());
			}
			ByteBuffer bb = ByteBuffer.allocate(4096);
			List<Path> deletePaths = new ArrayList<>();
			List<Request> requests = new ArrayList<>();
			for (Path path : receiptPaths) {
				int size = (int) Files.size(path);
				if (bb.capacity() < size)
					bb = ByteBuffer.allocate(size);
				try (FileBundle.File file = FileBundle.open(path)) {
					bb.clear();
					file.read(bb, 0);
					bb.flip();
					if (bb.getInt() == 0)
						deletePaths.add(path);
					else
						requests.add(new Request(bb));
				}
			}
			for (Path p : deletePaths)
				Files.deleteIfExists(p);
			requests.forEach(r -> queue(r));
		}

		void unInitialize() {
			if (ref.decrementAndGet() > 0)
				return;
			ConcurrentEnvironment.getInstance().shutdown("AppStoreScheduler");
		}
	}
}
