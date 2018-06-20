package limax.node.js.modules;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.EncodedKeySpec;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.RC2ParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import limax.codec.asn1.ASN1BitString;
import limax.codec.asn1.ASN1IA5String;
import limax.codec.asn1.ASN1Object;
import limax.codec.asn1.ASN1ObjectIdentifier;
import limax.codec.asn1.ASN1Sequence;
import limax.codec.asn1.DecodeBER;
import limax.node.js.Buffer;
import limax.node.js.Buffer.Data;
import limax.node.js.EventLoop;
import limax.node.js.Module;
import limax.util.Helper;
import limax.util.SecurityUtils;
import limax.util.Pair;

public final class Crypto implements Module {
	private final EventLoop eventLoop;

	public Crypto(EventLoop eventLoop) {
		this.eventLoop = eventLoop;
	}

	private ASN1Sequence decodeSpkac(Buffer spkac) throws Exception {
		return (ASN1Sequence) DecodeBER.decode(Base64.getDecoder().decode(spkac.toByteArray()));
	}

	public boolean verifySpkac(Buffer spkac) {
		try {
			ASN1Sequence seq = decodeSpkac(spkac);
			Signature sign = Signature.getInstance(((ASN1ObjectIdentifier) ((ASN1Sequence) seq.get(1)).get(0)).get());
			X509EncodedKeySpec keySpec = new X509EncodedKeySpec(((ASN1Sequence) seq.get(0)).get(0).toDER());
			sign.initVerify(SecurityUtils.getPublicKeyAlgorithm(keySpec).generatePublicKey(keySpec));
			sign.update(seq.get(0).toDER());
			byte[] data = ((ASN1BitString) seq.get(2)).getData();
			return sign.verify(data, 1, data.length - 1);
		} catch (Exception e) {
		}
		return false;
	}

	public Buffer exportChallenge(Buffer spkac) {
		try {
			ASN1Sequence seq = decodeSpkac(spkac);
			return new Buffer(((ASN1IA5String) ((ASN1Sequence) seq.get(0)).get(1)).getData());
		} catch (Exception e) {
			e.printStackTrace();
			return Buffer.EMPTY;
		}
	}

	public Buffer exportPublicKey(Buffer spkac) {
		try {
			return new Buffer(
					("-----BEGIN PUBLIC KEY-----\n"
							+ Base64.getMimeEncoder(64, new byte[] { 13 })
									.encodeToString(((ASN1Sequence) decodeSpkac(spkac).get(0)).get(0).toDER())
							+ "\n-----END PUBLIC KEY-----\n").getBytes(StandardCharsets.ISO_8859_1));
		} catch (Exception e) {
			return Buffer.EMPTY;
		}
	}

	public class CipherDecipher {
		private final Cipher cipher;

		public CipherDecipher(String algorithm, String password, boolean enc) throws Exception {
			Pair<String, Integer> pair = SecurityUtils.getCipherAlgorithm(algorithm);
			String transform = pair.getKey();
			algorithm = SecurityUtils.transform2Algorithm(transform);
			this.cipher = Cipher.getInstance(transform);
			byte[][] keyAndIV = SecurityUtils.EVP_BytesToKey(pair.getValue(), cipher.getBlockSize(),
					MessageDigest.getInstance("MD5"), null, password.getBytes(StandardCharsets.ISO_8859_1), 1);
			if (transform.contains("ECB") || transform.startsWith("RC4"))
				cipher.init(enc ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(keyAndIV[0], algorithm));
			else if (transform.startsWith("RC2"))
				cipher.init(enc ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(keyAndIV[0], algorithm),
						new RC2ParameterSpec(pair.getValue() * 8, keyAndIV[1]));
			else
				cipher.init(enc ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(keyAndIV[0], algorithm),
						new IvParameterSpec(keyAndIV[1]));
		}

