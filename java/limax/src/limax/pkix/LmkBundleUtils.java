package limax.pkix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.KeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.util.Arrays;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import limax.codec.CodecException;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.endpoint.LmkBundle;
import limax.util.Pair;
import limax.util.SecurityUtils;
import limax.util.SecurityUtils.PublicKeyAlgorithm;

public class LmkBundleUtils {
	private LmkBundleUtils() {
	}

	private static Octets passphrase2Octets(char[] passphrase) {
		byte[] key = new byte[passphrase.length];
		for (int i = 0; i < key.length; i++)
			key[i] = (byte) passphrase[i];
		return Octets.wrap(key);
	}

	private static byte[] toDERs(Certificate[] chain) throws Exception {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		for (Certificate c : chain)
			os.write(c.getEncoded());
		return os.toByteArray();
	}

	private static class LmkBundle_ extends LmkBundle {
		private LmkBundle_(Path path, char[] passphrase) throws CodecException, MarshalException, IOException {
			super(Octets.wrap(Files.readAllBytes(path)), passphrase2Octets(passphrase));
		}

		private LmkBundle_(PrivateKey privateKey, byte[] chain) {
			if (!(privateKey instanceof RSAPrivateKey))
				throw new UnsupportedOperationException("RSA only");
			if (privateKey instanceof RSAPrivateCrtKey) {
				RSAPrivateCrtKey k0 = (RSAPrivateCrtKey) privateKey;
				this.chain = Octets.wrap(chain);
				this.p = k0.getPrimeP();
				this.q = k0.getPrimeQ();
				this.exp1 = k0.getPrimeExponentP();
				this.exp2 = k0.getPrimeExponentQ();
				this.coef = k0.getCrtCoefficient();
			} else {
				RSAPrivateKey k1 = (RSAPrivateKey) privateKey;
				this.chain = Octets.wrap(chain);
				this.n = k1.getModulus();
				this.d = k1.getPrivateExponent();
			}
		}

		private Pair<PrivateKey, Certificate[]> extract() throws Exception {
			Certificate[] certs = CertificateFactory.getInstance("X.509")
					.generateCertificates(new ByteArrayInputStream(chain.array(), 0, chain.size()))
					.toArray(new Certificate[0]);
			BigInteger e = ((RSAPublicKey) certs[0].getPublicKey()).getPublicExponent();
			KeySpec keySpec;
			if (coef == null)
				keySpec = new RSAPrivateKeySpec(n, d);
			else {
				if (n == null)
					n = p.multiply(q);
				if (d == null)
					d = e.modInverse(p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE)));
				keySpec = new RSAPrivateCrtKeySpec(n, e, d, p, q, exp1, exp2, coef);
			}
			return new Pair<>(KeyFactory.getInstance("RSA").generatePrivate(keySpec), certs);
		}
	}

	static void save(Path path, byte[] privateKeyBytes, byte[] certificateChainBytes, char[] passphrase)
			throws Exception {
		Files.write(path,
				new LmkBundle_(PublicKeyAlgorithm.loadPrivateKey(new String(privateKeyBytes), passphrase),
						toDERs(SecurityUtils.sortCertificateChain(CertificateFactory.getInstance("X.509")
								.generateCertificates(new ByteArrayInputStream(certificateChainBytes))
								.toArray(new Certificate[0])))).save(passphrase2Octets(passphrase)).getBytes(),
				KeyContainer.options);
	}

	static Pair<PrivateKey, Certificate[]> extract(Path path, char[] passphrase) throws Exception {
		return new LmkBundle_(path, passphrase).extract();
	}

	public static LmkBundle createInstance(PrivateKey privateKey, Certificate[] chain) throws Exception {
		return new LmkBundle_(privateKey, toDERs(chain));
	}

	public static LmkBundle load(Path path, char[] passphrase) throws CodecException, MarshalException, IOException {
		return new LmkBundle_(path, passphrase);
	}

	public static void renew(Path path, char[] passphrase) throws Exception {
		Pair<PrivateKey, Certificate[]> pair = LmkBundleUtils.extract(path, passphrase);
		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(new KeyManager[] { new X509ExtendedKeyManager() {
			@Override
			public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
				return "none";
			}

			@Override
			public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
				return "none";
			}

			@Override
			public X509Certificate[] getCertificateChain(String alias) {
				return Arrays.stream(pair.getValue()).toArray(X509Certificate[]::new);
			}

			@Override
			public String[] getClientAliases(String keyType, Principal[] issuers) {
				return new String[] { "none" };
			}

			@Override
			public PrivateKey getPrivateKey(String alias) {
				return pair.getKey();
			}

			@Override
			public String[] getServerAliases(String keyType, Principal[] issuers) {
				return new String[] { "none" };
			}
		} }, new TrustManager[] { new X509TrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
		} }, null);
		Files.write(path,
				new LmkRequest(null, null)
						.createContext(SecurityUtils.extractOcspURI((X509Certificate) pair.getValue()[0]).getHost())
						.fetch(sslContext).save(passphrase2Octets(passphrase)).getBytes());
	}
}
