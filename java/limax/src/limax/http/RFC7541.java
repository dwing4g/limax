package limax.http;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import limax.codec.Octets;

class RFC7541 {
	private final static int[] HUFFMAN_ENCODE = { 0x00003ff8, 0x00ffffd8, 0x1fffffe2, 0x1fffffe3, 0x1fffffe4,
			0x1fffffe5, 0x1fffffe6, 0x1fffffe7, 0x1fffffe8, 0x01ffffea, 0x7ffffffc, 0x1fffffe9, 0x1fffffea, 0x7ffffffd,
			0x1fffffeb, 0x1fffffec, 0x1fffffed, 0x1fffffee, 0x1fffffef, 0x1ffffff0, 0x1ffffff1, 0x1ffffff2, 0x7ffffffe,
			0x1ffffff3, 0x1ffffff4, 0x1ffffff5, 0x1ffffff6, 0x1ffffff7, 0x1ffffff8, 0x1ffffff9, 0x1ffffffa, 0x1ffffffb,
			0x00000054, 0x000007f8, 0x000007f9, 0x00001ffa, 0x00003ff9, 0x00000055, 0x000001f8, 0x00000ffa, 0x000007fa,
			0x000007fb, 0x000001f9, 0x00000ffb, 0x000001fa, 0x00000056, 0x00000057, 0x00000058, 0x00000020, 0x00000021,
			0x00000022, 0x00000059, 0x0000005a, 0x0000005b, 0x0000005c, 0x0000005d, 0x0000005e, 0x0000005f, 0x000000dc,
			0x000001fb, 0x0000fffc, 0x00000060, 0x00001ffb, 0x000007fc, 0x00003ffa, 0x00000061, 0x000000dd, 0x000000de,
			0x000000df, 0x000000e0, 0x000000e1, 0x000000e2, 0x000000e3, 0x000000e4, 0x000000e5, 0x000000e6, 0x000000e7,
			0x000000e8, 0x000000e9, 0x000000ea, 0x000000eb, 0x000000ec, 0x000000ed, 0x000000ee, 0x000000ef, 0x000000f0,
			0x000000f1, 0x000000f2, 0x000001fc, 0x000000f3, 0x000001fd, 0x00003ffb, 0x000ffff0, 0x00003ffc, 0x00007ffc,
			0x00000062, 0x0000fffd, 0x00000023, 0x00000063, 0x00000024, 0x00000064, 0x00000025, 0x00000065, 0x00000066,
			0x00000067, 0x00000026, 0x000000f4, 0x000000f5, 0x00000068, 0x00000069, 0x0000006a, 0x00000027, 0x0000006b,
			0x000000f6, 0x0000006c, 0x00000028, 0x00000029, 0x0000006d, 0x000000f7, 0x000000f8, 0x000000f9, 0x000000fa,
			0x000000fb, 0x0000fffe, 0x00000ffc, 0x00007ffd, 0x00003ffd, 0x1ffffffc, 0x001fffe6, 0x007fffd2, 0x001fffe7,
			0x001fffe8, 0x007fffd3, 0x007fffd4, 0x007fffd5, 0x00ffffd9, 0x007fffd6, 0x00ffffda, 0x00ffffdb, 0x00ffffdc,
			0x00ffffdd, 0x00ffffde, 0x01ffffeb, 0x00ffffdf, 0x01ffffec, 0x01ffffed, 0x007fffd7, 0x00ffffe0, 0x01ffffee,
			0x00ffffe1, 0x00ffffe2, 0x00ffffe3, 0x00ffffe4, 0x003fffdc, 0x007fffd8, 0x00ffffe5, 0x007fffd9, 0x00ffffe6,
			0x00ffffe7, 0x01ffffef, 0x007fffda, 0x003fffdd, 0x001fffe9, 0x007fffdb, 0x007fffdc, 0x00ffffe8, 0x00ffffe9,
			0x003fffde, 0x00ffffea, 0x007fffdd, 0x007fffde, 0x01fffff0, 0x003fffdf, 0x007fffdf, 0x00ffffeb, 0x00ffffec,
			0x003fffe0, 0x003fffe1, 0x007fffe0, 0x003fffe2, 0x00ffffed, 0x007fffe1, 0x00ffffee, 0x00ffffef, 0x001fffea,
			0x007fffe2, 0x007fffe3, 0x007fffe4, 0x00fffff0, 0x007fffe5, 0x007fffe6, 0x00fffff1, 0x07ffffe0, 0x07ffffe1,
			0x001fffeb, 0x000ffff1, 0x007fffe7, 0x00fffff2, 0x007fffe8, 0x03ffffec, 0x07ffffe2, 0x07ffffe3, 0x07ffffe4,
			0x0fffffde, 0x0fffffdf, 0x07ffffe5, 0x01fffff1, 0x03ffffed, 0x000ffff2, 0x003fffe3, 0x07ffffe6, 0x0fffffe0,
			0x0fffffe1, 0x07ffffe7, 0x0fffffe2, 0x01fffff2, 0x003fffe4, 0x003fffe5, 0x07ffffe8, 0x07ffffe9, 0x1ffffffd,
			0x0fffffe3, 0x0fffffe4, 0x0fffffe5, 0x001fffec, 0x01fffff3, 0x001fffed, 0x003fffe6, 0x007fffe9, 0x003fffe7,
			0x003fffe8, 0x00fffff3, 0x007fffea, 0x007fffeb, 0x03ffffee, 0x03ffffef, 0x01fffff4, 0x01fffff5, 0x07ffffea,
			0x00fffff4, 0x07ffffeb, 0x0fffffe6, 0x07ffffec, 0x07ffffed, 0x0fffffe7, 0x0fffffe8, 0x0fffffe9, 0x0fffffea,
			0x0fffffeb, 0x1ffffffe, 0x0fffffec, 0x0fffffed, 0x0fffffee, 0x0fffffef, 0x0ffffff0, 0x07ffffee,
			0x7fffffff };

