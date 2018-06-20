package limax.codec;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5 extends MD {
	public MD5(Codec codec) {
		super(codec);
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
		}
	}

	public MD5() {
		this(NullCodec.getInstance());
	}

	public static byte[] digest(byte[] message) {
		try {
			return MessageDigest.getInstance("MD5").digest(message);
		} catch (NoSuchAlgorithmException e) {
		}
		return null;
	}
}
