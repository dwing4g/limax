package limax.auany;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import limax.auany.switcherauany.Pay;
import limax.auany.switcherauany.PayAck;
import limax.net.Transport;
import limax.util.ConcurrentEnvironment;
import limax.util.Trace;

public final class PayDelivery {
	private final static int RECORD_SIZE = 32;
	private static long EXPIRE;
	private static long CHECKPERIOD;
	private static int BACKOFF_MAX;
	private static int POOL_SIZE;
	private static int POOL_MASK;
	private static ScheduledExecutorService queueScheduler;
	private static FileBundle.File[] files;
	private static Map<Long, PayDelivery>[] delivering;
	private static ByteBuffer[] buffers;
	private static BitSet[] slots;

	private final long serial;
	private final long sessionid;
	private final int payid;
	private final int product;
	private final int price;
	private volatile int quantity;

	private volatile int backoff;
	private volatile int record;
	private volatile Future<?> future;

	private PayDelivery(long serial, long sessionid, int payid, int product, int price, int quantity) {
		this.serial = serial;
		this.sessionid = sessionid;
		this.payid = payid;
		this.product = product;
		this.price = price;
		this.quantity = quantity;
	}

	private PayDelivery(ByteBuffer bb) {
		this.serial = bb.getLong();
		this.sessionid = bb.getLong();
		this.payid = bb.getInt();
		this.product = bb.getInt();
		this.price = bb.getInt();
		this.quantity = bb.getInt();
	}

