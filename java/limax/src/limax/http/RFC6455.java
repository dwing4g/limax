package limax.http;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.BiConsumer;

import limax.codec.Octets;
import limax.util.Helper;
import limax.util.Pair;

public class RFC6455 {
	public final static byte opcodeCont = 0;
	public final static byte opcodeText = 1;
	public final static byte opcodeBinary = 2;
	public final static byte opcodeClose = 8;
	public final static byte opcodePing = 9;
	public final static byte opcodePong = 10;

	public final static short closeNormal = 1000;
	public final static short closeGoaway = 1001;
	public final static short closeProtocol = 1002;
	public final static short closeNotSupportFrame = 1003;
	public final static short closeVolatilePolicy = 1006;
	public final static short closeSizeExceed = 1009;
	public final static short closeServerException = 1011;

	public static class RFC6455Exception extends Exception {
		private static final long serialVersionUID = -6368119477527251729L;
		private final byte[] code;

		public RFC6455Exception(short code, String message) {
			super(message);
			byte[] tmp = message.getBytes(StandardCharsets.UTF_8);
			byte[] msg = new byte[tmp.length + 2];
			msg[0] = (byte) (code >>> 8);
			msg[1] = (byte) code;
			System.arraycopy(tmp, 0, msg, 2, tmp.length);
			this.code = msg;
		}

		public byte[] getCode() {
			return code;
		}
	}

	private final int maxMessageSize;
	private final int mode;
	private final Octets data = new Octets();
	private final Queue<Pair<Byte, byte[]>> queue = new ArrayDeque<>();
	private int stage = 0;
	private byte opcode;
	private boolean fin;
	private long length;
	private byte mask[] = new byte[4];
	private volatile boolean shutdownOut;
	private boolean shutdownIn;

	public RFC6455(int maxMessageSize, boolean server) {
		this.maxMessageSize = maxMessageSize;
		this.mode = server ? 128 : 0;
	}

	private int checkPolicy() throws RFC6455Exception {
		if (length < 0 || length + data.size() > maxMessageSize)
			throw new RFC6455Exception(closeSizeExceed,
					"messageSize = " + (length + data.size()) + " but MaxMessageSize = " + maxMessageSize);
		data.clear();
		return mode != 0 ? 10 : 18;
	}

	private void consume(byte c) {
		data.push_byte(c);
		length--;
	}

	private void process(byte c) throws RFC6455Exception {
		switch (stage) {
		case 0:
			fin = (c & 0x80) != 0;
			opcode = (byte) (c & 15);
			stage = 1;
			break;
		case 1:
			if ((c & 128) != mode)
				throw new RFC6455Exception(closeVolatilePolicy, "Volatile 5.3.");
			length = c & 127;
			if (length == 127) {
				length = 0;
				stage = 2;
			} else if (length == 126) {
				length = 0;
				stage = 8;
			} else {
				stage = checkPolicy();
			}
			break;
		case 2:
		case 3:
		case 4:
		case 5:
		case 6:
		case 7:
		case 8:
			length = (length << 8) | (c & 0xff);
			stage++;
			break;
		case 9:
			length = (length << 8) | (c & 0xff);
			stage = checkPolicy();
			break;
		case 10:
		case 11:
		case 12:
		case 13:
			mask[stage - 10] = c;
			stage++;
			break;
		case 14:
			stage = 15;
			consume((byte) (mask[0] ^ c));
			break;
		case 15:
			stage = 16;
			consume((byte) (mask[1] ^ c));
			break;
		case 16:
			stage = 17;
			consume((byte) (mask[2] ^ c));
			break;
		case 17:
			stage = 14;
			consume((byte) (mask[3] ^ c));
			break;
		case 18:
			consume(c);
			break;
		}
	}

	public Queue<Pair<Byte, byte[]>> unwrap(ByteBuffer in) throws RFC6455Exception {
		while (in.hasRemaining()) {
			process(in.get());
			if (stage > 13 && 0 == length) {
				if (fin)
					queue.offer(new Pair<>(opcode, data.getBytes()));
				stage = 0;
			}
		}
		return queue;
	}

	public ByteBuffer wrap(byte opcode, byte[] data) {
		int len = data.length;
		ByteBuffer bb = ByteBuffer.allocate(len + 14);
		bb.put((byte) (opcode | 0x80));
		byte mode_r = (byte) (mode ^ 0x80);
		if (len < 126) {
			bb.put((byte) (len | mode_r));
		} else if (len < 65536) {
			bb.put((byte) (126 | mode_r));
			bb.put((byte) (len >> 8));
			bb.put((byte) (len));
		} else {
			bb.put((byte) (127 | mode_r));
			bb.putLong(len);
		}
		if (mode != 0) {
			bb.put(data);
		} else {
			byte[] mask = Helper.makeRandValues(4);
			bb.put(mask);
			for (int i = 0; i < len; i++)
				bb.put((byte) (data[i] ^ mask[i & 3]));
		}
		bb.flip();
		return bb;
	}

	public void send(Runnable action) {
		if (!shutdownOut)
			action.run();
	}

	public void sendClose(Runnable action) {
		send(() -> {
			shutdownOut = true;
			action.run();
		});
	}

	public void recvClose(byte[] data, BiConsumer<Short, String> consumer) {
		if (shutdownIn)
			return;
		shutdownIn = true;
		short code;
		String reason;
		if (data.length >= 2) {
			code = (short) (((data[0] & 0xff) << 8) + (data[1] & 0xff));
			reason = new String(data, 2, data.length - 2, StandardCharsets.UTF_8);
		} else {
			code = RFC6455.closeProtocol;
			reason = "abnormal close frame without code";
		}
		consumer.accept(code, reason);
	}
}
