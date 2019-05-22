package limax.http;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import limax.codec.Octets;
import limax.http.RFC7541.Entry;
import limax.util.Pair;

class RFC7540 {
	public enum FrameType {
		DATA, HEADERS, PRIORITY, RST_STREAM, SETTINGS, PUSH_PROMISE, PING, GOAWAY, WINDOW_UPDATE, CONTINUATION;
		public byte registry() {
			return (byte) ordinal();
		}

		public static FrameType valueOf(byte registry) {
			try {
				return FrameType.values()[registry];
			} catch (Exception e) {
				return null;
			}
		}
	}

	public enum Settings {
		SETTINGS_TIMEOUT(-1, 0, Long.MAX_VALUE, 0), PING_TIMEOUT(-2, 0, Long.MAX_VALUE, 0),
		INCOMPLETE_FRAME_TIMEOUT(-3, 0, Long.MAX_VALUE, 0), IDLE_TIMEOUT(-4, 0, Long.MAX_VALUE, 0),
		CONNECTION_WINDOW_SIZE(-5, 0, Integer.MAX_VALUE, 65535), WINDOW_UPDATE_ACCUMULATE_PERCENT(-6, 0, 100, 80),
		RTT_MEASURE_PERIOD(-7, 1000, Long.MAX_VALUE, 1000), RTT_SAMPLES(-8, 1, Long.MAX_VALUE, 1),
		HEADER_TABLE_SIZE(1, 0, Integer.toUnsignedLong(-1), 4096), ENABLE_PUSH(2, 0, 1, 1),
		MAX_CONCURRENT_STREAMS(3, 0, Integer.toUnsignedLong(-1), Integer.toUnsignedLong(-1)),
		INITIAL_WINDOW_SIZE(4, 0, Integer.MAX_VALUE, 65535), MAX_FRAME_SIZE(5, 16384, 16777215, 16384),
		MAX_HEADER_LIST_SIZE(6, 0, Integer.toUnsignedLong(-1), Integer.toUnsignedLong(-1)),
		SETTINGS_ENABLE_CONNECT_PROTOCOL(8, 0, 1, 0);
		private final short registry;
		private final long low;
		private final long high;
		private final long def;

		Settings(int registry, long low, long high, long def) {
			this.registry = (short) registry;
			this.low = low;
			this.high = high;
			this.def = def;
		}

		public short registry() {
			return registry;
		}

		public long def() {
			return def;
		}

		public boolean validate(long value) {
			return value >= low && value <= high;
		}

		public static Settings valueOf(short registry) {
			for (Settings settings : Settings.values())
				if (settings.registry == registry)
					return settings;
			return null;
		}
	}

	public enum ErrorCode {
		NO_ERROR, PROTOCOL_ERROR, INTERNAL_ERROR, FLOW_CONTROL_ERROR, SETTINGS_TIMEOUT, STREAM_CLOSED, FRAME_SIZE_ERROR,
		REFUSED_STREAM, CANCEL, COMPRESSION_ERROR, CONNECT_ERROR, ENHANCE_YOUR_CALM, INADEQUATE_SECURITY,
		HTTP_1_1_REQUIRED;

		public int registry() {
			return ordinal();
		}

		public static ErrorCode valueOf(int registry) {
			try {
				return ErrorCode.values()[registry];
			} catch (Exception e) {
				return INTERNAL_ERROR;
			}
		}
	}

	private static class IncomingException extends Exception {
		private static final long serialVersionUID = -420679276985359612L;
		private final ErrorCode errorCode;

		IncomingException(ErrorCode errorCode) {
			this.errorCode = errorCode;
		}

		ErrorCode getErrorCode() {
			return errorCode;
		}
	}

	private enum Flag {
		ACK(1), END_STREAM(1), END_HEADERS(4), PADDED(8), PRIORITY(32);

		private final byte value;

		Flag(int value) {
			this.value = (byte) value;
		}

		byte value() {
			return value;
		}

		boolean test(byte flag) {
			return (flag & value) != 0;
		}
	}

	private static boolean isServerStreamIdentifier(int sid) {
		return (sid & 1) == 0;
	}

	private static boolean isClientStreamIdentifier(int sid) {
		return (sid & 1) == 1;
	}

	public static class Stream implements Function<Integer, Integer> {
		private final static int IDLE = 0;
		private final static int RESERVED_LOCAL = 1;
		private final static int RESERVED_REMOTE = 2;
		private final static int OPEN = 3;
		private final static int HALFCLOSED_LOCAL = 4;
		private final static int HALFCLOSED_REMOTE = 5;
		private final static int CLOSED = 6;

		private final static int HEADERS = 0;
		private final static int PUSH_PROMISE = 1;
		private final static int END_STREAM = 2;
		private final static int RST_STREAM = 3;

		private final static int[][] recvTransitions = new int[7][4];
		private final static int[][] sendTransitions = new int[7][4];

