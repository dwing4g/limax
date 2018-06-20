package limax.node.js.modules;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;

import limax.node.js.Buffer;
import limax.node.js.EventLoop;
import limax.node.js.Module;

public final class String_decoder implements Module {
	public String_decoder(EventLoop eventLoop) {

	}

	public static class Decoder {
		private final CharsetDecoder cd;
		private final ByteBuffer rm = ByteBuffer.allocate(16);

		Decoder(String encoding) {
			this.cd = Buffer.translateEncoding(encoding).newDecoder();
		}

		private StringBuilder append(ByteBuffer in) {
			StringBuilder sb = new StringBuilder();
			CharBuffer cb = CharBuffer.allocate(4096);
			while (rm.position() > 0 && in.hasRemaining()) {
				rm.put(in.get()).flip();
				cd.decode(rm, cb, false);
				rm.compact();
			}
			while (true) {
				cd.decode(in, cb, false);
				if (cb.position() == 0) {
					if (in.hasRemaining())
						rm.put(in);
					return sb;
				}
				sb.append(cb.flip());
			}
		}

		public String write(ByteBuffer bb) {
			return append(bb.duplicate()).toString();
		}

		public String end(ByteBuffer bb) {
			StringBuilder sb = bb == null ? new StringBuilder() : append(bb.duplicate());
			CharBuffer cb = CharBuffer.allocate(16);
			cd.decode(ByteBuffer.allocate(0), cb, true);
			cb.flip();
			return sb.append(cb).toString();
		}
	}

	public Decoder createDecoder(String encoding) {
		return new Decoder(encoding);
	}
}