	private final static int[] HUFFMAN_DECODE = { 0x00420001, 0x005d0002, 0x00680003, 0x00770004, 0x00900005,
			0x004b0006, 0x007b0007, 0x00470008, 0x004d0009, 0x0049000a, 0x000b000d, 0x000c0066, 0x01000124, 0x007f000e,
			0x0080000f, 0x00620010, 0x017b0011, 0x007c0012, 0x00960013, 0x00140019, 0x00c70015, 0x00d80016, 0x001700a2,
			0x001800a1, 0x01010187, 0x00a7001a, 0x0029001b, 0x00bf001c, 0x00d3001d, 0x00e5001e, 0x001f002d, 0x00200026,
			0x00210023, 0x01fe0022, 0x01020103, 0x00240025, 0x01040105, 0x01060107, 0x00270034, 0x00280033, 0x0108010b,
			0x00d0002a, 0x002b00a5, 0x01ef002c, 0x0109018e, 0x0037002e, 0x003f002f, 0x00930030, 0x01f90031, 0x0032003b,
			0x010a010d, 0x010c010e, 0x00350036, 0x010f0110, 0x01110112, 0x0038003c, 0x0039003a, 0x01130114, 0x01150117,
			0x01160200, 0x003d003e, 0x01180119, 0x011a011b, 0x00400041, 0x011c011d, 0x011e011f, 0x00550043, 0x00440052,
			0x008f0045, 0x00460051, 0x01200125, 0x0048004f, 0x01210122, 0x017c004a, 0x0123013e, 0x004c0050, 0x0126012a,
			0x013f004e, 0x0127012b, 0x01280129, 0x012c013b, 0x012d012e, 0x0053005a, 0x00540059, 0x012f0133, 0x00560082,
			0x00570058, 0x01300131, 0x01320161, 0x01340135, 0x005b005c, 0x01360137, 0x01380139, 0x0063005e, 0x008a005f,
			0x008e0060, 0x00610067, 0x013a0142, 0x013c0160, 0x00640084, 0x00650081, 0x013d0141, 0x0140015b, 0x01430144,
			0x00690070, 0x006a006d, 0x006b006c, 0x01450146, 0x01470148, 0x006e006f, 0x0149014a, 0x014b014c, 0x00710074,
			0x00720073, 0x014d014e, 0x014f0150, 0x00750076, 0x01510152, 0x01530154, 0x00780088, 0x0079007a, 0x01550156,
			0x01570159, 0x0158015a, 0x007d009b, 0x007e0094, 0x015c01c3, 0x015d017e, 0x015e017d, 0x015f0162, 0x00830087,
			0x01630165, 0x00850086, 0x01640166, 0x01670168, 0x0169016f, 0x0089008d, 0x016a016b, 0x008b008c, 0x016c016d,
			0x016e0170, 0x01710176, 0x01720175, 0x01730174, 0x00910092, 0x01770178, 0x0179017a, 0x017f01dc, 0x01d00095,
			0x01800182, 0x00c40097, 0x009800b2, 0x0099009e, 0x01e6009a, 0x01810184, 0x009c00af, 0x009d00cc, 0x018301a2,
			0x009f00a0, 0x01850186, 0x01880192, 0x0189018a, 0x00a300a4, 0x018b018c, 0x018d018f, 0x00a600ab, 0x01900191,
			0x00a800b9, 0x00a900ad, 0x00aa00ac, 0x01930195, 0x0194019f, 0x01960197, 0x00ae00b5, 0x0198019b, 0x00f100b0,
			0x00b100bc, 0x019901a1, 0x00b300b7, 0x00b400b6, 0x019a019c, 0x019d019e, 0x01a001a3, 0x00b800be, 0x01a401a9,
			0x00ba00c2, 0x00bb00bd, 0x01a501a6, 0x01a701ac, 0x01a801ae, 0x01aa01ad, 0x00c000da, 0x00c100ea, 0x01ab01ce,
			0x00c300cb, 0x01af01b4, 0x00c500eb, 0x00c600ca, 0x01b001b1, 0x00c800ce, 0x00c900cd, 0x01b201b5, 0x01b301d1,
			0x01b601b7, 0x01b801c2, 0x01b901ba, 0x00cf00d2, 0x01bb01bd, 0x00d100d7, 0x01bc01bf, 0x01be01c4, 0x00d400e0,
			0x00d500de, 0x00d600dd, 0x01c001c1, 0x01c501e7, 0x00d900f3, 0x01c601e4, 0x00f500db, 0x00dc00f4, 0x01c701cf,
			0x01c801c9, 0x00df00e4, 0x01ca01cd, 0x00ed00e1, 0x00f800e2, 0x01ff00e3, 0x01cb01cc, 0x01d201d5, 0x00e600f9,
			0x00e700ef, 0x00e800e9, 0x01d301d4, 0x01d601dd, 0x01d701e1, 0x00ec00f2, 0x01d801d9, 0x00ee00f6, 0x01da01db,
			0x00f000f7, 0x01de01df, 0x01e001e2, 0x01e301e5, 0x01e801e9, 0x01ea01eb, 0x01ec01ed, 0x01ee01f0, 0x01f101f4,
			0x01f201f3, 0x00fa00fd, 0x00fb00fc, 0x01f501f6, 0x01f701f8, 0x00fe00ff, 0x01fa01fb, 0x01fc01fd, };