		static {
			for (int i = 0; i < 7; i++)
				for (int j = 0; j < 4; j++)
					recvTransitions[i][j] = sendTransitions[i][j] = -1;
			recvTransitions[IDLE][HEADERS] = OPEN;
			sendTransitions[IDLE][HEADERS] = OPEN;
			recvTransitions[IDLE][PUSH_PROMISE] = RESERVED_REMOTE;
			sendTransitions[IDLE][PUSH_PROMISE] = RESERVED_LOCAL;
			sendTransitions[RESERVED_LOCAL][HEADERS] = HALFCLOSED_REMOTE;
			recvTransitions[RESERVED_LOCAL][RST_STREAM] = CLOSED;
			sendTransitions[RESERVED_LOCAL][RST_STREAM] = CLOSED;
			recvTransitions[RESERVED_REMOTE][HEADERS] = HALFCLOSED_LOCAL;
			sendTransitions[RESERVED_REMOTE][RST_STREAM] = CLOSED;
			recvTransitions[RESERVED_REMOTE][RST_STREAM] = CLOSED;
			recvTransitions[OPEN][HEADERS] = OPEN;
			sendTransitions[OPEN][HEADERS] = OPEN;
			recvTransitions[OPEN][END_STREAM] = HALFCLOSED_REMOTE;
			sendTransitions[OPEN][END_STREAM] = HALFCLOSED_LOCAL;
			recvTransitions[OPEN][RST_STREAM] = CLOSED;
			sendTransitions[OPEN][RST_STREAM] = CLOSED;
			sendTransitions[HALFCLOSED_REMOTE][HEADERS] = HALFCLOSED_REMOTE;
			sendTransitions[HALFCLOSED_REMOTE][END_STREAM] = CLOSED;
			recvTransitions[HALFCLOSED_REMOTE][RST_STREAM] = CLOSED;
			sendTransitions[HALFCLOSED_REMOTE][RST_STREAM] = CLOSED;
			recvTransitions[HALFCLOSED_LOCAL][HEADERS] = HALFCLOSED_LOCAL;
			recvTransitions[HALFCLOSED_LOCAL][END_STREAM] = CLOSED;
			sendTransitions[HALFCLOSED_LOCAL][RST_STREAM] = CLOSED;
			recvTransitions[HALFCLOSED_LOCAL][RST_STREAM] = CLOSED;
		}

		private static boolean isActive(int state) {
			return state == OPEN || state == HALFCLOSED_LOCAL || state == HALFCLOSED_REMOTE;
		}

		private volatile int state = IDLE;
		private int recvtype;
		private volatile int sendtype;

		private int recvTransitions(int type) throws IncomingException {
			int nextState = recvTransitions[state][type];
			if (nextState != -1) {
				recvtype = type;
				return nextState;
			}
			if (state == CLOSED && (type == RST_STREAM || sendtype == RST_STREAM)) {
				return CLOSED;
			}
			if (state == HALFCLOSED_REMOTE || recvtype == RST_STREAM) {
				error(ErrorCode.STREAM_CLOSED);
				return CLOSED;
			}
			throw new IncomingException(recvtype == END_STREAM ? ErrorCode.STREAM_CLOSED : ErrorCode.PROTOCOL_ERROR);
		}

		private int sendTransitions(int type) {
			int nextState = sendTransitions[state][type];
			if (nextState != -1)
				sendtype = type;
			return nextState;
		}

		private void transit(int nextState) {
			boolean s0 = isActive(state);
			boolean s1 = isActive(nextState);
			state = nextState;
			if (!s0 && s1 && !connection.countUp(this))
				error(ErrorCode.REFUSED_STREAM);
			if (s0 && !s1) {
				pump = null;
				connection.countDown(this);
			}
		}

		private final Connection connection;
		private final int sid;
		private final int windowUpdateAccumulateThreshold;
		private final AtomicInteger windowUpdateAccumulate = new AtomicInteger();
		private int streamWindow;
		private Processor processor;
		private volatile Consumer<Integer> pump;
		private List<Entry> headers = new ArrayList<>();
		private Long headersSize = 0L;
		private List<Entry> error;

		Stream(Connection connection, int sid) {
			this.connection = connection;
			this.sid = sid;
			this.windowUpdateAccumulateThreshold = (int) ((long) connection.SETTINGS_INITIAL_WINDOW_SIZE_LOCAL
					* connection.windowUpdateAccumulatePercent / 100);
			this.streamWindow = connection.SETTINGS_INITIAL_WINDOW_SIZE_PEER;
		}

		void startup(Processor processor) {
			this.processor = processor;
		}

		void error(ErrorCode errorCode) {
			sendReset(errorCode);
		}

		boolean isClosed() {
			return state == CLOSED;
		}

		private void decodeHeaderData(ByteBuffer data) throws IncomingException {
			try {
				connection.irfc7541.decode(data.slice(), e -> {
					if (headersSize == null)
						return;
					headersSize += e.size();
					if (headersSize <= connection.SETTINGS_MAX_HEADER_LIST_SIZE_LOCAL) {
						headers.add(e);
					} else {
						headers.clear();
						headersSize = null;
						if (connection.isServer())
							error = Collections.singletonList(new Entry(":status", "431"));
					}
				});
			} catch (Exception e) {
				throw new IncomingException(ErrorCode.COMPRESSION_ERROR);
			}
		}

		private boolean delayEnd;

		void recvHeaders(ByteBuffer data, boolean es, boolean eh) throws IncomingException {
			int nextState = recvTransitions(HEADERS);
			decodeHeaderData(data);
			if (connection.ignore(sid))
				return;
			if (eh) {
				processor.process(headers);
				headers.clear();
				transit(nextState);
			}
			if (es) {
				if (eh)
					processor.end();
				else
					delayEnd = true;
				transit(recvTransitions(END_STREAM));
			}
		}

		void recvPromise(Stream associateStream, ByteBuffer data, boolean eh) throws IncomingException {
			int nextState = recvTransitions(PUSH_PROMISE);
			decodeHeaderData(data);
			if (connection.ignore(sid))
				return;
			if (eh) {
				processor.promise(headers);
				headers.clear();
				transit(nextState);
			}
			if (associateStream.state != OPEN && associateStream.state != HALFCLOSED_LOCAL
					&& associateStream.sendtype != RST_STREAM)
				throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
		}

		void recvContinuation(ByteBuffer data, boolean eh) throws IncomingException {
			int nextState = state == RESERVED_REMOTE ? RESERVED_REMOTE : recvTransitions(HEADERS);
			decodeHeaderData(data);
			if (connection.ignore(sid))
				return;
			if (eh) {
				if (state == RESERVED_REMOTE)
					processor.promise(headers);
				else {
					processor.process(headers);
					if (delayEnd)
						processor.end();
				}
				headers.clear();
				transit(nextState);
			}
		}

