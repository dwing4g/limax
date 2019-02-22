package limax.http;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import limax.codec.Octets;
import limax.http.RFC7541.Entry;

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
		HEADER_TABLE_SIZE(0, -1, 4096), ENABLE_PUSH(0, 1, 1), MAX_CONCURRENT_STREAMS(0, -1, -1),
		INITIAL_WINDOW_SIZE(0, Integer.MAX_VALUE, 65535), MAX_FRAME_SIZE(16384, 16777215, 16384),
		MAX_HEADER_LIST_SIZE(0, -1, -1);
		private final int low;
		private final int high;
		private final int def;

		Settings(int low, int high, int def) {
			this.low = low;
			this.high = high;
			this.def = def;
		}

		public short registry() {
			return (short) (ordinal() + 1);
		}

		public int def() {
			return def;
		}

		public boolean validate(int value) {
			return Integer.compareUnsigned(value, low) >= 0 && Integer.compareUnsigned(value, high) <= 0;
		}

		public static Settings valueOf(short registry) {
			try {
				return Settings.values()[registry - 1];
			} catch (Exception e) {
				return null;
			}
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

	public static class Stream {
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

		private int state = IDLE;
		private int recvtype;
		private int sendtype;

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
			if (!s0 && s1 && !connection.countUp())
				error(ErrorCode.REFUSED_STREAM);
			if (s0 && !s1)
				connection.countDown();
		}

		private final Connection connection;
		private final int sid;
		private Processor processor;
		private int windowSize;
		private List<Entry> headers = new ArrayList<>();
		private int headersSize = 0;
		private List<Entry> error;

		Stream(Connection connection, int sid) {
			this.connection = connection;
			this.sid = sid;
			this.windowSize = connection.SETTINGS_INITIAL_WINDOW_SIZE;
		}

		void startup(Processor processor) {
			this.processor = processor;
		}

		void error(ErrorCode errorCode) {
			sendReset(errorCode);
		}

		private void decodeHeaderData(ByteBuffer data) throws IncomingException {
			try {
				connection.irfc7541.decode(data, e -> {
					if (headersSize == -1)
						return;
					headersSize += e.size();
					if (headersSize <= connection.SETTINGS_MAX_HEADER_LIST_SIZE) {
						headers.add(e);
					} else {
						headers.clear();
						headersSize = -1;
						if (connection.isServer()) {
							error = new ArrayList<>();
							error.add(new Entry(":status", "431"));
						}
					}
				});
			} catch (Exception e) {
				throw new IncomingException(ErrorCode.COMPRESSION_ERROR);
			}
		}

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
				processor.requestFinished();
				transit(recvTransitions(END_STREAM));
			}
		}

		void recvPromise(Stream associateStream, ByteBuffer data, boolean eh) throws IncomingException {
			int nextState = recvTransitions(HEADERS);
			decodeHeaderData(data);
			if (connection.ignore(sid))
				return;
			if (eh) {
				processor.process(headers);
				headers.clear();
				transit(nextState);
			}
			if (associateStream.state != OPEN && associateStream.state != HALFCLOSED_LOCAL
					&& associateStream.sendtype != RST_STREAM)
				throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
		}

		void recvContinuation(ByteBuffer data, boolean eh) throws IncomingException {
			int nextState = recvTransitions(HEADERS);
			decodeHeaderData(data);
			if (connection.ignore(sid))
				return;
			if (eh) {
				processor.process(headers);
				headers.clear();
				transit(nextState);
			}
		}

		void recvData(ByteBuffer data, boolean es, Consumer<Integer> windowUpdate) throws IncomingException {
			int nextState = recvTransitions(END_STREAM);
			windowUpdate.accept(sid);
			processor.process(data);
			if (es) {
				processor.requestFinished();
				transit(nextState);
			}
		}

		void recvReset(ErrorCode errorCode) throws IncomingException {
			transit(recvTransitions(RST_STREAM));
		}

		void recvWindowUpdate(int inc) throws IncomingException {
			if (windowSize < 0) {
				windowSize += inc;
			} else if ((windowSize += inc) < 0)
				error(ErrorCode.FLOW_CONTROL_ERROR);
			if (windowSize > 0)
				processor.update(windowSize);
		}

		public int getWindowSize() {
			return Math.min(windowSize, connection.getWindowSize());
		}

		public ErrorCode sendHeaders(List<Entry> headers, boolean es) {
			int nextState = sendTransitions(HEADERS);
			if (nextState == -1)
				return ErrorCode.PROTOCOL_ERROR;
			List<Entry> oheaders = error != null ? error : headers;
			Octets data = new Octets();
			connection.encode(encoder -> oheaders.stream()
					.forEach(e -> encoder.add(e.getKey(), e.getValue(), RFC7541.UpdateMode.UPDATE)), data);
			int flags = Flag.END_HEADERS.value();
			if (es)
				flags |= Flag.END_STREAM.value();
			int length = data.size();
			ByteBuffer bb = ByteBuffer.allocate(length + 9);
			bb.put((byte) (length >> 16));
			bb.put((byte) (length >> 8));
			bb.put((byte) length);
			bb.put(FrameType.HEADERS.registry());
			bb.put((byte) flags);
			bb.putInt(sid);
			bb.put(data.array(), 0, data.size());
			connection.queue(bb);
			transit(nextState);
			return ErrorCode.NO_ERROR;
		}

		public ErrorCode sendPromise(List<Entry> headers) {
			if (!connection.isServer())
				return ErrorCode.PROTOCOL_ERROR;
			if (connection.SETTINGS_ENABLE_PUSH != 1)
				return ErrorCode.PROTOCOL_ERROR;
			if (state != OPEN && state != HALFCLOSED_REMOTE)
				return ErrorCode.PROTOCOL_ERROR;
			int nextState = sendTransitions(PUSH_PROMISE);
			if (nextState == -1)
				return ErrorCode.PROTOCOL_ERROR;
			Stream promise = connection.createActiveStream();
			if (promise == null)
				return ErrorCode.REFUSED_STREAM;
			Octets data = new Octets();
			connection.encode(encoder -> headers.stream()
					.forEach(e -> encoder.add(e.getKey(), e.getValue(), RFC7541.UpdateMode.UPDATE)), data);
			int length = data.size() + 4;
			ByteBuffer bb = ByteBuffer.allocate(length + 9);
			bb.put((byte) (length >> 16));
			bb.put((byte) (length >> 8));
			bb.put((byte) length);
			bb.put(FrameType.PUSH_PROMISE.registry());
			bb.put(Flag.END_HEADERS.value());
			bb.putInt(sid);
			bb.putInt(promise.sid);
			bb.put(data.array(), 0, data.size());
			connection.queue(bb);
			transit(nextState);
			return ErrorCode.NO_ERROR;
		}

		private final static byte[] FH_RST_STREAM = new byte[] { 0, 0, 4, FrameType.RST_STREAM.registry(), 0 };

		private void sendReset(ErrorCode errorCode) {
			int nextState = sendTransitions(RST_STREAM);
			if (nextState != -1) {
				connection.queue(ByteBuffer.allocate(13).put(FH_RST_STREAM).putInt(sid).putInt(errorCode.registry()));
				transit(nextState);
			}
		}

		public void sendData(ByteBuffer data, boolean es) {
			int nextState = sendTransitions(END_STREAM);
			if (nextState != -1) {
				if (error != null)
					synchronized (connection) {
						windowSize -= connection.sendData(sid, data, es);
					}
				if (es)
					transit(nextState);
			}
		}

		public void sendData(ByteBuffer[] datas, boolean es) {
			int nextState = sendTransitions(END_STREAM);
			if (nextState != -1) {
				if (error != null)
					synchronized (connection) {
						windowSize -= connection.sendData(sid, datas, es);
					}
				if (es)
					transit(nextState);
			}
		}

		void shutdown(Throwable closeReason) {
			processor.shutdown(closeReason);
		}
	}

	public static class Connection {
		private int length;
		private FrameType type;
		private byte flag;
		private int sid;
		private Octets partial = new Octets();
		private int stage = 0;

		private void unPadding(ByteBuffer data) throws IncomingException {
			if (Flag.PADDED.test(flag)) {
				int plen = data.get() & 0xff;
				if (plen >= length)
					throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
				data.limit(data.limit() - plen);
			}
		}

		private final static byte[] FH_WINDOW_UPDATE = new byte[] { 0, 0, 4, FrameType.WINDOW_UPDATE.registry(), 0 };

		public synchronized boolean unwrap(ByteBuffer in) {
			boolean completeFrame = false;
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
					return completeFrame;
				int want = length - partial.size();
				int remain = in.remaining();
				if (want > remain) {
					partial.append(in.array(), in.position(), remain);
					in.position(in.limit());
					return completeFrame;
				}
				ByteBuffer data;
				if (want < length) {
					partial.append(in.array(), in.position(), want);
					data = partial.getByteBuffer();
					partial.clear();
				} else {
					data = ByteBuffer.wrap(in.array(), in.position(), want);
				}
				in.position(in.position() + want);
				stage = 0;
				completeFrame = true;
				if (type != null)
					try {
						if (length > SETTINGS_MAX_FRAME_SIZE)
							throw new IncomingException(ErrorCode.FRAME_SIZE_ERROR);
						switch (type) {
						case DATA: {
							if (sid == 0)
								throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
							unPadding(data);
							queue(ByteBuffer.allocate(13).put(FH_WINDOW_UPDATE).putInt(0).putInt(length));
							if (!ignore(sid))
								findStream(sid).recvData(data, Flag.END_STREAM.test(flag), _sid -> queue(
										ByteBuffer.allocate(13).put(FH_WINDOW_UPDATE).putInt(_sid).putInt(length)));
							break;
						}
						case HEADERS: {
							if (sid == 0)
								throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
							unPadding(data);
							boolean succeed = true;
							if (Flag.PRIORITY.test(flag)) {
								if (data.remaining() < 5)
									throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
								succeed = prioritization.priority(sid, data);
							} else
								succeed = prioritization.priority(sid, false, 0, Prioritization.DEFAULT_WEIGHT);
							Stream stream = createPassiveStream(sid);
							stream.recvHeaders(data, Flag.END_STREAM.test(flag), Flag.END_HEADERS.test(flag));
							if (!succeed)
								stream.error(ErrorCode.PROTOCOL_ERROR);
							break;
						}
						case PRIORITY: {
							if (sid == 0)
								throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
							if (length != 5) {
								findStream(sid).error(ErrorCode.FRAME_SIZE_ERROR);
							} else if (!prioritization.priority(sid, data)) {
								findStream(sid).error(ErrorCode.PROTOCOL_ERROR);
							}
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
							prioritization.priority(psid, false, sid, Prioritization.DEFAULT_WEIGHT);
							createPassiveStream(psid).recvPromise(findStream(sid), data, Flag.END_HEADERS.test(flag));
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
									recvWindowUpdate(inc);
								else
									findStream(sid).recvWindowUpdate(inc);
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
						return completeFrame;
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

		private int lastServerStreamIdentifier = -1;
		private int lastClientStreamIdentifier = -1;

		public Stream createPassiveStream(int sid) throws IncomingException {
			if (isServerStreamIdentifier(sid)) {
				if (sid <= lastServerStreamIdentifier)
					throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
				lastServerStreamIdentifier = sid;
			} else {
				if (sid <= lastClientStreamIdentifier)
					throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
				lastClientStreamIdentifier = sid;
			}
			Stream stream = new Stream(this, sid);
			map.put(sid, stream);
			stream.startup(context.createProcessor(stream));
			return stream;
		}

		void error(ErrorCode errorCode) {
			sendGoaway(errorCode);
		}

		private int SETTINGS_HEADER_TABLE_SIZE = Settings.HEADER_TABLE_SIZE.def();
		private int SETTINGS_ENABLE_PUSH = Settings.ENABLE_PUSH.def();
		private int SETTINGS_MAX_CONCURRENT_STREAMS = Settings.MAX_CONCURRENT_STREAMS.def();
		private int SETTINGS_INITIAL_WINDOW_SIZE = Settings.INITIAL_WINDOW_SIZE.def();
		private int SETTINGS_MAX_FRAME_SIZE = Settings.MAX_FRAME_SIZE.def();
		private int SETTINGS_MAX_HEADER_LIST_SIZE = Settings.MAX_HEADER_LIST_SIZE.def();

		private void sendSetting(Map<Settings, Integer> map, byte flag) {
			int length = map.size() * 6;
			ByteBuffer bb = ByteBuffer.allocate(length + 9);
			bb.put((byte) 0);
			bb.put((byte) 0);
			bb.put((byte) length);
			bb.put(FrameType.SETTINGS.registry());
			bb.put(flag);
			bb.putInt(0);
			map.forEach((k, v) -> {
				bb.putShort(k.registry());
				bb.putInt(v);
			});
			queue(bb);
		}

		private Map<Settings, Integer> outstandingSettings;
		private long settingsTimestamp;
		private Future<?> futureSettings;

		public void sendSettings(Map<Settings, Integer> settings, long SETTINGS_TIMEOUT) {
			synchronized (this) {
				if (outstandingSettings != null)
					return;
				outstandingSettings = new HashMap<>(settings);
				settingsTimestamp = System.currentTimeMillis();
			}
			futureSettings = context.schedule(() -> error(ErrorCode.SETTINGS_TIMEOUT), SETTINGS_TIMEOUT);
			sendSetting(settings, (byte) 0);
		}

		private void recvSettingAck() {
			futureSettings.cancel(false);
			updateRTT(System.currentTimeMillis() - settingsTimestamp);
			outstandingSettings.forEach((settings, value) -> {
				switch (settings) {
				case ENABLE_PUSH:
					SETTINGS_ENABLE_PUSH = value;
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
					irfc7541.update(SETTINGS_HEADER_TABLE_SIZE = value);
					break;
				case ENABLE_PUSH:
					if (!settings.validate(value))
						throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
					SETTINGS_ENABLE_PUSH = value;
					break;
				case MAX_CONCURRENT_STREAMS:
					SETTINGS_MAX_CONCURRENT_STREAMS = value;
					break;
				case INITIAL_WINDOW_SIZE:
					if (!settings.validate(value))
						throw new IncomingException(ErrorCode.FLOW_CONTROL_ERROR);
					int delta = value - SETTINGS_INITIAL_WINDOW_SIZE;
					SETTINGS_INITIAL_WINDOW_SIZE = value;
					if (delta > 0) {
						for (Stream s : map.values())
							if (!settings.validate(s.windowSize += delta))
								throw new IncomingException(ErrorCode.FLOW_CONTROL_ERROR);
					} else if (delta < 0) {
						for (Stream s : map.values())
							s.windowSize += delta;
					}
					break;
				case MAX_FRAME_SIZE:
					if (!settings.validate(value))
						throw new IncomingException(ErrorCode.PROTOCOL_ERROR);
					SETTINGS_MAX_FRAME_SIZE = value;
					break;
				case MAX_HEADER_LIST_SIZE:
					SETTINGS_MAX_HEADER_LIST_SIZE = value;
				}
			}
		}

		private void recvSetting(ByteBuffer data) throws IncomingException {
			updateSettings(data);
			sendSetting(Collections.emptyMap(), Flag.ACK.value());
		}

		public void setHeaderTableSize(int size, long SETTINGS_TIMEOUT) {
			synchronized (orfc7541) {
				orfc7541.update(size);
			}
			sendSettings(Collections.singletonMap(Settings.HEADER_TABLE_SIZE, size), SETTINGS_TIMEOUT);
		}

		void encode(Consumer<RFC7541.Encoder> consumer, Octets output) {
			synchronized (orfc7541) {
				consumer.accept(orfc7541.createEncoder(output));
			}
		}

		private List<Stream> unprocessed;
		private int goawaysid = -1;

		boolean ignore(int sid) {
			return goawaysid != -1 && sid > goawaysid;
		}

		private void recvGoaway(int lsid, ErrorCode errorCode, ByteBuffer data) {
			unprocessed = new ArrayList<>(map.tailMap(lsid, false).values());
		}

		private final static byte[] FH_GOAWAY = new byte[] { 0, 0, 8, FrameType.GOAWAY.registry(), 0, 0, 0, 0, 0 };

		private void sendGoaway(ErrorCode errorCode, int lsid) {
			futureRTT.cancel(false);
			queue(ByteBuffer.allocate(17).put(FH_GOAWAY).putInt(lsid).putInt(errorCode.registry()));
			if (errorCode != ErrorCode.NO_ERROR)
				context.sendFinal(meanRTT());
		}

		private void sendGoaway(ErrorCode errorCode) {
			if (goawaysid == -1)
				goawaysid = map.isEmpty() ? 0 : map.lastKey();
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
				future[0] = context.schedule(once, meanRTT());
				ping(rtt -> once.run());
			} else
				sendGoaway(ErrorCode.NO_ERROR);
		}

		public synchronized void shutdown(Throwable closeReason) {
			map.values().forEach(s -> s.shutdown(closeReason));
		}

		private final int RTT_SAMPLES;
		private final Queue<Long> rtts = new ArrayDeque<>();
		private long meanRTT;

		private final static byte[] FH_PING = new byte[] { 0, 0, 8, FrameType.PING.registry(), 0, 0, 0, 0, 0 };
		private final static byte[] FH_PING_ACK = new byte[] { 0, 0, 8, FrameType.PING.registry(), Flag.ACK.value(), 0,
				0, 0, 0 };

		private final Map<Long, Consumer<Long>> ping = new HashMap<>();

		public long meanRTT() {
			return meanRTT;
		}

		private void updateRTT(long rtt) {
			if (rtts.size() == RTT_SAMPLES)
				rtts.poll();
			rtts.offer(rtt);
			meanRTT = (long) rtts.stream().mapToLong(Long::valueOf).average().getAsDouble();
		}

		private void recvPing(ByteBuffer data, boolean ack) {
			if (ack) {
				long now = data.getLong();
				Consumer<Long> consumer = ping.remove(now);
				if (consumer != null) {
					long rtt = System.currentTimeMillis() - now;
					updateRTT(rtt);
					consumer.accept(rtt);
				}
			} else
				queue(ByteBuffer.allocate(17).put(FH_PING_ACK).put(data));
		}

		public synchronized void ping(Consumer<Long> consumer) {
			long now = System.currentTimeMillis();
			ping.put(now, consumer);
			queue(ByteBuffer.allocate(17).put(FH_PING).putLong(now));
		}

		boolean isServer() {
			return isServerStreamIdentifier(sidgen);
		}

		private int sidgen;
		private final Exchange context;
		private final RFC7541 irfc7541;
		private final RFC7541 orfc7541;
		private final Prioritization prioritization = new Prioritization();
		private final AtomicInteger counter = new AtomicInteger();
		private final Future<?> futureRTT;

		public Connection(Exchange context, boolean isServer, long RTT_MEASURE_PERIOD, int RTT_SAMPLES) {
			this.sidgen = isServer ? 2 : 1;
			this.context = context;
			this.irfc7541 = new RFC7541(SETTINGS_HEADER_TABLE_SIZE);
			this.orfc7541 = new RFC7541(SETTINGS_HEADER_TABLE_SIZE);
			this.RTT_SAMPLES = RTT_SAMPLES;
			this.futureRTT = context.schedulePeriodically(() -> ping(rtt -> {
			}), RTT_MEASURE_PERIOD);
		}

		public Connection(Exchange nettask, String settings, long RTT_MEASURE_PERIOD, int RTT_SAMPLES) {
			this(nettask, true, RTT_MEASURE_PERIOD, RTT_SAMPLES);
			try {
				updateSettings(ByteBuffer.wrap(Base64.getUrlDecoder().decode(settings)));
			} catch (Throwable e) {
				throw new RuntimeException("malformed http2-settings");
			}
		}

		boolean countUp() {
			return counter.incrementAndGet() <= SETTINGS_MAX_CONCURRENT_STREAMS;
		}

		void countDown() {
			counter.decrementAndGet();
		}

		public synchronized Stream createActiveStream() {
			if (unprocessed != null)
				return null;
			if (SETTINGS_MAX_CONCURRENT_STREAMS == 0)
				return null;
			Stream stream = new Stream(this, sidgen);
			map.put(sidgen, stream);
			sidgen += 2;
			return stream;
		}

		private int windowSize = SETTINGS_INITIAL_WINDOW_SIZE;

		private void recvWindowUpdate(int inc) throws IncomingException {
			if (windowSize < 0)
				windowSize += inc;
			else if ((windowSize += inc) < 0)
				throw new IncomingException(ErrorCode.FLOW_CONTROL_ERROR);
		}

		int getWindowSize() {
			return windowSize;
		}

		private ByteBuffer dataFrameHead(int sid, int length, boolean es) {
			ByteBuffer bb = ByteBuffer.allocate(9);
			bb.put((byte) (length >> 16));
			bb.put((byte) (length >> 8));
			bb.put((byte) length);
			bb.put(FrameType.DATA.registry());
			bb.put((byte) (es ? Flag.END_STREAM : 0));
			bb.putInt(sid);
			bb.flip();
			return bb;
		}

		int sendData(int sid, ByteBuffer data, boolean es) {
			int length = data.remaining();
			ByteBuffer bb = dataFrameHead(sid, length, es);
			if (length > 0)
				context.send(new ByteBuffer[] { bb, data });
			else
				context.send(bb);
			windowSize -= length;
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
				context.send(bbs);
			} else
				context.send(bb);
			windowSize -= length;
			return length;
		}

		void queue(ByteBuffer frame) {
			frame.flip();
			context.send(frame);
		}
	}

	static class Prioritization {
		private final static int DEFAULT_WEIGHT = 16;
		private final Map<Integer, Node> map = new HashMap<>();
		private final Node ROOT = new Node();

		Prioritization() {
			ROOT.weight = DEFAULT_WEIGHT;
			map.put(0, ROOT);
		}

		private static class Node {
			private Node parent;
			private final List<Node> children = new ArrayList<>();
			private double weight;
		}

		void remove(int sid) {
			if (sid == 0)
				return;
			Node curnode = map.remove(sid);
			if (curnode == null)
				return;
			curnode.parent.children.remove(curnode);
			if (curnode.children.isEmpty())
				return;
			double amount = 0;
			for (Node n : curnode.children)
				amount += n.weight;
			double scale = curnode.weight / amount;
			for (Node n : curnode.children) {
				n.weight *= scale;
				(n.parent = curnode.parent).children.add(n);
			}
		}

		boolean priority(int sid, boolean exclusive, int depsid, int weight) {
			if (weight < 1 || weight > 256)
				return false;
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
				curnode.children.addAll(depnode.children);
				depnode.children.clear();
				for (Node n : curnode.children)
					n.parent = curnode;
			}
			depnode.children.add(curnode);
			return true;
		}

		boolean priority(int sid, ByteBuffer data) {
			int tmp = data.getInt();
			int weight = (data.get() & 0xff) + 1;
			int depsid = tmp & Integer.MAX_VALUE;
			return depsid != sid ? priority(sid, tmp < 0, depsid, weight) : false;
		}

		private static final String[] names = { "ROOT", "A", "B", "C", "D", "E", "F" };

		private String toString(Node node, Map<Node, Integer> rmap) {
			return names[rmap.get(node)] + "<" + node.weight + ">"
					+ (node.parent == null ? "" : "/" + names[rmap.get(node.parent)]) + "("
					+ node.children.stream().map(n -> toString(n, rmap)).collect(Collectors.joining(",")) + ")";
		}

		@Override
		public String toString() {
			return toString(ROOT,
					map.entrySet().stream().collect(Collectors.toMap(e -> e.getValue(), e -> e.getKey())));
		}
	}

	public interface Processor {
		void process(List<RFC7541.Entry> headers);

		void process(ByteBuffer in);

		void update(int windowSize);

		void requestFinished();

		void shutdown(Throwable closeReason);
	}

	public interface Exchange {
		Processor createProcessor(Stream stream);

		void send(ByteBuffer[] bbs);

		void send(ByteBuffer bb);

		void sendFinal(long timeout);

		Future<?> schedule(Runnable r, long delay);

		Future<?> schedulePeriodically(Runnable r, long period);
	}
}
