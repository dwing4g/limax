package limax.codec;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA1 extends MD {
	public SHA1(Codec codec) {
		super(codec);
		try {
			md = MessageDigest.getInstance("SHA1");
		} catch (NoSuchAlgorithmException e) {
		}
	}

	public SHA1() {
		this(NullCodec.getInstance());
	}

	public static byte[] digest(byte[] message) {
		try {
			return MessageDigest.getInstance("SHA1").digest(message);
		} catch (NoSuchAlgorithmException e) {
		}
		return null;
	}
}