		void recvData(ByteBuffer data, boolean es) throws Exception {
			int nextState = recvTransitions(END_STREAM);
			processor.process(data);
			if (es) {
				processor.end();
				transit(nextState);
			}
		}

		void windowUpdate(int inc) {
			int prev, next;
			do {
				next = (prev = windowUpdateAccumulate.get()) + inc;
				if (next > windowUpdateAccumulateThreshold) {
					inc = next;
					next = 0;
				} else
					inc = 0;
			} while (!windowUpdateAccumulate.compareAndSet(prev, next));
			if (inc != 0) {
				connection.sendWindowUpdate(sid, inc);
				connection.sendWindowUpdate(inc);
			}
		}

		void recvReset(ErrorCode errorCode) throws IncomingException {
			processor.reset(errorCode);
			transit(recvTransitions(RST_STREAM));
		}

		void changeInitialWindow(int delta) {
			streamWindow += delta;
		}

		void recvWindowUpdate(int inc) {
			if (streamWindow < 0)
				streamWindow += inc;
			else if ((streamWindow += inc) < 0)
				error(ErrorCode.FLOW_CONTROL_ERROR);
			connection.flush(true);
		}

		public void setPump(Consumer<Integer> pump) {
			if (sendTransitions(END_STREAM) != -1) {
				this.pump = pump;
				connection.flush(true);
			}
		}

		public void flush(Supplier<Boolean> action) {
			connection.flush(action);
		}

		@Override
		public Integer apply(Integer window) {
			Consumer<Integer> pump = this.pump;
			if (pump == null)
				return 0;
			int min = Math.min(window, streamWindow);
			if (min <= 0)
				return Integer.MIN_VALUE;
			int pre = streamWindow;
			pump.accept(min);
			return pre - streamWindow;
		}

		private ByteBuffer createFrame(FrameType type, int flags, Octets payload) {
			int length = payload.size();
			ByteBuffer bb = ByteBuffer.allocate(length + 9);
			bb.put((byte) (length >> 16));
			bb.put((byte) (length >> 8));
			bb.put((byte) length);
			bb.put(type.registry());
			bb.put((byte) flags);
			bb.putInt(sid);
			bb.put(payload.array(), 0, payload.size());
			bb.flip();
			return bb;
		}

		public ErrorCode sendHeaders(List<Entry> headers, boolean es) {
			int nextState = sendTransitions(HEADERS);
			if (nextState == -1)
				return ErrorCode.PROTOCOL_ERROR;
			List<Entry> oheaders = error != null ? error : headers;
			if (oheaders.stream().mapToLong(e -> e.size()).sum() > connection.SETTINGS_MAX_HEADER_LIST_SIZE_PEER)
				return ErrorCode.CANCEL;
			List<Octets> payloads = connection.encode(
					encoder -> oheaders.forEach(e -> encoder.add(e.getKey(), e.getValue(), RFC7541.UpdateMode.UPDATE)));
			int count = payloads.size();
			int flags = es ? Flag.END_STREAM.value() : 0;
			if (count == 1) {
				connection.queue(createFrame(FrameType.HEADERS, flags | Flag.END_HEADERS.value(), payloads.get(0)));
			} else {
				ByteBuffer[] bbs = new ByteBuffer[count--];
				bbs[0] = createFrame(FrameType.HEADERS, flags, payloads.get(0));
				for (int i = 1; i < count; i++)
					bbs[i] = createFrame(FrameType.CONTINUATION, 0, payloads.get(i));
				bbs[count] = createFrame(FrameType.CONTINUATION, Flag.END_HEADERS.value(), payloads.get(count));
				connection.queue(bbs);
			}
			transit(nextState);
			return ErrorCode.NO_ERROR;
		}

		public Stream sendPromise(List<Entry> headers) {
			if (!connection.isServer() || isServerStreamIdentifier(sid) || connection.SETTINGS_ENABLE_PUSH != 1)
				return null;
			if (state != OPEN && state != HALFCLOSED_REMOTE)
				return null;
			if (headers.stream().mapToLong(e -> e.size()).sum() > connection.SETTINGS_MAX_HEADER_LIST_SIZE_PEER)
				return null;
			Stream promise = connection.createActiveStream();
			if (promise == null)
				return null;
			List<Octets> payloads = connection.encode(
					encoder -> headers.forEach(e -> encoder.add(e.getKey(), e.getValue(), RFC7541.UpdateMode.UPDATE)));
			int count = payloads.size();
			Octets payload = payloads.get(0);
			int length = payload.size() + 4;
			ByteBuffer bb = ByteBuffer.allocate(length + 9);
			bb.put((byte) (length >> 16));
			bb.put((byte) (length >> 8));
			bb.put((byte) length);
			bb.put(FrameType.PUSH_PROMISE.registry());
			bb.put((byte) --count == 0 ? Flag.END_HEADERS.value() : 0);
			bb.putInt(sid);
			bb.putInt(promise.sid);
			bb.put(payload.array(), 0, payload.size());
			connection.queueFlip(bb);
			if (count > 0) {
				ByteBuffer[] bbs = new ByteBuffer[count];
				for (int i = 1; i < count; i++)
					bbs[i - 1] = createFrame(FrameType.CONTINUATION, 0, payloads.get(i));
				bbs[count - 1] = createFrame(FrameType.CONTINUATION, Flag.END_HEADERS.value(), payloads.get(count - 1));
				connection.queue(bbs);
			}
			promise.state = RESERVED_LOCAL;
			return promise;
		}

		private final static byte[] FH_RST_STREAM = new byte[] { 0, 0, 4, FrameType.RST_STREAM.registry(), 0 };

