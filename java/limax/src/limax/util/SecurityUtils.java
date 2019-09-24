package limax.util;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertPathBuilderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAKeyPairGenerator;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.EncodedKeySpec;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import limax.codec.CodecException;
import limax.codec.asn1.ASN1ConstructedObject;
import limax.codec.asn1.ASN1Integer;
import limax.codec.asn1.ASN1Null;
import limax.codec.asn1.ASN1Object;
import limax.codec.asn1.ASN1ObjectIdentifier;
import limax.codec.asn1.ASN1OctetString;
import limax.codec.asn1.ASN1PrimitiveObject;
import limax.codec.asn1.ASN1RawData;
import limax.codec.asn1.ASN1Sequence;
import limax.codec.asn1.DecodeBER;

public final class SecurityUtils {

	public static byte[][] EVP_BytesToKey(int key_len, int iv_len, MessageDigest md, byte[] salt, byte[] data,
			int count) {
		byte[][] both = new byte[2][];
		byte[] key = new byte[key_len];
		int key_ix = 0;
		byte[] iv = new byte[iv_len];
		int iv_ix = 0;
		both[0] = key;
		both[1] = iv;
		byte[] md_buf = null;
		int nkey = key_len;
		int niv = iv_len;
		int i = 0;
		if (data == null)
			return both;
		int addmd = 0;
		for (;;) {
			md.reset();
			if (addmd++ > 0)
				md.update(md_buf);
			md.update(data);
			if (null != salt)
				md.update(salt, 0, 8);
			md_buf = md.digest();
			for (i = 1; i < count; i++) {
				md.reset();
				md.update(md_buf);
				md_buf = md.digest();
			}
			i = 0;
			if (nkey > 0) {
				for (;;) {
					if (nkey == 0)
						break;
					if (i == md_buf.length)
						break;
					key[key_ix++] = md_buf[i];
					nkey--;
					i++;
				}
			}
			if (niv > 0 && i != md_buf.length) {
				for (;;) {
					if (niv == 0)
						break;
					if (i == md_buf.length)
						break;
					iv[iv_ix++] = md_buf[i];
					niv--;
					i++;
				}
			}
			if (nkey == 0 && niv == 0)
				break;
		}
		for (i = 0; i < md_buf.length; i++)
			md_buf[i] = 0;
		return both;
	}