	private static class BitStream {
		private int[] data;
		private int nbits;

		public BitStream() {
			data = new int[16];
		}

		public byte[] toByteArray() {
			byte[] r = new byte[(nbits >> 3) + ((nbits & 7) != 0 ? 1 : 0)];
			int n = nbits >> 5;
			int j = 0;
			for (int i = 0; i < n; i++) {
				int v = data[i];
				r[j++] = (byte) (v >> 24);
				r[j++] = (byte) (v >> 16);
				r[j++] = (byte) (v >> 8);
				r[j++] = (byte) v;
			}
			int m = nbits & 31;
			m = (m >> 3) + ((m & 7) != 0 ? 1 : 0);
			for (int i = 0; i < m; i++)
				r[j++] = (byte) (data[n] >> (24 - (i << 3)));
			return r;
		}

		public void append(int val, int len) {
			if (nbits + len > data.length << 5)
				data = Arrays.copyOf(data, data.length << 1);
			int shift = 32 - (nbits & 31) - len;
			if (shift >= 0) {
				data[nbits >> 5] |= val << shift;
			} else {
				data[nbits >> 5] |= val >>> -shift;
				data[(nbits >> 5) + 1] = val << (32 + shift);
			}
			nbits += len;
		}

		public BitStream padding() {
			int e = nbits & 7;
			if (e > 0)
				append(255 >> e, 8 - e);
			return this;
		}
	}