		public void sendReset(ErrorCode errorCode) {
			int nextState = sendTransitions(RST_STREAM);
			if (nextState != -1) {
				connection
						.queueFlip(ByteBuffer.allocate(13).put(FH_RST_STREAM).putInt(sid).putInt(errorCode.registry()));
				transit(nextState);
			}
		}

		public void sendData(ByteBuffer data, boolean es) {
			int nextState = sendTransitions(END_STREAM);
			if (nextState != -1) {
				streamWindow -= connection.sendData(sid, data, es);
				if (es)
					transit(nextState);
			}
		}

		public void sendData(ByteBuffer[] datas, boolean es) {
			int nextState = sendTransitions(END_STREAM);
			if (nextState != -1) {
				streamWindow -= connection.sendData(sid, datas, es);
				if (es)
					transit(nextState);
			}
		}

		void shutdown(Throwable closeReason) {
			processor.shutdown(closeReason);
		}
	}

	public static class Connection implements Consumer<Integer> {
		private int length;
		private FrameType type;
		private byte flag;
		private int sid;
		private Octets partial;
		private int stage = 0;

		private void unPadding(ByteBuffer data) throws IncomingException {
			if (Flag.PADDED.test(flag)) {
				int plen = data.get() & 0xff;
				if (plen >= length)
					throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
				data.limit(data.limit() - plen);
			}
		}

		public synchronized long unwrap(ByteBuffer in) throws Exception {
			long timeout = incompleteFrameTimeout;
			while (true) {
				for (; stage < 9 && in.hasRemaining(); stage++) {
					int b = in.get() & 0xff;
					switch (stage) {
					case 0:
						length = b << 16;
						break;
					case 1:
						length |= b << 8;
						break;
					case 2:
						length |= b;
						break;
					case 3:
						type = FrameType.valueOf((byte) b);
						break;
					case 4:
						flag = (byte) b;
						break;
					case 5:
						sid = b << 24;
						break;
					case 6:
						sid |= b << 16;
						break;
					case 7:
						sid |= b << 8;
						break;
					case 8:
						sid |= b;
						sid &= Integer.MAX_VALUE;
						break;
					}
				}
				if (stage < 9)
					return timeout;
				int want = length - (partial == null ? 0 : partial.size());
				int remaining = in.remaining();
				if (want > remaining) {
					if (partial == null)
						partial = new Octets();
					partial.append(in.array(), in.position(), remaining);
					in.position(in.limit());
					return timeout;
				}
				ByteBuffer data;
				if (want < length) {
					partial.append(in.array(), in.position(), want);
					data = partial.getByteBuffer();
					partial = null;
				} else {
					data = ByteBuffer.wrap(in.array(), in.position(), want);
				}
				in.position(in.position() + want);
				stage = 0;
				timeout = idelTimeout;
				if (type != null)
					try {
						if (length > SETTINGS_MAX_FRAME_SIZE_LOCAL)
							throw new IncomingException(ErrorCode.FRAME_SIZE_ERROR);
						switch (type) {
						case DATA: {
							if (sid == 0)
								throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
							unPadding(data);
							if (!ignore(sid))
								findStream(sid).recvData(data, Flag.END_STREAM.test(flag));
							else
								sendWindowUpdate(data.remaining());
							break;
						}
						case HEADERS: {
							if (sid == 0)
								throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
							unPadding(data);
							Stream stream;
							if (Flag.PRIORITY.test(flag)) {
								if (data.remaining() < 5)
									throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
								stream = createPassiveStream(sid, data);
							} else
								stream = createPassiveStream(sid, null);
							stream.recvHeaders(data, Flag.END_STREAM.test(flag), Flag.END_HEADERS.test(flag));
							break;
						}
						case PRIORITY: {
							if (sid == 0)
								throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
							if (length != 5)
								findStream(sid).error(ErrorCode.FRAME_SIZE_ERROR);
							else
								execute(prioritization.prepare(sid, data));
							break;
						}
						case RST_STREAM: {
							if (sid == 0)
								throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
							if (length != 4)
								findStream(sid).error(ErrorCode.FRAME_SIZE_ERROR);
							else
								findStream(sid).recvReset(ErrorCode.valueOf(data.getInt()));
							break;
						}
						case SETTINGS: {
							if (sid != 0)
								throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
							if (Flag.ACK.test(flag)) {
								if (length != 0)
									throw new IncomingException(ErrorCode.FRAME_SIZE_ERROR);
								recvSettingAck();
							} else {
								if ((length % 6) != 0)
									throw new IncomingException(ErrorCode.FRAME_SIZE_ERROR);
								recvSetting(data);
							}
							break;
						}
						case PUSH_PROMISE: {
							if (sid == 0 || isServer() || SETTINGS_ENABLE_PUSH == 0)
								throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
							unPadding(data);
							int psid = data.getInt() & Integer.MAX_VALUE;
							if (isClientStreamIdentifier(psid))
								throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
							createPassiveStream(psid, null).recvPromise(findStream(sid), data,
									Flag.END_HEADERS.test(flag));
							break;
						}
						case PING: {
							if (sid != 0)
								throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
							if (length != 8)
								throw new IncomingException(ErrorCode.FRAME_SIZE_ERROR);
							recvPing(data, Flag.ACK.test(flag));
							break;
						}
						case GOAWAY: {
							if (sid != 0)
								throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
							if (length < 8)
								throw new IncomingException(ErrorCode.FRAME_SIZE_ERROR);
							recvGoaway(data.getInt() & Integer.MAX_VALUE, ErrorCode.valueOf(data.getInt()), data);
							break;
						}
						case WINDOW_UPDATE: {
							if (length != 4)
								throw new IncomingException(ErrorCode.FRAME_SIZE_ERROR);
							int inc = data.getInt() & Integer.MAX_VALUE;
							if (inc == 0) {
								if (sid == 0)
									throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
								findStream(sid).error(ErrorCode.PROTOCOL_ERROR);
							} else {
								if (sid == 0)
									execute(() -> recvWindowUpdate(inc));
								else {
									Stream stream = findStream(sid);
									execute(() -> stream.recvWindowUpdate(inc));
								}
							}
							break;
						}
						case CONTINUATION: {
							if (sid == 0)
								throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
							findStream(sid).recvContinuation(data, Flag.END_HEADERS.test(flag));
							break;
						}
						}
					} catch (IncomingException e) {
						error(e.getErrorCode());
						return timeout;
					}
			}
		}