	private final static ASN1Integer pkcs8Version = new ASN1Integer(BigInteger.ZERO);
	private final static ASN1ObjectIdentifier OID_RSA_RSA = new ASN1ObjectIdentifier("1.2.840.113549.1.1.1");
	private final static ASN1ObjectIdentifier OID_X957_DSA = new ASN1ObjectIdentifier("1.2.840.10040.4.1");
	private final static ASN1ObjectIdentifier OID_ECC_PUBLIC_KEY = new ASN1ObjectIdentifier("1.2.840.10045.2.1");
	private final static ASN1Sequence ALG_HASH_MD2 = new ASN1Sequence(new ASN1ObjectIdentifier("1.2.840.113549.2.2"),
			new ASN1Null());
	private final static ASN1Sequence ALG_HASH_MD5 = new ASN1Sequence(new ASN1ObjectIdentifier("1.2.840.113549.2.5"),
			new ASN1Null());
	private final static ASN1Sequence ALG_HASH_SHA1 = new ASN1Sequence(new ASN1ObjectIdentifier("1.3.14.3.2.26"),
			new ASN1Null());
	private final static ASN1Sequence ALG_HASH_SHA224 = new ASN1Sequence(
			new ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.4"), new ASN1Null());
	private final static ASN1Sequence ALG_HASH_SHA256 = new ASN1Sequence(
			new ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.1"), new ASN1Null());
	private final static ASN1Sequence ALG_HASH_SHA384 = new ASN1Sequence(
			new ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.2"), new ASN1Null());
	private final static ASN1Sequence ALG_HASH_SHA512 = new ASN1Sequence(
			new ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.3"), new ASN1Null());

	private final static Map<String, Pair<String, ASN1Object>> mapHash = new HashMap<>();
	private final static Map<String, String> mapMac = new HashMap<>();
	private final static Map<String, Pair<String, Integer>> mapCipher = new HashMap<>();
	static {
		mapHash.put("MD2", new Pair<>("MD2", ALG_HASH_MD2));
		mapHash.put("MD5", new Pair<>("MD5", ALG_HASH_MD5));
		mapHash.put("SHA-1", new Pair<>("SHA-1", ALG_HASH_SHA1));
		mapHash.put("SHA-224", new Pair<>("SHA-224", ALG_HASH_SHA224));
		mapHash.put("SHA-256", new Pair<>("SHA-256", ALG_HASH_SHA256));
		mapHash.put("SHA-384", new Pair<>("SHA-384", ALG_HASH_SHA384));
		mapHash.put("SHA-512", new Pair<>("SHA-512", ALG_HASH_SHA512));
		mapHash.put("SHA", mapHash.get("SHA-1"));
		mapHash.put("SHA1", mapHash.get("SHA-1"));
		mapHash.put("SHA224", mapHash.get("SHA-224"));
		mapHash.put("SHA256", mapHash.get("SHA-256"));
		mapHash.put("SHA384", mapHash.get("SHA-384"));
		mapHash.put("SHA512", mapHash.get("SHA-512"));

		mapMac.put("MD5", "HmacMD5");
		mapMac.put("HMACMD5", "HmacMD5");
		mapMac.put("SHA", "HmacSHA1");
		mapMac.put("SHA1", "HmacSHA1");
		mapMac.put("SHA-1", "HmacSHA1");
		mapMac.put("HMACSHA1", "HmacSHA1");
		mapMac.put("HMACSHA-1", "HmacSHA1");
		mapMac.put("SHA224", "HmacSHA224");
		mapMac.put("SHA-224", "HmacSHA224");
		mapMac.put("HMACSHA224", "HmacSHA224");
		mapMac.put("HMACSHA-224", "HmacSHA224");
		mapMac.put("SHA256", "HmacSHA256");
		mapMac.put("SHA-256", "HmacSHA256");
		mapMac.put("HMACSHA256", "HmacSHA256");
		mapMac.put("HMACSHA-256", "HmacSHA256");
		mapMac.put("SHA384", "HmacSHA384");
		mapMac.put("SHA-384", "HmacSHA384");
		mapMac.put("HMACSHA384", "HmacSHA384");
		mapMac.put("HMACSHA-384", "HmacSHA384");
		mapMac.put("SHA512", "HmacSHA512");
		mapMac.put("SHA-512", "HmacSHA512");
		mapMac.put("HMACSHA512", "HmacSHA512");
		mapMac.put("HMACSHA-512", "HmacSHA512");

		mapCipher.put("aes-128-cbc", new Pair<>("AES/CBC/PKCS5Padding", 16));
		mapCipher.put("aes-128-cfb", new Pair<>("AES/CFB/NoPadding", 16));
		mapCipher.put("aes-128-cfb8", new Pair<>("AES/CFB8/NoPadding", 16));
		mapCipher.put("aes-128-ctr", new Pair<>("AES/CTR/NoPadding", 16));
		mapCipher.put("aes-128-ecb", new Pair<>("AES/ECB/PKCS5Padding", 16));
		mapCipher.put("aes-128-ofb", new Pair<>("AES/OFB/NoPadding", 16));
		mapCipher.put("aes128", mapCipher.get("aes-128-cbc"));

		mapCipher.put("bf-cbc", new Pair<>("BlowFish/CBC/PKCS5Padding", 16));
		mapCipher.put("bf-cfb", new Pair<>("BlowFish/CFB/NoPadding", 16));
		mapCipher.put("bf-ecb", new Pair<>("BlowFish/ECB/PKCS5Padding", 16));
		mapCipher.put("bf-ofb", new Pair<>("BlowFish/OFB/NoPadding", 16));
		mapCipher.put("bf", mapCipher.get("bf-cbc"));
		mapCipher.put("blowfish", mapCipher.get("bf-cbc"));

		mapCipher.put("des-cbc", new Pair<>("DES/CBC/PKCS5Padding", 8));
		mapCipher.put("des-cfb", new Pair<>("DES/CFB/NoPadding", 8));
		mapCipher.put("des-cfb8", new Pair<>("DES/CFB8/NoPadding", 8));
		mapCipher.put("des-ecb", new Pair<>("DES/ECB/PKCS5Padding", 8));
		mapCipher.put("des-ofb", new Pair<>("DES/OFB/NoPadding", 8));
		mapCipher.put("des", mapCipher.get("des-cbc"));

		mapCipher.put("des-ede3-cbc", new Pair<>("DESede/CBC/PKCS5Padding", 24));
		mapCipher.put("des-ede3-cfb", new Pair<>("DESede/CFB/NoPadding", 24));
		mapCipher.put("des-ede3-cfb8", new Pair<>("DESede/CFB8/NoPadding", 24));
		mapCipher.put("des-ede3-ofb", new Pair<>("DESede/OFB/NoPadding", 24));
		mapCipher.put("des3", mapCipher.get("des-ede3-cbc"));

		mapCipher.put("rc2-40-cbc", new Pair<>("RC2/CBC/PKCS5Padding", 5));
		mapCipher.put("rc2-64-cbc", new Pair<>("RC2/CBC/PKCS5Padding", 8));
		mapCipher.put("rc2-cbc", new Pair<>("RC2/CBC/PKCS5Padding", 16));
		mapCipher.put("rc2-cfb", new Pair<>("RC2/CFB/NoPadding", 16));
		mapCipher.put("rc2-ecb", new Pair<>("RC2/ECB/PKCS5Padding", 16));
		mapCipher.put("rc2-ofb", new Pair<>("RC2/OFB/NoPadding", 16));
		mapCipher.put("rc2", mapCipher.get("rc2-cbc"));

		mapCipher.put("rc4-40", new Pair<>("RC4", 5));
		mapCipher.put("rc4", new Pair<>("RC4", 16));
	}

	private final static ASN1ObjectIdentifier PBES2 = new ASN1ObjectIdentifier("1.2.840.113549.1.5.13");
	private final static ASN1ObjectIdentifier PBKDF2 = new ASN1ObjectIdentifier("1.2.840.113549.1.5.12");
	private final static ASN1ObjectIdentifier DESEDE3CBCPAD = new ASN1ObjectIdentifier("1.2.840.113549.3.7");

	public static Pair<String, ASN1Object> getHashAlgorithm(String name) {
		return mapHash.get(name.toUpperCase());
	}

	public static String getMacAlgorithm(String name) {
		return mapMac.get(name.toUpperCase());
	}

	public static Pair<String, Integer> getCipherAlgorithm(String name) {
		return mapCipher.get(name.toLowerCase());
	}

	public static Collection<String> getCiphers() {
		return Collections.unmodifiableCollection(mapCipher.keySet());
	}

	private static Collection<String> curves;

	public static Collection<String> getCurves() {
		if (curves == null) {
			try {
				KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
				curves = Collections.unmodifiableCollection(Arrays.stream(((String) keyPairGenerator.getProvider()
						.getService("AlgorithmParameters", "EC").getAttribute("SupportedCurves")).replace('[', ',')
								.replace(']', ',').replace('|', ',').split(","))
						.filter(curve -> {
							try {
								if (!curve.isEmpty()) {
									keyPairGenerator.initialize(new ECGenParameterSpec(curve));
									keyPairGenerator.generateKeyPair();
									return true;
								}
							} catch (Exception e) {
							}
							return false;
						}).collect(Collectors.toList()));
			} catch (Exception e) {
				curves = Collections.emptyList();
			}
		}
		return curves;
	}

	public static Collection<String> getHashes() {
		return Collections.unmodifiableCollection(mapHash.keySet());
	}

	private final static ASN1ObjectIdentifier SHA256withDSA = new ASN1ObjectIdentifier("2.16.840.1.101.3.4.3.2");
	private final static ASN1ObjectIdentifier SHA256withRSA = new ASN1ObjectIdentifier("1.2.840.113549.1.1.11");
	private final static ASN1ObjectIdentifier SHA384withRSA = new ASN1ObjectIdentifier("1.2.840.113549.1.1.12");
	private final static ASN1ObjectIdentifier SHA512withRSA = new ASN1ObjectIdentifier("1.2.840.113549.1.1.13");
	private final static ASN1ObjectIdentifier SHA256withECDSA = new ASN1ObjectIdentifier("1.2.840.10045.4.3.2");
	private final static ASN1ObjectIdentifier SHA384withECDSA = new ASN1ObjectIdentifier("1.2.840.10045.4.3.3");
	private final static ASN1ObjectIdentifier SHA512withECDSA = new ASN1ObjectIdentifier("1.2.840.10045.4.3.4");

	private final static Set<String> SIGN256 = new HashSet<>(
			Arrays.asList(SHA256withDSA.get(), SHA256withRSA.get(), SHA256withECDSA.get()));
	private final static Set<String> SIGN384 = new HashSet<>(Arrays.asList(SHA384withRSA.get(), SHA384withECDSA.get()));
	private final static Set<String> SIGN512 = new HashSet<>(Arrays.asList(SHA512withRSA.get(), SHA512withECDSA.get()));

	public static enum PublicKeyAlgorithm {
		RSA {
			@Override
			public byte[] sign(KeySpec keySpec, ASN1Object hash, byte[] digest) throws Exception {
				Signature sign = Signature.getInstance("NONEWithRSA");
				sign.initSign(KeyFactory.getInstance("RSA").generatePrivate(keySpec));
				sign.update(new ASN1Sequence(hash, new ASN1OctetString(digest)).toDER());
				return sign.sign();
			}

			@Override
			public boolean verify(PublicKey publicKey, ASN1Object hash, byte[] digest, byte[] signature)
					throws Exception {
				Signature sign = Signature.getInstance("NONEWithRSA");
				sign.initVerify(publicKey);
				sign.update(new ASN1Sequence(hash, new ASN1OctetString(digest)).toDER());
				return sign.verify(signature);
			}

			@Override
			public ASN1ObjectIdentifier getSignatureAlgorithm(int bits) {
				switch (bits) {
				case 384:
					return SHA384withRSA;
				case 512:
					return SHA512withRSA;
				default:
					return SHA256withRSA;
				}
			}

			@Override
			public ASN1Object createAlgorithmIdentifier(ASN1ObjectIdentifier signature) {
				return new ASN1Sequence(signature, new ASN1Null());
			}

			@Override
			public KeyPair reKey(PublicKey publicKey) throws GeneralSecurityException {
				KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
				keyPairGenerator.initialize(((RSAPublicKey) publicKey).getModulus().bitLength());
				return keyPairGenerator.generateKeyPair();
			}
		},
		DSA {
			@Override
			public byte[] sign(KeySpec keySpec, ASN1Object hash, byte[] digest) throws Exception {
				Signature sign = Signature.getInstance("NONEWithDSA");
				sign.initSign(KeyFactory.getInstance("DSA").generatePrivate(keySpec));
				sign.update(Arrays.copyOf(digest, 20));
				return sign.sign();
			}

			@Override
			public boolean verify(PublicKey publicKey, ASN1Object hash, byte[] digest, byte[] signature)
					throws Exception {
				Signature sign = Signature.getInstance("NONEWithDSA");
				sign.initVerify(publicKey);
				sign.update(Arrays.copyOf(digest, 20));
				return sign.verify(signature);
			}

			@Override
			public ASN1ObjectIdentifier getSignatureAlgorithm(int bits) {
				return SHA256withDSA;
			}

			@Override
			public KeyPair reKey(PublicKey publicKey) throws GeneralSecurityException {
				KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DSA");
				((DSAKeyPairGenerator) keyPairGenerator).initialize(((DSAPublicKey) publicKey).getParams(), null);
				return keyPairGenerator.generateKeyPair();
			}
		},
		EC {
			@Override
			public byte[] sign(KeySpec keySpec, ASN1Object hash, byte[] digest) throws Exception {
				Signature sign = Signature.getInstance("NONEWithECDSA");
				sign.initSign(KeyFactory.getInstance("EC").generatePrivate(keySpec));
				sign.update(digest);
				return sign.sign();
			}

			@Override
			public boolean verify(PublicKey publicKey, ASN1Object hash, byte[] digest, byte[] signature)
					throws Exception {
				Signature sign = Signature.getInstance("NONEWithECDSA");
				sign.initVerify(publicKey);
				sign.update(digest);
				return sign.verify(signature);
			}

			@Override
			public ASN1ObjectIdentifier getSignatureAlgorithm(int bits) {
				switch (bits) {
				case 384:
					return SHA384withECDSA;
				case 512:
					return SHA512withECDSA;
				default:
					return SHA256withECDSA;
				}
			}

			@Override
			public KeyPair reKey(PublicKey publicKey) throws GeneralSecurityException {
				KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
				keyPairGenerator.initialize(((ECPublicKey) publicKey).getParams());
				return keyPairGenerator.generateKeyPair();
			}
		};

		public abstract ASN1ObjectIdentifier getSignatureAlgorithm(int bits);

		public abstract KeyPair reKey(PublicKey publicKey) throws GeneralSecurityException;

		public ASN1Object createAlgorithmIdentifier(ASN1ObjectIdentifier signature) {
			return new ASN1Sequence(signature);
		}

		public static int getSignatureSize(String oid) throws NoSuchAlgorithmException {
			if (SIGN256.contains(oid))
				return 256;
			if (SIGN384.contains(oid))
				return 384;
			if (SIGN512.contains(oid))
				return 512;
			throw new NoSuchAlgorithmException("Unsupported signature algorithm " + oid);
		}

		public static PrivateKey loadPrivateKey(PKCS8EncodedKeySpec pkcs8) throws Exception {
			return KeyFactory.getInstance(valueOf(pkcs8).toString()).generatePrivate(pkcs8);
		}

		public static PrivateKey loadPrivateKey(String pemEncodedPrivateKey, char[] passphrase) throws Exception {
			return loadPrivateKey((PKCS8EncodedKeySpec) loadPEM(pemEncodedPrivateKey, passphrase));
		}

		public static PublicKey loadPublicKey(X509EncodedKeySpec x509) throws Exception {
			return KeyFactory.getInstance(valueOf(x509).toString()).generatePublic(x509);
		}

		public static PublicKey loadPublicKey(String pemEncodedPublicKey) throws Exception {
			return loadPublicKey((X509EncodedKeySpec) loadPEM(pemEncodedPublicKey, null));
		}

		private static PublicKeyAlgorithm valueOf(EncodedKeySpec encodedKeySpec) throws NoSuchAlgorithmException {
			try {
				ASN1Sequence seq = (ASN1Sequence) DecodeBER.decode(encodedKeySpec.getEncoded());
				ASN1Object ident = ((ASN1Sequence) seq.get(seq.size() == 2 ? 0 : 1)).get(0);
				if (ident.equals(OID_RSA_RSA))
					return RSA;
				if (ident.equals(OID_X957_DSA))
					return DSA;
				if (ident.equals(OID_ECC_PUBLIC_KEY))
					return EC;
			} catch (Exception e) {
			}
			throw new NoSuchAlgorithmException();
		}

		public static PublicKeyAlgorithm valueOf(PrivateKey privateKey) throws NoSuchAlgorithmException {
			if (privateKey instanceof RSAPrivateKey)
				return RSA;
			if (privateKey instanceof DSAPrivateKey)
				return DSA;
			if (privateKey instanceof ECPrivateKey)
				return EC;
			String algorithm = privateKey.getAlgorithm().toUpperCase();
			switch (algorithm) {
			case "RSA":
			case "DSA":
			case "EC":
				return PublicKeyAlgorithm.valueOf(algorithm);
			}
			throw new NoSuchAlgorithmException();
		}

		public static PublicKeyAlgorithm valueOf(PublicKey publicKey) throws NoSuchAlgorithmException {
			if (publicKey instanceof RSAPublicKey)
				return RSA;
			if (publicKey instanceof DSAPublicKey)
				return DSA;
			if (publicKey instanceof ECPublicKey)
				return EC;
			throw new NoSuchAlgorithmException();
		}

		public abstract byte[] sign(KeySpec keySpec, ASN1Object hash, byte[] digest) throws Exception;

		public abstract boolean verify(PublicKey publicKey, ASN1Object hash, byte[] digest, byte[] signature)
				throws Exception;

		public PublicKey generatePublicKey(KeySpec keySpec) throws Exception {
			return KeyFactory.getInstance(toString()).generatePublic(keySpec);
		}

		public boolean verify(KeySpec keySpec, ASN1Object hash, byte[] digest, byte[] signature) throws Exception {
			return verify(generatePublicKey(keySpec), hash, digest, signature);
		}

		public static boolean verify(Certificate cert, ASN1Object hash, byte[] digest, byte[] signature)
				throws Exception {
			PublicKey publicKey = cert.getPublicKey();
			return getPublicKeyAlgorithm(publicKey).verify(publicKey, hash, digest, signature);
		}
	}

	public static PublicKeyAlgorithm getPublicKeyAlgorithm(Object obj) throws Exception {
		if (obj instanceof RSAPublicKey)
			return PublicKeyAlgorithm.RSA;
		if (obj instanceof DSAPublicKey)
			return PublicKeyAlgorithm.DSA;
		if (obj instanceof ECPublicKey)
			return PublicKeyAlgorithm.EC;
		if (obj instanceof EncodedKeySpec) {
			ASN1Sequence seq = (ASN1Sequence) DecodeBER.decode(((EncodedKeySpec) obj).getEncoded());
			ASN1Object tmp = seq.get(0);
			seq = (ASN1Sequence) (tmp instanceof ASN1Sequence ? tmp : seq.get(1));
			for (int i = 0; i < seq.size(); i++) {
				tmp = seq.get(i);
				if (OID_RSA_RSA.equals(tmp))
					return PublicKeyAlgorithm.RSA;
				if (OID_X957_DSA.equals(tmp))
					return PublicKeyAlgorithm.DSA;
				if (OID_ECC_PUBLIC_KEY.equals(tmp))
					return PublicKeyAlgorithm.EC;
			}
		}
		throw new NoSuchAlgorithmException();
	}

	public static String transform2Algorithm(String transform) {
		int pos = transform.indexOf('/');
		return pos == -1 ? transform : transform.substring(0, pos);
	}

	public static Object loadPEM(String pem, char[] passphrase) throws Exception {
		String[] lines = pem.split("[\r\n]");
		String title = lines[0].toUpperCase();
		int startline = 1;
		int endline = lines.length - 1;
		Cipher cipher = null;
		if (lines[startline].indexOf(':') != -1) {
			for (String head; !(head = lines[startline++]).isEmpty();) {
				int pos = head.indexOf(':');
				if (cipher == null && head.substring(0, pos).equalsIgnoreCase("DEK-INFO")) {
					String[] algAndIV = head.substring(pos + 1).split(",", 2);
					Pair<String, Integer> pair = getCipherAlgorithm(algAndIV[0].trim());
					byte[] iv = Helper.fromHexString(algAndIV[1].trim());
					byte[] data = new byte[passphrase.length];
					for (int i = 0; i < data.length; i++)
						data[i] = (byte) passphrase[i];
					cipher = Cipher.getInstance(pair.getKey());
					cipher.init(Cipher.DECRYPT_MODE,
							new SecretKeySpec(EVP_BytesToKey(pair.getValue(), iv.length,
									MessageDigest.getInstance("MD5"), iv, data, 1)[0],
									transform2Algorithm(pair.getKey())),
							new IvParameterSpec(iv));
					for (int i = 0; i < data.length; i++)
						data[i] = 0;
				}
			}
		}
		StringBuilder sb = new StringBuilder();
		for (int i = startline; i < endline; i++)
			sb.append(lines[i]);
		byte[] data = Base64.getDecoder().decode(sb.toString());
		if (cipher != null)
			data = cipher.doFinal(data);
		if (title.contains("PUBLIC"))
			return new X509EncodedKeySpec(data);
		if (title.contains("BEGIN CERTIFICATE"))
			return CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(data));
		if (title.contains("BEGIN PKCS7"))
			return CertificateFactory.getInstance("X.509").generateCertPath(new ByteArrayInputStream(data));
		if (title.contains("BEGIN X509 CRL"))
			return CertificateFactory.getInstance("X.509").generateCRL(new ByteArrayInputStream(data));
		if (title.contains("BEGIN PUBLIC KEY"))
			return new X509EncodedKeySpec(data);
		if (title.contains("PRIVATE")) {
			ASN1Sequence seq = (ASN1Sequence) DecodeBER.decode(data);
			if (title.contains("RSA")) {
				return new PKCS8EncodedKeySpec(new ASN1Sequence(pkcs8Version,
						new ASN1Sequence(OID_RSA_RSA, new ASN1Null()), new ASN1OctetString(seq.toDER())).toDER());
			} else if (title.contains("DSA")) {
				return new PKCS8EncodedKeySpec(new ASN1Sequence(pkcs8Version,
						new ASN1Sequence(OID_X957_DSA, new ASN1Sequence(seq.get(1), seq.get(2), seq.get(3))),
						new ASN1OctetString(seq.get(5).toDER())).toDER());
			} else if (title.contains("EC")) {
				ASN1Object rfc5915version = seq.get(0);
				ASN1Object rfc5915privateKey = seq.get(1);
				ASN1Object rfc5915publicKey = seq.get(3);
				ASN1Object pkcs8privateKey = new ASN1OctetString(
						new ASN1Sequence(rfc5915version, rfc5915privateKey, rfc5915publicKey).toDER());
				ASN1Object curveName = ((ASN1ConstructedObject) seq.get(2)).get(0);
				return new PKCS8EncodedKeySpec(
						new ASN1Sequence(pkcs8Version, new ASN1Sequence(OID_ECC_PUBLIC_KEY, curveName), pkcs8privateKey)
								.toDER());
			} else if (title.contains("BEGIN PRIVATE KEY")) {
				return new PKCS8EncodedKeySpec(data);
			} else if (title.contains("BEGIN ENCRYPTED PRIVATE KEY")) {
				ASN1Sequence pkcs5 = (ASN1Sequence) seq.get(0);
				if (pkcs5.get(0).equals(PBES2)) {
					byte[] encrypted = ((ASN1OctetString) seq.get(1)).get();
					ASN1Sequence pbes2Param = (ASN1Sequence) pkcs5.get(1);
					ASN1Sequence keyDerivationFunc = (ASN1Sequence) pbes2Param.get(0);
					ASN1Sequence encryptionScheme = (ASN1Sequence) pbes2Param.get(1);
					if (keyDerivationFunc.get(0).equals(PBKDF2)) {
						SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(PBKDF2.get());
						ASN1Sequence pbkdf2Param = (ASN1Sequence) keyDerivationFunc.get(1);
						byte[] salt = ((ASN1OctetString) pbkdf2Param.get(0)).get();
						int iteration = ((ASN1Integer) pbkdf2Param.get(1)).get().intValue();
						ASN1ObjectIdentifier alg = (ASN1ObjectIdentifier) encryptionScheme.get(0);
						if (alg.equals(DESEDE3CBCPAD)) {
							cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding");
							SecretKey secretKey = secretKeyFactory
									.generateSecret(new PBEKeySpec(passphrase, salt, iteration, 192));
							IvParameterSpec iv = new IvParameterSpec(((ASN1OctetString) encryptionScheme.get(1)).get());
							cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey.getEncoded(), "DESede"), iv);
							return new PKCS8EncodedKeySpec(cipher.doFinal(encrypted));
						}
					}
				} else {
					EncryptedPrivateKeyInfo info = new EncryptedPrivateKeyInfo(data);
					return info.getKeySpec(
							SecretKeyFactory.getInstance(info.getAlgName()).generateSecret(new PBEKeySpec(passphrase)));
				}
			}
		}
		throw new UnsupportedEncodingException("Unsupported " + title);
	}

	public static boolean isSignedBy(Certificate candidate, Certificate issuer) {
		try {
			X509Certificate _candidate = (X509Certificate) candidate;
			X509Certificate _issuer = (X509Certificate) issuer;
			if (!_candidate.getIssuerX500Principal().equals(_issuer.getSubjectX500Principal()))
				return false;
			candidate.verify(issuer.getPublicKey());
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static Certificate[] sortCertificateChain(Certificate[] certs) throws CertPathBuilderException {
		Set<Certificate> set = Collections.newSetFromMap(new IdentityHashMap<>());
		Stack<Certificate> stack = new Stack<>();
		for (Certificate cert : certs) {
			if (SecurityUtils.isSignedBy(cert, cert))
				stack.push(cert);
			else
				set.add(cert);
		}
		if (stack.isEmpty())
			throw new CertPathBuilderException("rootCA absent");
		while (!set.isEmpty()) {
			boolean found = false;
			for (Iterator<Certificate> it = set.iterator(); it.hasNext();) {
				Certificate candidate = it.next();
				if (isSignedBy(candidate, stack.peek())) {
					stack.push(candidate);
					it.remove();
					found = true;
				}
			}
			if (!found)
				throw new CertPathBuilderException("incomplete certificate chain");
		}
		Collections.reverse(stack);
		return stack.toArray(new Certificate[0]);
	}

	public static URI extractOcspURI(X509Certificate cert) throws CodecException {
		ASN1Sequence accessDescriptions = (ASN1Sequence) DecodeBER
				.decode(((ASN1OctetString) DecodeBER.decode(cert.getExtensionValue("1.3.6.1.5.5.7.1.1"))).get());
		ASN1Sequence accessDescription = (ASN1Sequence) accessDescriptions.get(0);
		ASN1PrimitiveObject accessLocation = (ASN1PrimitiveObject) accessDescription.get(1);
		return URI.create(new String(accessLocation.getData()));
	}

	private final static Map<Path, Pair<char[], Provider>> mapPKCS11Providers = new HashMap<>();

	public synchronized static Pair<char[], KeyStore> loadPKCS11KeyStore(Path path, Function<String, char[]> cb)
			throws Exception {
		path = path.toAbsolutePath().normalize();
		Pair<char[], Provider> pair = mapPKCS11Providers.get(path);
		if (pair == null) {
			Provider provider = Security.getProvider("SunPKCS11");
			if (provider == null)
				provider = (Provider) Class.forName("sun.security.pkcs11.SunPKCS11").getConstructor(String.class)
						.newInstance(path.toString());
			else
				Provider.class.getMethod("configure", String.class).invoke(provider, path.toString());
			Security.addProvider(provider);
			mapPKCS11Providers.put(path, pair = new Pair<>(cb.apply("PKCS11 [" + path + "] PIN:"), provider));
		}
		KeyStore keyStore = KeyStore.getInstance("PKCS11", pair.getValue());
		keyStore.load(null, pair.getKey());
		return new Pair<>(pair.getKey(), keyStore);
	}

	public static String encodePEM(String title, byte[] data) {
		StringBuilder sb = new StringBuilder("-----BEGIN " + title + "-----\n")
				.append(Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(data));
		if (sb.charAt(sb.length() - 1) != '\n')
			sb.append('\n');
		return sb.append("-----END " + title + "-----\n").toString();
	}

	private final static ASN1ObjectIdentifier pbealgo = new ASN1ObjectIdentifier("1.2.840.113549.1.12.1.3");

	public static String assemblePKCS8(PrivateKey privateKey, char[] passphrase) throws Exception {
		Cipher cipher = Cipher.getInstance(pbealgo.get());
		AlgorithmParameters params = AlgorithmParameters.getInstance(pbealgo.get());
		params.init(new PBEParameterSpec(Helper.makeRandValues(16), 2048));
		cipher.init(Cipher.ENCRYPT_MODE,
				SecretKeyFactory.getInstance(pbealgo.get()).generateSecret(new PBEKeySpec(passphrase)), params);
		return encodePEM("ENCRYPTED PRIVATE KEY",
				new ASN1Sequence(new ASN1Sequence(pbealgo, new ASN1RawData(params.getEncoded())),
						new ASN1OctetString(cipher.doFinal(privateKey.getEncoded()))).toDER());
	}

	public static String assembleX509PublicKey(PublicKey publicKey) throws Exception {
		return encodePEM("PUBLIC KEY", publicKey.getEncoded());
	}

	public static String assemblePKCS7(Certificate[] certs) throws Exception {
		return encodePEM("PKCS7",
				CertificateFactory.getInstance("X.509").generateCertPath(Arrays.asList(certs)).getEncoded("PKCS7"));
	}
}
