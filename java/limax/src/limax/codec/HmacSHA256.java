package limax.codec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacSHA256 extends Hmac {
	public HmacSHA256(Codec sink, byte[] key, int off, int len) {
		super(sink);
		try {
			mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, off, len, "HmacSHA256"));
		} catch (Exception e) {
		}
	}

	public HmacSHA256(byte[] key, int off, int len) {
		this(NullCodec.getInstance(), key, off, len);
	}
}