		private final TreeMap<Integer, Stream> map = new TreeMap<>();

		private Stream findStream(int sid) throws IncomingException {
			Stream stream = map.get(sid);
			if (stream == null)
				throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
			return stream;
		}

		private int lastStreamIdentifier = 0;

		Stream createPassiveStream(int sid, ByteBuffer priorityData) throws IncomingException {
			if (sid <= lastStreamIdentifier || (isServer() && isServerStreamIdentifier(sid))
					|| (!isServer() && isClientStreamIdentifier(sid)))
				throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
			lastStreamIdentifier = sid;
			Stream stream = new Stream(this, sid);
			map.put(sid, stream);
			stream.startup(exchange.createProcessor(stream));
			Runnable prepare = prioritization.prepare(sid, priorityData);
			execute(() -> {
				prepare.run();
				prioritization.setup(sid, stream);
			});
			return stream;
		}

		void error(ErrorCode errorCode) {
			sendGoaway(errorCode);
		}

		private int SETTINGS_ENABLE_PUSH = (int) Settings.ENABLE_PUSH.def();
		private long SETTINGS_MAX_CONCURRENT_STREAMS = Settings.MAX_CONCURRENT_STREAMS.def();
		private int SETTINGS_INITIAL_WINDOW_SIZE_LOCAL = (int) Settings.INITIAL_WINDOW_SIZE.def();
		private int SETTINGS_INITIAL_WINDOW_SIZE_PEER = (int) Settings.INITIAL_WINDOW_SIZE.def();
		private int SETTINGS_MAX_FRAME_SIZE_LOCAL = (int) Settings.MAX_FRAME_SIZE.def();
		private int SETTINGS_MAX_FRAME_SIZE_PEER = (int) Settings.MAX_FRAME_SIZE.def();
		private long SETTINGS_MAX_HEADER_LIST_SIZE_LOCAL = Settings.MAX_HEADER_LIST_SIZE.def();
		private long SETTINGS_MAX_HEADER_LIST_SIZE_PEER = Settings.MAX_HEADER_LIST_SIZE.def();

		private void sendSetting(Map<Settings, Long> map, byte flag) {
			int length = map.size() * 6;
			ByteBuffer bb = ByteBuffer.allocate(length + 9);
			bb.put((byte) (length >> 16));
			bb.put((byte) (length >> 8));
			bb.put((byte) length);
			bb.put(FrameType.SETTINGS.registry());
			bb.put(flag);
			bb.putInt(0);
			map.forEach((k, v) -> {
				bb.putShort(k.registry());
				bb.putInt(v.intValue());
			});
			queueFlip(bb);
		}

		private Map<Settings, Long> outstandingSettings;
		private long settingsTimestamp;
		private Future<?> futureSettings;

		public void sendSettings(Map<Settings, Long> settings, long SETTINGS_TIMEOUT) {
			synchronized (this) {
				if (outstandingSettings != null)
					return;
				outstandingSettings = new EnumMap<>(settings);
				settingsTimestamp = System.currentTimeMillis();
			}
			futureSettings = exchange.getScheduler().schedule(() -> error(ErrorCode.SETTINGS_TIMEOUT), SETTINGS_TIMEOUT,
					TimeUnit.MILLISECONDS);
			sendSetting(settings, (byte) 0);
		}

		private void recvSettingAck() {
			futureSettings.cancel(false);
			updateRTT(System.currentTimeMillis() - settingsTimestamp);
			outstandingSettings.forEach((settings, v) -> {
				int value = v.intValue();
				switch (settings) {
				case HEADER_TABLE_SIZE:
					synchronized (orfc7541) {
						orfc7541.update(value);
					}
					break;
				case ENABLE_PUSH:
					SETTINGS_ENABLE_PUSH = value;
					break;
				case MAX_CONCURRENT_STREAMS:
					SETTINGS_MAX_CONCURRENT_STREAMS = (int) Math.min(SETTINGS_MAX_CONCURRENT_STREAMS,
							Integer.toUnsignedLong(value));
					break;
				case INITIAL_WINDOW_SIZE:
					SETTINGS_INITIAL_WINDOW_SIZE_LOCAL = value;
					break;
				case MAX_FRAME_SIZE:
					SETTINGS_MAX_FRAME_SIZE_LOCAL = value;
					break;
				case MAX_HEADER_LIST_SIZE:
					SETTINGS_MAX_HEADER_LIST_SIZE_LOCAL = value;
					break;
				default:
				}
			});
			outstandingSettings = null;
		}

