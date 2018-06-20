package limax.codec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

public final class CharSource implements Appendable, Source {
	private final CharsetEncoder ce;
	private final Codec sink;
	private final byte[] buffer = new byte[8192];
	private final ByteBuffer bb = ByteBuffer.wrap(buffer);
	private final CharBuffer cb = CharBuffer.allocate(1024);

	public CharSource(Charset charset, Codec sink) {
		this.ce = charset.newEncoder();
		this.sink = sink;
	}

	@Override
	public Appendable append(CharSequence csq) throws IOException {
		return append(csq, 0, csq.length());
	}

	@Override
	public Appendable append(CharSequence csq, int start, int end) throws IOException {
		if (cb.remaining() >= end - start) {
			cb.append(csq, start, end);
			return this;
		}
		if (cb.position() > 0)
			update();
		update(CharBuffer.wrap(csq, start, end - start));
		return this;
	}

	@Override
	public Appendable append(char c) throws IOException {
		if (!cb.hasRemaining())
			update();
		cb.append(c);
		return this;
	}

	@Override
	public void flush() throws CodecException {
		try {
			update();
			cb.flip();
			ce.encode(cb, bb, true);
			ce.flush(bb);
			sink.update(buffer, 0, bb.position());
		} catch (Exception e) {
			throw new CodecException(e);
		}
	}

	private void update() throws IOException {
		try {
			cb.flip();
			while (true) {
				ce.encode(cb, bb, false);
				if (bb.position() == 0) {
					cb.compact();
					break;
				}
				sink.update(buffer, 0, bb.position());
				bb.clear();
			}
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	private void update(CharBuffer tmp) throws IOException {
		try {
			while (cb.position() > 0 && tmp.hasRemaining()) {
				cb.put(tmp.get()).flip();
				ce.encode(cb, bb, false);
				cb.compact();
			}
			while (true) {
				ce.encode(tmp, bb, false);
				if (bb.position() == 0) {
					if (tmp.hasRemaining())
						cb.put(tmp);
					break;
				}
				sink.update(buffer, 0, bb.position());
				bb.clear();
			}
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

}