	private static byte[] huffmanEncode(byte[] in) {
		BitStream bs = new BitStream();
		for (byte b : in) {
			int val = HUFFMAN_ENCODE[b & 0xff];
			int pos = Integer.numberOfLeadingZeros(val);
			bs.append((-1 >>> (pos + 1)) & val, 31 - pos);
		}
		return bs.padding().toByteArray();
	}

	private static byte[] huffmanDecode(ByteBuffer in, int len) {
		Octets r = new Octets();
		for (int state = HUFFMAN_DECODE[0], i = 0; i < len; i++) {
			byte b = in.get();
			for (int j = 0; j < 8; j++, b <<= 1) {
				state = (b & 0x80) == 0 ? (state >> 16) : (state & 0xffff);
				if (state >= HUFFMAN_DECODE.length) {
					r.push_byte((byte) (state - HUFFMAN_DECODE.length));
					state = 0;
				}
				state = HUFFMAN_DECODE[state];
			}
		}
		return r.getBytes();
	}

	public static class Entry {
		private final String key;
		private final String value;
		private final int size;

		Entry(String key, String value) {
			this.key = key;
			this.value = value;
			this.size = key.length() + value.length() + 32;
		}

		int size() {
			return size;
		}

		public String getKey() {
			return key;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return key + ": " + value;
		}

	}

	private final static int DYNAMIC_INDEX_FIRST = 62;
	private final static Entry staticTable[] = new Entry[DYNAMIC_INDEX_FIRST - 1];
	private final static Map<String, Integer> staticEntries = new HashMap<>();

	public enum StaticTable {
		AUTHORITY(":authority"), METHOD_GET(":method: GET"), METHOD_POST(":method: POST"), PATH_ROOT(":path: /"),
		PATH_INDEX(":path: /index.html"), SCHEME_HTTP(":scheme: http"), SCHEME_HTTPS(":scheme: https"),
		STATUS_200(":status: 200"), STATUS_204(":status: 204"), STATUS_206(":status: 206"), STATUS_304(":status: 304"),
		STATUS_400(":status: 400"), STATUS_404(":status: 404"), STATUS_500(":status: 500"),
		ACCEPT_CHARSET("accept-charset"), ACCEPT_ENCODING("accept-encoding: gzip, deflate"),
		ACCEPT_LANGUAGE("accept-language"), ACCEPT_RANGES("accept-ranges"), ACCEPT("accept"),
		ACCESS_CONTROL_ALLOW_ORIGIN("access-control-allow-origin"), AGE("age"), ALLOW("allow"),
		AUTHORIZATION("authorization"), CACHE_CONTROL("cache-control"), CONTENT_DISPOSITION("content-disposition"),
		CONTENT_ENCODING("content-encoding"), CONTENT_LANGUAGE("content-language"), CONTENT_LENGTH("content-length"),
		CONTENT_LOCATION("content-location"), CONTENT_RANGE("content-range"), CONTENT_TYPE("content-type"),
		COOKIE("cookie"), DATE("date"), ETAG("etag"), EXPECT("expect"), EXPIRES("expires"), FROM("from"), HOST("host"),
		IF_MATCH("if-match"), IF_MODIFIED_SINCE("if-modified-since"), IF_NONE_MATCH("if-none-match"),
		IF_RANGE("if-range"), IF_UNMODIFIED_SINCE("if-unmodified-since"), LAST_MODIFIED("last-modified"), LINK("link"),
		LOCATION("location"), MAX_FORWARDS("max-forwards"), PROXY_AUTHENTICATE("proxy-authenticate"),
		PROXY_AUTHORIZATION("proxy-authorization"), RANGE("range"), REFERER("referer"), REFRESH("refresh"),
		RETRY_AFTER("retry-after"), SERVER("server"), SET_COOKIE("set-cookie"),
		STRICT_TRANSPORT_SECURITY("strict-transport-security"), TRANSFER_ENCODING("transfer-encoding"),
		USER_AGENT("user-agent"), VARY("vary"), VIA("via"), WWW_AUTHENTICATE("www-authenticate");

