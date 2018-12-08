package limax.key.ed;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import limax.codec.ByteArrayCodecFunction;
import limax.key.KeyAllocator;
import limax.key.KeyException;
import limax.util.Pair;

public class Transformer {
	private final KeyTranslate keyTranslate;

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

	private static Long getLifetime(String fragment) {
		try {
			int len = fragment.length();
			switch (fragment.charAt(len - 1)) {
			case 'h':
			case 'H':
				return TimeUnit.HOURS.toMillis(Long.parseLong(fragment.substring(0, len - 1)));
			case 'm':
			case 'M':
				return TimeUnit.MINUTES.toMillis(Long.parseLong(fragment.substring(0, len - 1)));
			case 's':
			case 'S':
				return TimeUnit.SECONDS.toMillis(Long.parseLong(fragment.substring(0, len - 1)));
			}
			return Long.parseLong(fragment);
		} catch (Exception e) {
		}
		return null;
	}

	public Transformer(Map<URI, byte[]> keyAllocator) {
		Map<URI, Pair<byte[], Long>> keys = new HashMap<>();
		keyAllocator.entrySet().forEach(e -> {
			try {
				URI raw = e.getKey();
				URI uri = new URI(raw.getScheme(), raw.getSchemeSpecificPart(), null);
				keys.put(uri, new Pair<>(e.getValue(), getLifetime(raw.getFragment())));
			} catch (Exception e1) {
			}
		});
		this.keyTranslate = new KeyTranslate() {
			private KeyRep makeKeyRep(byte[] ident, byte[] key) {
				byte[] _key = Arrays.copyOf(key, key.length + ident.length);
				System.arraycopy(ident, 0, _key, key.length, ident.length);
				return new KeyRep(ident, _key);
			}

			@Override
			public KeyRep createKeyRep(URI uri) throws Exception {
				Pair<byte[], Long> pair = keys.get(
						uri.getFragment() == null ? uri : new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null));
				if (pair == null)
					throw new KeyException(KeyException.Type.UnsupportedURI);
				byte[] ident = new URI(uri.getScheme(), uri.getSchemeSpecificPart(),
						Long.toString(System.currentTimeMillis(), Character.MAX_RADIX)).toASCIIString().getBytes();
				return makeKeyRep(ident, pair.getKey());
			}

			@Override
			public KeyRep createKeyRep(byte[] ident) throws Exception {
				URI uri = new URI(new String(ident));
				Pair<byte[], Long> pair = keys.get(new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null));
				if (pair == null)
					throw new KeyException(KeyException.Type.UnsupportedURI);
				Long lifetime = pair.getValue();
				if (lifetime != null) {
					long timestamp;
					try {
						timestamp = Long.parseLong(uri.getFragment(), Character.MAX_RADIX);
					} catch (Exception e) {
						throw new KeyException(KeyException.Type.MalformedKeyIdent);
					}
					long elapsed = System.currentTimeMillis() - timestamp;
					if (elapsed < 0 || elapsed > lifetime)
						throw new KeyException(KeyException.Type.ServerRekeyed);
				}
				return makeKeyRep(ident, pair.getKey());
			}
		};
	}

	public ByteArrayCodecFunction getDecoder() {
		return data -> Compressor.decode(KeyProtector.decode(keyTranslate, data));
	}

	public ByteArrayCodecFunction getEncoder(URI uri, KeyProtector keyProtector, Compressor compressor) {
		return data -> keyProtector.encode(keyTranslate, uri, compressor.encode(data));
	}
}
