package limax.codec;

public final class NullCodec {
	private static Codec codec = new Codec() {
		@Override
		public void update(byte c) throws CodecException {
		}

		@Override
		public void update(byte[] data, int off, int len) throws CodecException {
		}

		@Override
		public void flush() throws CodecException {
		}
	};

	public static Codec getInstance() {
		return codec;
	}
}