		private final String line;

		StaticTable(String line) {
			this.line = line;
		}
	}

	static {
		for (StaticTable item : StaticTable.values()) {
			String line = item.line;
			int pos = line.lastIndexOf(':');
			Entry entry;
			if (pos != -1 && pos != 0) {
				entry = new Entry(line.substring(0, pos), line.substring(pos + 2));
				staticEntries.putIfAbsent(entry.toString(), item.ordinal() + 1);
			} else {
				entry = new Entry(line, "");
			}
			staticTable[item.ordinal()] = entry;
			staticEntries.putIfAbsent(entry.getKey(), item.ordinal() + 1);
		}
	}

	private static class PackedInt {
		private final int mask;

		PackedInt(int N) {
			this.mask = (1 << N) - 1;
		}

		void encode(Octets octets, int prefix, int i) {
			if (i >= mask) {
				octets.push_byte((byte) (prefix | mask));
				for (i -= mask; i >= 128; i >>= 7)
					octets.push_byte((byte) (i | 128));
				octets.push_byte((byte) i);
			} else
				octets.push_byte((byte) (prefix | i));
		}

		int decode(ByteBuffer in, int r) {
			r &= mask;
			if (r == mask) {
				int m = 0, b;
				do {
					b = in.get();
					r += (b & 127) << m;
					m += 7;
				} while ((b & 128) == 128);
			}
			return r;
		}
	}

	private final static PackedInt packedInt4 = new PackedInt(4);
	private final static PackedInt packedInt5 = new PackedInt(5);
	private final static PackedInt packedInt6 = new PackedInt(6);
	private final static PackedInt packedInt7 = new PackedInt(7);

	public enum UpdateMode {
		UPDATE, NO, NEVER
	}

	public class Encoder {
		private final int maxFrameSize;
		private final byte huffman;
		private final List<Octets> payloads = new ArrayList<>();

		Encoder(int maxFrameSize, boolean huffman) {
			this.maxFrameSize = maxFrameSize;
			this.huffman = (byte) (huffman ? 0x80 : 0x00);
			this.payloads.add(new Octets());
		}

		private void merge(Octets octets) {
			Octets cur = payloads.get(payloads.size() - 1);
			if (cur.size() + octets.size() <= maxFrameSize)
				cur.append(octets);
			else
				payloads.add(octets);
		}

		private void encode(String text, Octets octets) {
			byte[] binary = text.getBytes(StandardCharsets.ISO_8859_1);
			if (huffman != 0)
				binary = huffmanEncode(binary);
			packedInt7.encode(octets, huffman, binary.length);
			octets.insert(octets.size(), binary);
		}

		public void add(String key, String val, UpdateMode mode) {
			Octets octets = new Octets();
			key = key.toLowerCase();
			int index = find(key + ": " + val);
			if (index > 0) {
				packedInt7.encode(octets, 0x80, index);
				merge(octets);
				return;
			}
			index = find(key);
			if (index > 0) {
				switch (mode) {
				case UPDATE:
					index = RFC7541.this.update(index, new Entry(toEntry(index).getKey(), val));
					packedInt6.encode(octets, 0x40, index);
					break;
				case NO:
					packedInt4.encode(octets, 0x00, index);
					break;
				case NEVER:
					packedInt4.encode(octets, 0x10, index);
				}
			} else {
				switch (mode) {
				case UPDATE:
					RFC7541.this.update(0, new Entry(key, val));
					octets.push_byte((byte) 0x40);
					break;
				case NO:
					octets.push_byte((byte) 0x00);
					break;
				case NEVER:
					octets.push_byte((byte) 0x10);
				}
				encode(key, octets);
			}
			encode(val, octets);
			merge(octets);
		}