	private void save(boolean trn) throws IOException {
		int i = (int) (serial & POOL_MASK);
		buffers[i].clear();
		buffers[i].putLong(serial).putLong(sessionid).putInt(payid).putInt(product).putInt(price).putInt(quantity)
				.flip();
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

	private boolean queue(Transport transport) {
		if (transport != null)
			try {
				if (quantity > 0)
					new Pay(payid, serial, sessionid, product, price, quantity).send(transport);
				else
					new PayAck(payid, serial).send(transport);
				return true;
			} catch (Exception e) {
				if (Trace.isInfoEnabled())
					Trace.info("PayDelivery.queue " + (quantity > 0 ? "PAY" : "ACK") + ",payid=" + payid + ",serial="
							+ Long.toString(serial, Character.MAX_RADIX) + ",sessionid=" + sessionid);
			}
		return false;
	}

	private void queue() {
		if (quantity < 0)
			return;
		if (System.currentTimeMillis() - (serial >> 16) > EXPIRE) {
			int i = (int) (serial & POOL_MASK);
			Map<Long, PayDelivery> map = delivering[i];
			synchronized (map) {
				int save = quantity;
				quantity = -1;
				try {
					save(true);
					delivering[i].remove(serial);
					slots[i].clear(record);
					quantity = save;
					PayManager.getLogger().logDead(this);
				} catch (IOException e) {
					quantity = save;
				}
			}
		}
		if (queue(SessionManager.getTransport(payid)))
			backoff = 1;
		else if (backoff < BACKOFF_MAX)
			backoff++;
		synchronized (this) {
			if (future != null)
				future.cancel(false);
			future = queueScheduler.scheduleWithFixedDelay(() -> queue(), CHECKPERIOD * backoff, Long.MAX_VALUE,
					TimeUnit.MILLISECONDS);
		}
	}

	public static Runnable start(long serial, long sessionid, int payid, int product, int price, int quantity)
			throws IOException {
		PayDelivery pd = new PayDelivery(serial, sessionid, payid, product, price, quantity);
		int i = (int) (serial & POOL_MASK);
		Map<Long, PayDelivery> map = delivering[i];
		BitSet slot = slots[i];
		synchronized (map) {
			pd.record = slot.nextClearBit(0);
			pd.save(false);
			slot.set(pd.record);
			map.put(serial, pd);
		}
		return () -> pd.queue();
	}

	static void ack(PayAck ack) {
		int i = (int) (ack.serial & POOL_MASK);
		Map<Long, PayDelivery> map = delivering[i];
		synchronized (map) {
			PayDelivery pd = map.get(ack.serial);
			if (pd == null)
				return;
			int quantity = pd.quantity;
			if (quantity > 0) {
				pd.quantity = 0;
				try {
					pd.save(true);
					pd.queue();
				} catch (IOException e) {
					pd.quantity = quantity;
				}
			} else {
				pd.quantity = -1;
				try {
					pd.save(true);
					map.remove(ack.serial);
					slots[i].clear(pd.record);
				} catch (IOException e) {
					pd.quantity = quantity;
				}
			}
		}
	}

	static void flushQueue(int payid, Transport transport) {
		queueScheduler.execute(() -> {
			for (int i = 0; i < POOL_SIZE; i++) {
				Map<Long, PayDelivery> map = delivering[i];
				synchronized (map) {
					map.values().stream().filter(pd -> pd.payid == payid).forEach(pd -> pd.queue(transport));
				}
			}
		});
	}

	@SuppressWarnings("unchecked")
	static void initialize(Path deliveryQueueHome, int deliveryQueueConcurrencyBits, long deliveryExpire,
			long deliveryQueueCheckPeriod, int deliveryQueueBackoffMax, int deliveryQueueScheduler) throws IOException {
		if (deliveryQueueConcurrencyBits > 16 || deliveryQueueConcurrencyBits < 0)
			throw new IllegalArgumentException(
					"deliveryQueueConcurrencyBits must in range [0,16], but " + deliveryQueueConcurrencyBits);
		Files.createDirectories(deliveryQueueHome);
		POOL_SIZE = 1 << deliveryQueueConcurrencyBits;
		POOL_MASK = POOL_SIZE - 1;
		EXPIRE = deliveryExpire;
		CHECKPERIOD = deliveryQueueCheckPeriod;
		BACKOFF_MAX = deliveryQueueBackoffMax;
		delivering = new Map[POOL_SIZE];
		buffers = new ByteBuffer[POOL_SIZE];
		files = new FileBundle.File[POOL_SIZE];
		slots = new BitSet[POOL_SIZE];
		List<Path> pathDeliverings;
		try (Stream<Path> stream = Files.list(deliveryQueueHome)) {
			pathDeliverings = stream.filter(p -> p.getFileName().toString().startsWith("delivering."))
					.collect(Collectors.toList());
		}
		Path pathCommit = deliveryQueueHome.resolve("commit");
		if (!Files.exists(pathCommit)) {
			Path pathPrepare = deliveryQueueHome.resolve("prepare");
			try (FileChannel fcPrepare = FileChannel.open(pathPrepare, EnumSet.of(StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
				for (Path p : pathDeliverings)
					try (FileChannel fc = FileChannel.open(p, StandardOpenOption.READ)) {
						fc.transferTo(0, fc.size(), fcPrepare);
					}
				fcPrepare.force(false);
			}
			Files.move(pathPrepare, pathCommit, StandardCopyOption.ATOMIC_MOVE);
		}
		for (Path p : pathDeliverings)
			Files.deleteIfExists(p);
		for (int i = 0; i < POOL_SIZE; i++)
			delivering[i] = new HashMap<>();
		long now = System.currentTimeMillis();
		List<PayDelivery> expiredList = new ArrayList<>();
		ByteBuffer bb = ByteBuffer.allocateDirect(RECORD_SIZE * 1024);
		try (FileChannel fcCommit = FileChannel.open(pathCommit, StandardOpenOption.READ)) {
			for (; fcCommit.read(bb) > 0; bb.clear())
				for (bb.flip(); bb.hasRemaining();) {
					PayDelivery pd = new PayDelivery(bb);
					if (pd.quantity >= 0)
						if (now - (pd.serial >> 16) > EXPIRE)
							expiredList.add(pd);
						else
							delivering[(int) (pd.serial & POOL_MASK)].put(pd.serial, pd);
				}
		}
		queueScheduler = ConcurrentEnvironment.getInstance().newScheduledThreadPool("PayDeliveryQueueScheduler",
				deliveryQueueScheduler);
		for (int i = 0; i < POOL_SIZE; i++) {
			buffers[i] = ByteBuffer.allocateDirect(RECORD_SIZE);
			files[i] = FileBundle.open(deliveryQueueHome.resolve("delivering." + i));
			int record = 0;
			for (PayDelivery pd : delivering[i].values()) {
				pd.record = record++;
				pd.save(false);
				pd.queue();
			}
			files[i].force(false);
			slots[i] = new BitSet(record);
			slots[i].set(0, record);
		}
		Files.deleteIfExists(pathCommit);
		for (PayDelivery pd : expiredList)
			PayManager.getLogger().logDead(pd);
	}

	static void unInitialize() {
		ConcurrentEnvironment.getInstance().shutdown("PayDeliveryQueueScheduler");
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

	public int getPrice() {
		return price;
	}

	public int getQuantity() {
		return quantity;
	}

	public long getElapsed() {
		return System.currentTimeMillis() - (serial >> 16);
	}

}