		private void updateSettings(ByteBuffer data) throws IncomingException {
			while (data.hasRemaining()) {
				Settings settings = Settings.valueOf(data.getShort());
				int value = data.getInt();
				if (settings == null)
					continue;
				switch (settings) {
				case HEADER_TABLE_SIZE:
					irfc7541.update(value);
					break;
				case ENABLE_PUSH:
					if (!settings.validate(value))
						throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
					SETTINGS_ENABLE_PUSH = value;
					break;
				case MAX_CONCURRENT_STREAMS:
					SETTINGS_MAX_CONCURRENT_STREAMS = (int) Math.min(SETTINGS_MAX_CONCURRENT_STREAMS,
							Integer.toUnsignedLong(value));
					break;
				case INITIAL_WINDOW_SIZE:
					if (!settings.validate(value))
						throw new IncomingException(ErrorCode.FLOW_CONTROL_ERROR);
					int delta = value - SETTINGS_INITIAL_WINDOW_SIZE_PEER;
					List<Stream> exists = map.values().stream().filter(s -> !s.isClosed()).collect(Collectors.toList());
					execute(() -> exists.forEach(s -> s.changeInitialWindow(delta)));
					SETTINGS_INITIAL_WINDOW_SIZE_PEER = value;
					break;
				case MAX_FRAME_SIZE:
					if (!settings.validate(value))
						throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
					SETTINGS_MAX_FRAME_SIZE_PEER = value;
					break;
				case MAX_HEADER_LIST_SIZE:
					SETTINGS_MAX_HEADER_LIST_SIZE_PEER = value;
					break;
				case SETTINGS_ENABLE_CONNECT_PROTOCOL:
					break;
				default:
				}
			}
		}

		private void recvSetting(ByteBuffer data) throws IncomingException {
			updateSettings(data);
			sendSetting(Collections.emptyMap(), Flag.ACK.value());
		}

		public void setHeaderTableSize(int size, long SETTINGS_TIMEOUT) {
			sendSettings(Collections.singletonMap(Settings.HEADER_TABLE_SIZE, Integer.toUnsignedLong(size)),
					SETTINGS_TIMEOUT);
		}

		public int getMaxFrameSize() {
			return SETTINGS_MAX_FRAME_SIZE_PEER;
		}

		List<Octets> encode(Consumer<RFC7541.Encoder> consumer) {
			RFC7541.Encoder encoder = orfc7541.createEncoder(SETTINGS_MAX_FRAME_SIZE_PEER);
			synchronized (orfc7541) {
				consumer.accept(encoder);
			}
			return encoder.getPayloads();
		}

		private List<Stream> unprocessed;
		private int goawaysid = -1;

		boolean ignore(int sid) {
			return goawaysid != -1 && sid >= goawaysid;
		}

		private void recvGoaway(int lsid, ErrorCode errorCode, ByteBuffer data) {
			unprocessed = map.tailMap(lsid, false).values().stream().filter(s -> !s.isClosed())
					.collect(Collectors.toList());
		}

		private void cleanup() {
			List<Integer> sids = new ArrayList<>();
			synchronized (this) {
				for (Iterator<Map.Entry<Integer, Stream>> it = map.entrySet().iterator(); it.hasNext();) {
					Map.Entry<Integer, Stream> e = it.next();
					if (e.getValue().isClosed()) {
						sids.add(e.getKey());
						it.remove();
					}
				}
			}
			execute(() -> prioritization.remove(sids));
		}

		private final static byte[] FH_GOAWAY = new byte[] { 0, 0, 8, FrameType.GOAWAY.registry(), 0, 0, 0, 0, 0 };

		private void sendGoaway(ErrorCode errorCode, int lsid) {
			futureRTT.cancel(false);
			queueFlip(ByteBuffer.allocate(17).put(FH_GOAWAY).putInt(lsid).putInt(errorCode.registry()));
			if (errorCode != ErrorCode.NO_ERROR)
				exchange.sendFinal(meanRTT());
		}

		private void sendGoaway(ErrorCode errorCode) {
			if (goawaysid == -1)
				goawaysid = lastStreamIdentifier;
			sendGoaway(errorCode, goawaysid);
		}

		public synchronized void goway() {
			if (goawaysid != -1)
				return;
			if (isServer()) {
				sendGoaway(ErrorCode.NO_ERROR, Integer.MAX_VALUE);
				Future<?>[] future = new Future<?>[1];
				Runnable once = () -> {
					synchronized (this) {
						if (future[0] != null) {
							future[0].cancel(false);
							future[0] = null;
							sendGoaway(ErrorCode.NO_ERROR);
						}
					}
				};
				future[0] = exchange.getScheduler().schedule(once, meanRTT(), TimeUnit.MILLISECONDS);
				ping(rtt -> once.run());
			} else
				sendGoaway(ErrorCode.NO_ERROR);
		}

		public synchronized void shutdown(Throwable closeReason) {
			futureRTT.cancel(false);
			map.values().stream().filter(s -> !s.isClosed()).forEach(s -> s.shutdown(closeReason));
		}

		private final int rttSamples;
		private final long pingTimeout;
		private final Queue<Long> rtts = new ArrayDeque<>();
		private long meanRTT;

		private final static byte[] FH_PING = new byte[] { 0, 0, 8, FrameType.PING.registry(), 0, 0, 0, 0, 0 };
		private final static byte[] FH_PING_ACK = new byte[] { 0, 0, 8, FrameType.PING.registry(), Flag.ACK.value(), 0,
				0, 0, 0 };

		private final Map<Long, Pair<Consumer<Long>, Future<?>>> ping = new HashMap<>();

		public long meanRTT() {
			return meanRTT;
		}

		private void updateRTT(long rtt) {
			if (rtts.size() == rttSamples)
				rtts.poll();
			rtts.offer(rtt);
			meanRTT = (long) rtts.stream().mapToLong(Long::valueOf).average().getAsDouble();
		}

		private void recvPing(ByteBuffer data, boolean ack) {
			if (ack) {
				long now = data.getLong();
				Pair<Consumer<Long>, Future<?>> pair = ping.remove(now);
				if (pair != null) {
					pair.getValue().cancel(false);
					long rtt = System.currentTimeMillis() - now;
					updateRTT(rtt);
					pair.getKey().accept(rtt);
				}
			} else
				queueFlip(ByteBuffer.allocate(17).put(FH_PING_ACK).put(data));
		}

