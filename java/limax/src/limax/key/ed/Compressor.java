package limax.key.ed;

import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import limax.codec.CodecException;
import limax.codec.Octets;
import limax.codec.RFC2118Decode;
import limax.codec.RFC2118Encode;
import limax.codec.SinkOctets;

public enum Compressor {
	NONE {
		@Override
		public byte[] encode(byte[] data) {
			byte[] b = new byte[data.length + 1];
			System.arraycopy(data, 0, b, 1, data.length);
			return b;
		}

		@Override
		byte[] decode(byte[] data, int off, int len) {
			return Arrays.copyOfRange(data, 1, len + 1);
		}
	},
	RFC2118 {
		@Override
		public byte[] encode(byte[] data) throws CodecException {
			Octets octets = new Octets();
			octets.push_byte((byte) 1);
			RFC2118Encode codec = new RFC2118Encode(new SinkOctets(octets));
			codec.update(data, 0, data.length);
			codec.flush();
			return octets.size() - 1 < data.length ? octets.getBytes() : NONE.encode(data);
		}

		@Override
		byte[] decode(byte[] data, int off, int len) throws CodecException {
			Octets octets = new Octets();
			RFC2118Decode codec = new RFC2118Decode(new SinkOctets(octets));
			codec.update(data, off, len);
			codec.flush();
			return octets.getBytes();
		}
	},
	ZIP {
		@Override
		public byte[] encode(byte[] data) {
			Deflater codec = new Deflater();
			codec.setInput(data);
			codec.finish();
			byte[] b = new byte[data.length + 1];
			int len = codec.deflate(b, 1, data.length);
			codec.end();
			if (len == data.length) {
				System.arraycopy(data, 0, b, 1, data.length);
				return b;
			}
			b[0] = 2;
			return Arrays.copyOf(b, len);
		}

		@Override
		byte[] decode(byte[] data, int off, int len) throws CodecException {
			Inflater codec = new Inflater();
			codec.setInput(data, off, len);
			try {
				byte[] b = new byte[len << 1];
				len = codec.inflate(b);
				if (len < b.length)
					return Arrays.copyOf(b, len);
				Octets octets = new Octets(b);
				while (true) {
					len = codec.inflate(b);
					if (len == 0)
						return octets.getBytes();
					octets.append(b, 0, len);
				}
			} catch (DataFormatException e) {
				throw new CodecException(e);
			} finally {
				codec.end();
			}
		}
	};

	abstract byte[] encode(byte[] data) throws CodecException;

	abstract byte[] decode(byte[] data, int off, int len) throws CodecException;

	static byte[] decode(byte[] data) throws CodecException {
		try {
			return Compressor.values()[data[0]].decode(data, 1, data.length - 1);
		} catch (CodecException e) {
			throw e;
		} catch (Exception e) {
			throw new CodecException(e);
		}
	}
}
