package limax.node.js.modules.http;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import limax.codec.Codec;
import limax.codec.CodecException;

public class MessageBuilder implements Codec {
	private final static int LINEMAX = 16384;
	private final static byte CR = '\r';
	private final static byte LF = '\n';
	private ByteBuffer accumulate = ByteBuffer.allocate(LINEMAX);
	private Message message;
	private Message messageReady;
	private ByteBuffer bb;

	private enum Stage {
		START, HEADER, BODY
	}

	private Stage stage = Stage.START;

	public MessageBuilder(boolean isRequest) {
		this.message = new Message(isRequest);
	}

	private boolean accept(byte c) throws CodecException {
		switch (c) {
		case CR:
			break;
		case LF:
			int len = accumulate.position();
			String line = new String(accumulate.array(), 0, len, StandardCharsets.UTF_8);
			if (stage == Stage.START) {
				stage = Stage.HEADER;
				message.setStartLine(line);
			} else if (len > 0) {
				message.setHeadLine(line);
			} else {
				stage = Stage.BODY;
				messageReady = message;
				message = null;
				accumulate = null;
				return false;
			}
			accumulate.clear();
			break;
		default:
			accumulate.put(c);
		}
		return true;
	}

	@Override
	public void update(byte c) throws CodecException {
		if (stage == Stage.BODY)
			update(new byte[] { c }, 0, 1);
		else
			accept(c);
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		if (stage != Stage.BODY)
			while (len-- > 0 && accept(data[off++]))
				;
		bb = len > 0 ? ByteBuffer.wrap(data, off, len) : null;
	}

	public Message getMessage() {
		Message tmp = messageReady;
		messageReady = null;
		return tmp;
	}

	public ByteBuffer getByteBuffer() {
		ByteBuffer tmp = bb;
		bb = null;
		return tmp;
	}

	@Override
	public void flush() throws CodecException {
	}
}