		public CipherDecipher(String algorithm, Buffer key, Buffer iv, boolean enc) throws Exception {
			String transform = SecurityUtils.getCipherAlgorithm(algorithm).getKey();
			this.cipher = Cipher.getInstance(transform);
			algorithm = SecurityUtils.transform2Algorithm(transform);
			Data _key = key.toData();
			Data _iv = iv.toData();
			if (transform.contains("ECB") || transform.startsWith("RC4"))
				cipher.init(enc ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
						new SecretKeySpec(_key.buf, _key.off, _key.len, algorithm));
			else if (transform.startsWith("RC2"))
				cipher.init(enc ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
						new SecretKeySpec(_key.buf, _key.off, _key.len, algorithm),
						new RC2ParameterSpec(_key.len * 8, Arrays.copyOfRange(_iv.buf, _iv.off, _iv.off + _iv.len)));
			else
				cipher.init(enc ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
						new SecretKeySpec(_key.buf, _key.off, _key.len, algorithm),
						new IvParameterSpec(_iv.buf, _iv.off, _iv.len));
		}

		public void update(Buffer buffer, Object callback) {
			eventLoop.execute(callback, r -> update(buffer));
		}

		public void _final(Object callback) {
			eventLoop.execute(callback, r -> r.add(_final()));
		}

		public Buffer update(Buffer buffer) throws Exception {
			ByteBuffer r = ByteBuffer.allocate(cipher.getOutputSize(buffer.size()));
			cipher.update(buffer.toByteBuffer(), r);
			r.flip();
			return new Buffer(r);
		}

		public Buffer _final() throws Exception {
			ByteBuffer r = ByteBuffer.allocateDirect(cipher.getOutputSize(0));
			cipher.doFinal(ByteBuffer.allocate(0), r);
			r.flip();
			return new Buffer(r);
		}
	}

	public static class DiffieHellman {
		private final BigInteger prime;
		private BigInteger generator;
		private BigInteger privatekey;
		private BigInteger publickey;

		private void init(Buffer generator) {
			this.generator = generator == null ? BigInteger.valueOf(2) : new BigInteger(generator.toByteArray());
			this.privatekey = new BigInteger(1, Helper.makeRandValues(prime.bitLength())).mod(prime);
			this.publickey = this.generator.modPow(privatekey, prime);
		}

		public DiffieHellman(int bits, Buffer generator) {
			this.prime = BigInteger.probablePrime(bits, new SecureRandom());
			init(generator);
		}

		public DiffieHellman(Buffer prime, Buffer generator) {
			this.prime = new BigInteger(prime.toByteArray());
			init(generator);
		}

		public DiffieHellman(String group) {
			if (!group.toLowerCase().startsWith("modp"))
				throw new IllegalArgumentException();
			int g = Integer.valueOf(group.substring(4));
			if (!Helper.isDHGroupSupported(g))
				throw new IllegalArgumentException();
			this.prime = Helper.getDHGroup(g);
			init(null);
		}

		public Buffer computeSecret(Buffer other_publickey) {
			return new Buffer(new BigInteger(other_publickey.toByteArray()).modPow(privatekey, prime).toByteArray());
		}

		public Buffer generateKeys() {
			return new Buffer(publickey.toByteArray());
		}

		public Buffer getGenerator() {
			return new Buffer(generator.toByteArray());
		}

		public Buffer getPrime() {
			return new Buffer(prime.toByteArray());
		}

		public Buffer getPrivateKey() {
			return new Buffer(privatekey.toByteArray());
		}

		public Buffer getPublicKey() {
			return new Buffer(publickey.toByteArray());
		}
	}

	public class ECDH {
		private final Key privateKey;
		private final Key publicKey;

		public ECDH(String curve) throws Exception {
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
			keyPairGenerator.initialize(new ECGenParameterSpec(curve));
			KeyPair keyPair = keyPairGenerator.generateKeyPair();
			privateKey = keyPair.getPrivate();
			publicKey = keyPair.getPublic();
		}

		public Buffer computeSecret(Buffer other_publicKey) throws Exception {
			KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
			keyAgreement.init(privateKey);
			keyAgreement.doPhase(
					KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(other_publicKey.toByteArray())),
					true);
			return new Buffer(keyAgreement.generateSecret());
		}