		public void update(int size) {
			Octets octets = new Octets();
			packedInt5.encode(octets, 0x20, size);
			merge(octets);
		}

		public List<Octets> getPayloads() {
			return payloads;
		}
	}

	private class Decoder {
		private final ByteBuffer bb;

		Decoder(ByteBuffer bb) {
			this.bb = bb;
		}

		private String decode() {
			int prefix = bb.get();
			int len = packedInt7.decode(bb, prefix);
			byte[] data;
			if ((prefix & 0x80) != 0)
				data = huffmanDecode(bb, len);
			else
				bb.get(data = new byte[len]);
			return new String(data, StandardCharsets.ISO_8859_1);
		}

		private Entry toEntry(int index) {
			return new Entry(index != 0 ? RFC7541.this.toEntry(index).key : decode(), decode());
		}

		public void run(Consumer<Entry> consumer) {
			while (bb.hasRemaining()) {
				int prefix = bb.get();
				if ((prefix & 0x80) != 0) {
					consumer.accept(RFC7541.this.toEntry(packedInt7.decode(bb, prefix)));
				} else if ((prefix & 0x40) != 0) {
					int index = packedInt6.decode(bb, prefix);
					Entry entry = toEntry(index);
					update(index, entry);
					consumer.accept(entry);
				} else if ((prefix & 0x20) != 0) {
					update(packedInt5.decode(bb, prefix));
				} else {
					consumer.accept(toEntry(packedInt4.decode(bb, prefix)));
				}
			}
		}
	}

	private final List<Entry> table = new ArrayList<>();
	private int capacity;
	private int size = 0;

	public RFC7541(int capacity) {
		this.capacity = capacity;
	}

	Entry toEntry(int index) {
		if (index == 0)
			throw new RuntimeException("bad index 0");
		return index < DYNAMIC_INDEX_FIRST ? staticTable[index - 1] : table.get(index - DYNAMIC_INDEX_FIRST);
	}

	int find(String key) {
		Integer e = staticEntries.get(key);
		if (e != null)
			return e;
		for (int n = 0; n < table.size(); n++)
			if (table.get(n).getKey().equalsIgnoreCase(key))
				return n + DYNAMIC_INDEX_FIRST;
		return 0;
	}

	private int evict(int index, int retain) {
		int lock = index - DYNAMIC_INDEX_FIRST;
		if (retain <= 0) {
			if (lock < 0) {
				table.clear();
				size = 0;
			} else {
				Entry entry = table.get(lock);
				table.clear();
				table.add(entry);
				size = entry.size();
				lock = 0;
			}
		} else {
			int n = table.size();
			if (lock < 0) {
				for (; --n >= 0 && retain < size;)
					size -= table.remove(n).size();
			} else {
				for (; --n >= 0 && retain < size && n != lock;)
					size -= table.remove(n).size();
				for (; --n >= 0 && retain < size; lock--)
					size -= table.remove(n).size();
			}
		}
		return lock + DYNAMIC_INDEX_FIRST;
	}

	int update(int index, Entry entry) {
		int retain = capacity - entry.size();
		if (retain < size)
			index = evict(index, retain);
		size += entry.size();
		table.add(0, entry);
		return index;
	}

	public void update(int capacity) {
		if (capacity < this.capacity)
			evict(0, capacity);
		this.capacity = capacity;
	}

	public Encoder createEncoder(int maxFrameSize, boolean huffman) {
		return new Encoder(maxFrameSize, huffman);
	}

	public Encoder createEncoder(int maxFrameSize) {
		return new Encoder(maxFrameSize, true);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Dynamic Table:\n");
		for (int i = 0; i < table.size(); i++) {
			Entry entry = table.get(i);
			sb.append(String.format("[%3d] (s = %3d) %s\n", i + 1, entry.size(), entry));
		}
		sb.append(String.format("      Table size:%4d, Capacity:%4d", size, capacity));
		return sb.toString();
	}

	public void decode(ByteBuffer bb, Consumer<Entry> consumer) {
		new Decoder(bb).run(consumer);
	}
}
