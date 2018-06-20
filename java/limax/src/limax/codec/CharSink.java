package limax.codec;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public final class CharSink implements Codec {
	private CharsetDecoder cd;
	private final CharConsumer consumer;
	private final ByteBuffer bb = ByteBuffer.allocate(16);
	private final CharBuffer cb = CharBuffer.allocate(4096);

	public CharSink(Charset charset, CharConsumer consumer) {
		this.cd = charset.newDecoder();
		this.consumer = consumer;
	}

	public CharSink(CharConsumer consumer) {
		this.consumer = consumer;
	}

	public void setCharset(Charset charset) {
		cd = charset.newDecoder();
	}

	@Override
	public void update(byte c) throws CodecException {
		update(new byte[] { c }, 0, 1);
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		try {
			ByteBuffer tmp = ByteBuffer.wrap(data, off, len);
			while (bb.position() > 0 && tmp.hasRemaining()) {
				bb.put(tmp.get()).flip();
				cd.decode(bb, cb, false);
				bb.compact();
			}
			while (true) {
				cd.decode(tmp, cb, false);
				if (cb.position() == 0) {
					if (tmp.hasRemaining())
						bb.put(tmp);
					break;
				}
				update();
			}
		} catch (Exception e) {
			throw new CodecException(e);
		}
	}

	@Override
	public void flush() throws CodecException {
		try {
			bb.flip();
			cd.decode(bb, cb, true);
			cd.flush(cb);
			update();
			if (consumer instanceof JSONDecoder)
				((JSONDecoder) consumer).flush();
		} catch (Exception e) {
			throw new CodecException(e);
		}
	}

	private void update() {
		for (cb.flip(); cb.hasRemaining();)
			consumer.accept(cb.get());
		cb.clear();
	}

}