		public synchronized void ping(Consumer<Long> consumer) {
			long now = System.currentTimeMillis();
			ping.put(now,
					new Pair<>(consumer, exchange.getScheduler().scheduleWithFixedDelay(
							() -> exchange.cancel(new Exception("RFC7540.ping timeTimeout[" + pingTimeout + "]")),
							pingTimeout, pingTimeout, TimeUnit.MILLISECONDS)));
			queueFlip(ByteBuffer.allocate(17).put(FH_PING).putLong(now));
		}

		boolean isServer() {
			return isServerStreamIdentifier(sidgen);
		}

		private int sidgen;
		private final Exchange exchange;
		private final RFC7541 irfc7541;
		private final RFC7541 orfc7541;
		private final Prioritization prioritization;
		private final AtomicLong concurrent = new AtomicLong();
		private final Future<?> futureRTT;
		private final int windowUpdateAccumulatePercent;
		private final int windowUpdateAccumulateThreshold;
		private final long idelTimeout;
		private final long incompleteFrameTimeout;

		public Connection(boolean isServer, Exchange exchange, Map<Settings, Long> initSettings) {
			this.sidgen = isServer ? 2 : 1;
			this.exchange = exchange;
			this.prioritization = new Prioritization();
			this.irfc7541 = new RFC7541((int) Settings.HEADER_TABLE_SIZE.def());
			this.orfc7541 = new RFC7541((int) Settings.HEADER_TABLE_SIZE.def());
			this.rttSamples = initSettings.get(Settings.RTT_SAMPLES).intValue();
			this.pingTimeout = initSettings.get(Settings.PING_TIMEOUT);
			this.idelTimeout = initSettings.get(Settings.IDLE_TIMEOUT);
			this.incompleteFrameTimeout = initSettings.get(Settings.INCOMPLETE_FRAME_TIMEOUT);
			long rttMeasurePeriod = initSettings.get(Settings.RTT_MEASURE_PERIOD);
			this.futureRTT = exchange.getScheduler().scheduleAtFixedRate(() -> {
				ping(rtt -> {
				});
				cleanup();
			}, rttMeasurePeriod, rttMeasurePeriod, TimeUnit.MILLISECONDS);
			long connectionWindowSize = initSettings.get(Settings.CONNECTION_WINDOW_SIZE);
			this.windowUpdateAccumulatePercent = initSettings.get(Settings.WINDOW_UPDATE_ACCUMULATE_PERCENT).intValue();
			this.windowUpdateAccumulateThreshold = (int) (connectionWindowSize * windowUpdateAccumulatePercent / 100);
			sendSettings(
					initSettings.entrySet().stream()
							.filter(e -> e.getKey().registry() > 0 && !e.getValue().equals(e.getKey().def()))
							.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())),
					initSettings.get(Settings.SETTINGS_TIMEOUT));
			int inc = (int) (connectionWindowSize - Settings.CONNECTION_WINDOW_SIZE.def());
			if (inc != 0)
				sendWindowUpdate(0, inc);
		}

		public Connection(Exchange exchange, String http2_settings, Map<Settings, Long> initSettings) {
			this(true, exchange, initSettings);
			try {
				updateSettings(ByteBuffer.wrap(Base64.getUrlDecoder().decode(http2_settings)));
			} catch (Throwable e) {
				throw new RuntimeException("malformed http2-settings");
			}
		}

		private void execute(Runnable r) {
			exchange.execute(r);
		}

		synchronized Stream createActiveStream() {
			if (unprocessed != null)
				return null;
			if (SETTINGS_MAX_CONCURRENT_STREAMS < concurrent.get())
				return null;
			int sid = sidgen;
			sidgen += 2;
			Stream stream = new Stream(this, sid);
			map.put(sid, stream);
			execute(() -> {
				prioritization.priority(sid, false, 0, Prioritization.DEFAULT_WEIGHT);
				prioritization.setup(sid, stream);
			});
			return stream;
		}

		private int windowUpdateAccumulate;
		private int connectionWindow = SETTINGS_INITIAL_WINDOW_SIZE_PEER;
		private int activeWindow = 0;
		private final static byte[] FH_WINDOW_UPDATE = new byte[] { 0, 0, 4, FrameType.WINDOW_UPDATE.registry(), 0 };

		void sendWindowUpdate(int sid, int inc) {
			queueFlip(ByteBuffer.allocate(13).put(FH_WINDOW_UPDATE).putInt(sid).putInt(inc));
		}

		void sendWindowUpdate(int inc) {
			windowUpdateAccumulate += inc;
			if (windowUpdateAccumulate > windowUpdateAccumulateThreshold) {
				sendWindowUpdate(0, windowUpdateAccumulate);
				windowUpdateAccumulate = 0;
			}
		}

		private void recvWindowUpdate(int inc) {
			if ((connectionWindow += inc) < 0)
				error(ErrorCode.FLOW_CONTROL_ERROR);
			flush(true);
		}

		private ByteBuffer dataFrameHead(int sid, int length, boolean es) {
			ByteBuffer bb = ByteBuffer.allocate(9);
			bb.put((byte) (length >> 16));
			bb.put((byte) (length >> 8));
			bb.put((byte) length);
			bb.put(FrameType.DATA.registry());
			bb.put(es ? Flag.END_STREAM.value() : (byte) 0);
			bb.putInt(sid);
			bb.flip();
			return bb;
		}

		int sendData(int sid, ByteBuffer data, boolean es) {
			int length = data.remaining();
			ByteBuffer bb = dataFrameHead(sid, length, es);
			if (length > 0)
				queue(new ByteBuffer[] { bb, data });
			else
				queue(bb);
			return length;
		}

		int sendData(int sid, ByteBuffer[] datas, boolean es) {
			int length = 0;
			for (ByteBuffer bb : datas)
				length += bb.remaining();
			ByteBuffer bb = dataFrameHead(sid, length, es);
			if (length > 0) {
				ByteBuffer[] bbs = new ByteBuffer[datas.length + 1];
				bbs[0] = bb;
				System.arraycopy(datas, 0, bbs, 1, datas.length);
				queue(bbs);
			} else
				queue(bb);
			return length;
		}

		void queueFlip(ByteBuffer frame) {
			frame.flip();
			queue(frame);
		}

		void queue(ByteBuffer frame) {
			exchange.send(frame);
		}

		void queue(ByteBuffer[] frame) {
			exchange.send(frame);
		}

		boolean countUp(Stream stream) {
			boolean r = concurrent.incrementAndGet() <= SETTINGS_MAX_CONCURRENT_STREAMS;
			if (!r)
				concurrent.decrementAndGet();
			return r;
		}

		void countDown(Stream stream) {
			concurrent.decrementAndGet();
		}

		@Override
		public void accept(Integer window) {
			activeWindow = window;
			flush(false);
		}

		void flush(boolean source) {
			int min = Math.min(activeWindow, connectionWindow);
			if (min > 0) {
				int r = prioritization.apply(min);
				int sent = r & Integer.MAX_VALUE;
				if (sent > 0) {
					connectionWindow -= sent;
					activeWindow -= sent;
				} else if (!source && r == 0) {
					exchange.clearAlarm();
				}
			}
		}

		void flush(Supplier<Boolean> action) {
			execute(() -> {
				if (action.get())
					flush(true);
			});
		}
	}

	static class Prioritization implements Function<Integer, Integer> {
		private final static int DEFAULT_WEIGHT = 16;
		private final static Function<Integer, Integer> DEFAULT_CONSUMER = window -> 0;
		private final Map<Integer, Node> map = new HashMap<>();
		private final Node ROOT = new Node();

		Prioritization() {
			ROOT.weight = DEFAULT_WEIGHT;
			map.put(0, ROOT);
		}

		private class Node implements Function<Integer, Integer> {
			private Node parent;
			private final List<Node> children = new ArrayList<>();
			private int weight;
			private long total;
			private Function<Integer, Integer> consumer = DEFAULT_CONSUMER;

			@Override
			public Integer apply(Integer window) {
				int r = consumer.apply(window);
				int busy = r & Integer.MIN_VALUE;
				int sent = r & Integer.MAX_VALUE;
				if (sent > 0)
					for (Node n = this; n != ROOT; n = n.parent)
						n.total += sent;
				if (window > sent && !children.isEmpty()) {
					children.sort(Comparator.comparingLong(n -> n.total / n.weight));
					for (Node n : children)
						if ((sent += n.apply(window - sent)) == window)
							break;
				}
				return sent | busy;
			}
		}

		@Override
		public Integer apply(Integer window) {
			return ROOT.apply(window);
		}

		private void remove(int sid) {
			if (sid == 0)
				return;
			Node curnode = map.remove(sid);
			if (curnode == null)
				return;
			curnode.parent.children.remove(curnode);
			if (curnode.children.isEmpty())
				return;
			long amount = 0;
			for (Node n : curnode.children)
				amount += n.weight;
			for (Node n : curnode.children) {
				n.weight = (int) (curnode.weight * n.weight / amount);
				if (n.weight == 0)
					n.weight = 1;
				(n.parent = curnode.parent).children.add(n);
			}
		}

		void remove(Collection<Integer> sids) {
			for (Integer sid : sids)
				remove(sid);
		}

		void setup(int sid, Function<Integer, Integer> consumer) {
			map.get(sid).consumer = consumer;
		}

		void priority(int sid, boolean exclusive, int depsid, int weight) {
			Node depnode = map.get(depsid);
			if (depnode == null) {
				depnode = ROOT;
				weight = DEFAULT_WEIGHT;
			}
			Node curnode = map.get(sid);
			if (curnode == null) {
				map.put(sid, curnode = new Node());
			} else {
				for (Node n = depnode; (n = n.parent) != ROOT;)
					if (n == curnode) {
						depnode.parent.children.remove(depnode);
						depnode.parent = curnode.parent;
						depnode.parent.children.add(depnode);
						break;
					}
				curnode.parent.children.remove(curnode);
			}
			curnode.parent = depnode;
			curnode.weight = weight;
			if (exclusive) {
				for (Node n : depnode.children)
					n.parent = curnode;
				curnode.children.addAll(depnode.children);
				depnode.children.clear();
			}
			depnode.children.add(curnode);
		}

		Runnable prepare(int sid, ByteBuffer data) {
			if (data != null) {
				int tmp = data.getInt();
				int weight = (data.get() & 0xff) + 1;
				int depsid = tmp & Integer.MAX_VALUE;
				if (depsid != sid)
					return () -> priority(sid, tmp < 0, depsid, weight);
			}
			return () -> priority(sid, false, 0, DEFAULT_WEIGHT);
		}
	}

	public interface Processor {
		default void promise(List<RFC7541.Entry> headers) {
		}

		void process(List<RFC7541.Entry> headers);

		void process(ByteBuffer in) throws Exception;

		void end();

		void shutdown(Throwable closeReason);

		void reset(ErrorCode errorCode);
	}

	public interface Exchange {
		Processor createProcessor(Stream stream);

		void send(ByteBuffer[] bbs);

		void send(ByteBuffer bb);

		void sendFinal(long timeout);

		void cancel(Throwable closeReason);

		ScheduledExecutorService getScheduler();

		void execute(Runnable r);

		void clearAlarm();
	}
}
