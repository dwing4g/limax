package limax.codec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacSHA1 extends Hmac {
	public HmacSHA1(Codec sink, byte[] key, int off, int len) {
		super(sink);
		try {
			mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(key, off, len, "HmacSHA1"));
		} catch (Exception e) {
		}
	}

	public HmacSHA1(byte[] key, int off, int len) {
		this(NullCodec.getInstance(), key, off, len);
	}
}
