package limax.codec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacMD5 extends Hmac {
	public HmacMD5(Codec sink, byte[] key, int off, int len) {
		super(sink);
		try {
			mac = Mac.getInstance("HmacMD5");
			mac.init(new SecretKeySpec(key, off, len, "HmacMD5"));
		} catch (Exception e) {
		}
	}

	public HmacMD5(byte[] key, int off, int len) {
		this(NullCodec.getInstance(), key, off, len);
	}
}
