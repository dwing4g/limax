package limax.codec;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA256 extends MD {
	public SHA256(Codec codec) {
		super(codec);
		try {
			md = MessageDigest.getInstance("SHA1");
		} catch (NoSuchAlgorithmException e) {
		}
	}

	public SHA256() {
		this(NullCodec.getInstance());
	}

	public static byte[] digest(byte[] message) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(message);
		} catch (NoSuchAlgorithmException e) {
		}
		return null;
	}
}
