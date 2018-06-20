package limax.auany;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PayOrder {
	private final static int RECORD_SIZE = 36;
	private final static AtomicInteger serialGenerator = new AtomicInteger();
	private static long EXPIRE;
	private static int POOL_SIZE;
	private static int POOL_MASK;
	private static FileBundle.File[] files;
	private static SortedMap<Long, PayOrder>[] outstanding;
	private static ByteBuffer[] buffers;
	private static PayLogger logger;

	private long serial;
	private final long sessionid;
	private final int gateway;
	private final int payid;
	private final int product;
	private final int price;
	private final int quantity;

	private int record;

	private PayOrder(long sessionid, int gateway, int payid, int product, int price, int quantity) {
		this.serial = serialAlloc();
		this.sessionid = sessionid;
		this.gateway = gateway;
		this.payid = payid;
		this.product = product;
		this.price = price;
		this.quantity = quantity;
	}

	private PayOrder(ByteBuffer bb) {
		this.serial = bb.getLong();
		this.sessionid = bb.getLong();
		this.gateway = bb.getInt();
		this.payid = bb.getInt();
		this.product = bb.getInt();
		this.price = bb.getInt();
		this.quantity = bb.getInt();
	}

	@Override
	public String toString() {
		return "[PayOrder:" + serial + "," + sessionid + "," + gateway + "," + payid + "," + product + "," + price + ","
				+ quantity + "]";
	}

	private void save(boolean trn) throws IOException {
		int i = (int) (serial & POOL_MASK);
		buffers[i].clear();
		buffers[i].putLong(serial).putLong(sessionid).putInt(gateway).putInt(payid).putInt(product).putInt(price)
				.putInt(quantity).flip();
		if (trn) {
			FileBundle.begin();
			try {
				files[i].write(buffers[i], (long) record * RECORD_SIZE);
			} finally {
				FileBundle.end();
			}
		} else
			files[i].write(buffers[i], (long) record * RECORD_SIZE);
	}

	public static long create(long sessionid, int gateway, int payid, int product, int price, int quantity)
			throws IOException {
		PayOrder o = new PayOrder(sessionid, gateway, payid, product, price, quantity);
		logger.logCreate(o);
		SortedMap<Long, PayOrder> map = outstanding[(int) (o.serial & POOL_MASK)];
		PayOrder expire = null;
		synchronized (map) {
			boolean remove = false;
			Iterator<Entry<Long, PayOrder>> iterator = map.entrySet().iterator();
			if (iterator.hasNext()) {
				Entry<Long, PayOrder> e = iterator.next();
				if ((e.getKey() & Long.MIN_VALUE) == Long.MIN_VALUE) {
					o.record = e.getValue().record;
					remove = true;
				} else if (((o.serial - e.getKey()) >> 16) > EXPIRE) {
					expire = e.getValue();
					o.record = expire.record;
					remove = true;
				} else
					o.record = map.size();
			} else {
				o.record = map.size();
			}
			o.save(true);
			if (remove)
				iterator.remove();
			map.put(o.serial, o);
		}
		if (expire != null)
			logger.logExpire(expire);
		return o.serial;
	}

	private interface IOP {
		Runnable run(long serial, long sessionid, int payid, int product, int price, int quantity) throws IOException;
	}

	public static PayOrder get(long serial) {
		Map<Long, PayOrder> map = outstanding[(int) (serial & POOL_MASK)];
		synchronized (map) {
			return map.get(serial);
		}
	}

	private static PayOrder ack(long serial, int gateway, IOP iop) throws IOException {
		Map<Long, PayOrder> map = outstanding[(int) (serial & POOL_MASK)];
		synchronized (map) {
			PayOrder o = map.get(serial);
			if (o == null || o.gateway != gateway) {
				logger.logFake(serial, gateway, o.gateway);
				return null;
			}
			Runnable r;
			FileBundle.begin();
			try {
				r = iop.run(o.serial, o.sessionid, o.payid, o.product, o.price, o.quantity);
				o.serial |= Long.MIN_VALUE;
				o.save(false);
				map.remove(serial);
				map.put(o.serial, o);
			} finally {
				o.serial &= Long.MAX_VALUE;
				FileBundle.end();
			}
			r.run();
			return o;
		}
	}

	public static void ok(long serial, int gateway) throws IOException {
		PayOrder o = ack(serial, gateway, PayDelivery::start);
		if (o != null)
			logger.logOk(o);
	}

	public static void fail(long serial, int gateway, String gatewayMessage) throws IOException {
		PayOrder o = ack(serial, gateway, (a, b, c, d, e, f) -> {
			return () -> {
			};
		});
		if (o != null)
			logger.logFail(o, gatewayMessage);
	}

	@SuppressWarnings("unchecked")
	static void initialize(Path orderQueueHome, int orderQueueConcurrencyBits, long orderExpire) throws IOException {
		if (orderQueueConcurrencyBits > 16 || orderQueueConcurrencyBits < 0)
			throw new IllegalArgumentException(
					"payQueueConcurrencyBits must in range [0,16], but " + orderQueueConcurrencyBits);
		Files.createDirectories(orderQueueHome);
		POOL_SIZE = 1 << orderQueueConcurrencyBits;
		POOL_MASK = POOL_SIZE - 1;
		EXPIRE = orderExpire;
		outstanding = new SortedMap[POOL_SIZE];
		buffers = new ByteBuffer[POOL_SIZE];
		files = new FileBundle.File[POOL_SIZE];
		List<Path> pathOutstandings;
		try (Stream<Path> stream = Files.list(orderQueueHome)) {
			pathOutstandings = stream.filter(p -> p.getFileName().toString().startsWith("outstanding."))
					.collect(Collectors.toList());
		}
		Path pathCommit = orderQueueHome.resolve("commit");
		if (!Files.exists(pathCommit)) {
			Path pathPrepare = orderQueueHome.resolve("prepare");
			try (FileChannel fcPrepare = FileChannel.open(pathPrepare, EnumSet.of(StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
				for (Path p : pathOutstandings)
					try (FileChannel fc = FileChannel.open(p, StandardOpenOption.READ)) {
						fc.transferTo(0, fc.size(), fcPrepare);
					}
				fcPrepare.force(false);
			}
			Files.move(pathPrepare, pathCommit, StandardCopyOption.ATOMIC_MOVE);
		}
		for (Path p : pathOutstandings)
			Files.deleteIfExists(p);
		for (int i = 0; i < POOL_SIZE; i++)
			outstanding[i] = new TreeMap<>();
		long now = System.currentTimeMillis();
		ByteBuffer bb = ByteBuffer.allocateDirect(RECORD_SIZE * 1024);
		List<PayOrder> expiredList = new ArrayList<>();
		try (FileChannel fcCommit = FileChannel.open(pathCommit, StandardOpenOption.READ)) {
			for (; fcCommit.read(bb) > 0; bb.clear())
				for (bb.flip(); bb.hasRemaining();) {
					PayOrder o = new PayOrder(bb);
					if ((o.serial & Long.MIN_VALUE) == Long.MIN_VALUE)
						;
					else if (now - (o.serial >> 16) > EXPIRE)
						expiredList.add(o);
					else
						outstanding[(int) (o.serial & POOL_MASK)].put(o.serial, o);
				}
		}
		for (int i = 0; i < POOL_SIZE; i++) {
			buffers[i] = ByteBuffer.allocateDirect(RECORD_SIZE);
			files[i] = FileBundle.open(orderQueueHome.resolve("outstanding." + i));
			int record = 0;
			for (PayOrder o : outstanding[i].values()) {
				o.record = record++;
				o.save(false);
			}
			files[i].force(false);
		}
		Files.deleteIfExists(pathCommit);
		logger = PayManager.getLogger();
		for (PayOrder o : expiredList)
			logger.logExpire(o);
	}

	static void unInitialize() {
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

	public int getGateway() {
		return gateway;
	}

	public int getProduct() {
		return product;
	}

	public int getPrice() {
		return price;
	}

	public int getQuantity() {
		return quantity;
	}

	public long getElapsed() {
		return System.currentTimeMillis() - (serial >> 16);
	}

	public static long serialAlloc() {
		return (System.currentTimeMillis() << 16) | (serialGenerator.getAndIncrement() & 0xffff);
	}
}
