package limax.key.ed;

import java.net.URI;
import java.security.SignatureException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import limax.codec.CodecException;
import limax.codec.MD5;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.util.Helper;

public enum KeyProtector {
	HMACSHA224, HMACSHA256, HMACSHA384, HMACSHA512, TripleDESCBC {
		@Override
		byte[] encode(byte[] ident, byte[] key, byte[] data) throws Exception {
			OctetsStream os = new OctetsStream();
			os.push_byte((byte) ordinal());
			byte[] iv = Helper.makeRandValues(8);
			os.marshal(ident);
			os.marshal(iv);
			Cipher c = Cipher.getInstance("DESede/CBC/PKCS5Padding");
			c.init(Cipher.ENCRYPT_MODE, SecretKeyFactory.getInstance("DESede").generateSecret(new DESedeKeySpec(key)),
					new IvParameterSpec(iv));
			os.marshal(c.doFinal(data));
			return os.getBytes();
		}

		@Override
		byte[] decode(byte[] key, OctetsStream os) throws Exception {
			Cipher c = Cipher.getInstance("DESede/CBC/PKCS5Padding");
			c.init(Cipher.DECRYPT_MODE, SecretKeyFactory.getInstance("DESede").generateSecret(new DESedeKeySpec(key)),
					new IvParameterSpec(os.unmarshal_bytes()));
			return c.doFinal(os.unmarshal_bytes());
		}
	},
	AESCBC128 {
		@Override
		byte[] encode(byte[] ident, byte[] key, byte[] data) throws Exception {
			OctetsStream os = new OctetsStream();
			os.push_byte((byte) ordinal());
			byte[] iv = Helper.makeRandValues(16);
			os.marshal(ident);
			os.marshal(iv);
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
			c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(MD5.digest(key), "AES"), new IvParameterSpec(iv));
			os.marshal(c.doFinal(data));
			return os.getBytes();
		}

		@Override
		byte[] decode(byte[] key, OctetsStream os) throws Exception {
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
			c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(MD5.digest(key), "AES"),
					new IvParameterSpec(os.unmarshal_bytes()));
			return c.doFinal(os.unmarshal_bytes());
		}
	};

	byte[] encode(byte[] ident, byte[] key, byte[] data) throws Exception {
		OctetsStream os = new OctetsStream();
		os.push_byte((byte) ordinal());
		os.marshal(ident);
		Mac mac = Mac.getInstance(name());
		mac.init(new SecretKeySpec(key, name()));
		os.marshal(data);
		os.marshal(mac.doFinal(data));
		return os.getBytes();
	}

	byte[] encode(KeyTranslate keyTranslate, URI uri, byte[] data) throws CodecException {
		try {
			KeyRep keyRep = keyTranslate.createKeyRep(uri);
			return encode(keyRep.getIdent(), keyRep.getKey(), data);
		} catch (Exception e) {
			throw new CodecException(e);
		}
	}

	byte[] decode(byte[] key, OctetsStream os) throws Exception {
		Mac mac = Mac.getInstance(name());
		mac.init(new SecretKeySpec(key, name()));
		byte[] data = os.unmarshal_bytes();
		if (Arrays.equals(mac.doFinal(data), os.unmarshal_bytes()))
			return data;
		throw new SignatureException();
	}

	static byte[] decode(KeyTranslate keyTranslate, byte[] data) throws CodecException {
		try {
			OctetsStream os = OctetsStream.wrap(Octets.wrap(data));
			return KeyProtector.values()[os.unmarshal_byte()]
					.decode(keyTranslate.createKeyRep(os.unmarshal_bytes()).getKey(), os);
		} catch (CodecException e) {
			throw e;
		} catch (Exception e) {
			throw new CodecException(e);
		}
	}
}