		public Buffer generateKeys() throws Exception {
			return getPublicKey();
		}

		public Buffer getPrivateKey() {
			return new Buffer(privateKey.getEncoded());
		}

		public Buffer getPublicKey() {
			return new Buffer(publicKey.getEncoded());
		}
	}

	public class Hash {
		private final MessageDigest md;

		Hash(String algorithm) throws Exception {
			this.md = MessageDigest.getInstance(SecurityUtils.getHashAlgorithm(algorithm).getKey());
		}

		public void update(Buffer buffer, Object callback) {
			eventLoop.execute(callback, r -> update(buffer));
		}

		public void digest(Object callback) {
			eventLoop.execute(callback, r -> r.add(digest()));
		}

		public void update(Buffer buffer) {
			md.update(buffer.toByteBuffer());
		}

		public Buffer digest() {
			return new Buffer(md.digest());
		}
	}

	public class Hmac {
		private final Mac mac;

		Hmac(String algorithm, Buffer key) throws Exception {
			algorithm = SecurityUtils.getMacAlgorithm(algorithm);
			this.mac = Mac.getInstance(algorithm);
			Data data = key.toData();
			mac.init(new SecretKeySpec(data.buf, data.off, data.len, algorithm));
		}

		public void update(Buffer buffer, Object callback) {
			eventLoop.execute(callback, r -> update(buffer));
		}

		public void digest(Object callback) {
			eventLoop.execute(callback, r -> r.add(digest()));
		}

		public void update(Buffer buffer) {
			mac.update(buffer.toByteBuffer());
		}

		public Buffer digest() {
			return new Buffer(mac.doFinal());
		}
	}

	public class SignVerify {
		private final MessageDigest md;
		private final ASN1Object alg;

		SignVerify(String algorithm) throws Exception {
			int pos = algorithm.indexOf('-');
			if (pos != -1)
				algorithm = algorithm.substring(pos + 1);
			Pair<String, ASN1Object> pair = SecurityUtils.getHashAlgorithm(algorithm);
			this.md = MessageDigest.getInstance(pair.getKey());
			this.alg = pair.getValue();
		}

		public void update(Buffer buffer, Object callback) {
			eventLoop.execute(callback, r -> update(buffer));
		}

		public void update(Buffer buffer) {
			md.update(buffer.toByteBuffer());
		}

		public Buffer sign(String pem, String passphrase) throws Exception {
			EncodedKeySpec eks = (EncodedKeySpec) SecurityUtils.loadPEM(pem, passphrase.toCharArray());
			return new Buffer(SecurityUtils.getPublicKeyAlgorithm(eks).sign(eks, alg, md.digest()));
		}

		public boolean verify(String pem, String passphrase, Buffer buffer) throws Exception {
			byte[] signature = buffer.toByteArray();
			Object obj = SecurityUtils.loadPEM(pem, passphrase.toCharArray());
			return obj instanceof EncodedKeySpec
					? SecurityUtils.getPublicKeyAlgorithm(obj).verify((EncodedKeySpec) obj, alg, md.digest(), signature)
					: SecurityUtils.PublicKeyAlgorithm.verify((Certificate) obj, alg, md.digest(), signature);
		}
	}

	public Hash createHash(String algorithm) throws Exception {
		return new Hash(algorithm);
	}

	public Hmac createHmac(String algorithm, Buffer key) throws Exception {
		return new Hmac(algorithm, key);
	}

	public SignVerify createSignVerify(String algorithm) throws Exception {
		return new SignVerify(algorithm);
	}

	public CipherDecipher createCipher(String algorithm, String password) throws Exception {
		return new CipherDecipher(algorithm, password, true);
	}

	public CipherDecipher createCipheriv(String algorithm, Buffer key, Buffer iv) throws Exception {
		return new CipherDecipher(algorithm, key, iv, true);
	}

	public CipherDecipher createDecipher(String algorithm, String password) throws Exception {
		return new CipherDecipher(algorithm, password, false);
	}

	public CipherDecipher createDecipheriv(String algorithm, Buffer key, Buffer iv) throws Exception {
		return new CipherDecipher(algorithm, key, iv, false);
	}

	public DiffieHellman createDiffieHellman(int bits, Buffer generator) {
		return new DiffieHellman(bits, generator);
	}

	public DiffieHellman createDiffieHellman(Buffer prime, Buffer generator) {
		return new DiffieHellman(prime, generator);
	}

	public DiffieHellman createDiffieHellman(String group, int dummy) throws Exception {
		return new DiffieHellman(group);
	}

	public ECDH createECDH(String curve) throws Exception {
		return new ECDH(curve);
	}

	public Object[] getCiphers() {
		return SecurityUtils.getCiphers().toArray();
	}

	public Object[] getCurves() {
		return SecurityUtils.getCurves().toArray();
	}

	public Object[] getHashes() {
		return SecurityUtils.getHashes().toArray();
	}

	public void pbkdf2(String password, Buffer salt, int iterations, int bytes, String digest, Object callback) {
		eventLoop.execute(callback, r -> r.add(pbkdf2Sync(password, salt, iterations, bytes, digest)));
	}

	public Buffer pbkdf2Sync(String password, Buffer salt, int iterations, int bytes, String digest) throws Exception {
		return new Buffer(SecretKeyFactory.getInstance("PBKDF2WithHmac" + digest)
				.generateSecret(new PBEKeySpec(password.toCharArray(), salt.toByteArray(), iterations, bytes * 8))
				.getEncoded());
	}

	public Buffer privateDecrypt(String pem, String passphrase, Buffer buffer) throws Exception {
		KeySpec keySpec = (KeySpec) SecurityUtils.loadPEM(pem, passphrase.toCharArray());
		Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
		cipher.init(Cipher.DECRYPT_MODE, KeyFactory.getInstance("RSA").generatePrivate(keySpec));
		ByteBuffer r = ByteBuffer.allocate(cipher.getOutputSize(buffer.size()));
		cipher.doFinal(buffer.toByteBuffer(), r);
		r.flip();
		return new Buffer(r);
	}

	public Buffer privateEncrypt(String pem, String passphrase, Buffer buffer) throws Exception {
		KeySpec keySpec = (KeySpec) SecurityUtils.loadPEM(pem, passphrase.toCharArray());
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA").generatePrivate(keySpec));
		ByteBuffer r = ByteBuffer.allocate(cipher.getOutputSize(buffer.size()));
		cipher.doFinal(buffer.toByteBuffer(), r);
		r.flip();
		return new Buffer(r);
	}

	public Buffer publicDecrypt(String pem, String passphrase, Buffer buffer) throws Exception {
		KeySpec keySpec = (KeySpec) SecurityUtils.loadPEM(pem, passphrase.toCharArray());
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.DECRYPT_MODE, KeyFactory.getInstance("RSA").generatePublic(keySpec));
		ByteBuffer r = ByteBuffer.allocate(cipher.getOutputSize(buffer.size()));
		cipher.doFinal(buffer.toByteBuffer(), r);
		r.flip();
		return new Buffer(r);
	}

	public Buffer publicEncrypt(String pem, String passphrase, Buffer buffer) throws Exception {
		KeySpec keySpec = (KeySpec) SecurityUtils.loadPEM(pem, passphrase.toCharArray());
		Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
		cipher.init(Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA").generatePublic(keySpec));
		ByteBuffer r = ByteBuffer.allocate(cipher.getOutputSize(buffer.size()));
		cipher.doFinal(buffer.toByteBuffer(), r);
		r.flip();
		return new Buffer(r);
	}

	public void randomBytes(int size, Object callback) {
		eventLoop.execute(callback, r -> r.add(randomBytesSync(size)));
	}

	public Buffer randomBytesSync(int size) {
		return new Buffer(Helper.makeRandValues(size));
	}
}
