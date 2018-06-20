package limax.key.ed;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

import limax.codec.CodecException;
import limax.key.KeyAllocator;

public class Transformer {
	private final KeyTranslate keyTranslate;

	public class Encoder {
		private final URI uri;
		private final KeyProtector keyProtector;
		private final Compressor compressor;

		Encoder(URI uri, KeyProtector keyProtector, Compressor compressor) {
			this.uri = uri;
			this.keyProtector = keyProtector;
			this.compressor = compressor;
		}

		public byte[] encode(byte[] data) throws CodecException {
			return Transformer.this.encode(uri, keyProtector, compressor, data);
		}
	}

	public Transformer(KeyAllocator keyAllocator) {
		this.keyTranslate = new KeyTranslate() {
			@Override
			public KeyRep createKeyRep(URI uri) throws Exception {
				return new KeyRep(keyAllocator.createKeyDesc(uri));
			}

			@Override
			public KeyRep createKeyRep(byte[] ident) throws Exception {
				return new KeyRep(keyAllocator.createKeyDesc(ident));
			}
		};
	}

	public Transformer(Map<URI, byte[]> keyAllocator) {
		this.keyTranslate = new KeyTranslate() {
			@Override
			public KeyRep createKeyRep(URI uri) throws Exception {
				byte[] key = keyAllocator.get(uri);
				Objects.requireNonNull(key, () -> "Group URI <" + uri + "> not found");
				byte[] ident = uri.toASCIIString().getBytes();
				return new KeyRep(ident, key);
			}

			@Override
			public KeyRep createKeyRep(byte[] ident) throws Exception {
				return createKeyRep(new URI(new String(ident)));
			}
		};
	}

	public byte[] encode(URI uri, KeyProtector keyProtector, Compressor compressor, byte[] data) throws CodecException {
		return keyProtector.encode(keyTranslate, uri, compressor.encode(data));
	}

	public Encoder getEncoder(URI uri, KeyProtector keyProtector, Compressor compressor) {
		return new Encoder(uri, keyProtector, compressor);
	}

	public byte[] decode(byte[] data) throws CodecException {
		return Compressor.decode(KeyProtector.decode(keyTranslate, data));
	}
}
