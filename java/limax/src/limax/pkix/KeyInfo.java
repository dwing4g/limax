package limax.pkix;

import java.net.Socket;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.function.Function;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;

public class KeyInfo {
	private final KeyContainer keyContainer;
	private final KeyUpdater keyUpdater;
	private final String alias;
	private final Path path;
	private final char[] passphrase;
	private final KeyStore keyStore;

	KeyInfo(KeyContainer keyContainer, KeyUpdater keyUpdater, String alias, Path path, char[] passphrase,
			PrivateKey privateKey, Certificate[] chain) throws Exception {
		this.keyContainer = keyContainer;
		this.keyUpdater = keyUpdater;
		this.alias = alias;
		this.path = path;
		this.passphrase = passphrase;
		this.keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, null);
		KeyContainer.setKeyEntry(keyStore, alias, privateKey, passphrase, chain);
	}

	KeyInfo(KeyContainer keyContainer, KeyUpdater keyUpdater, String alias, Path path, char[] passphrase,
			KeyStore keyStore) throws UnrecoverableKeyException, Exception {
		this.keyContainer = keyContainer;
		this.keyUpdater = keyUpdater;
		this.alias = alias;
		this.path = path;
		this.passphrase = passphrase;
		this.keyStore = keyStore;
	}

	public PrivateKey getPrivateKey() throws Exception {
		return (PrivateKey) keyStore.getKey(alias, passphrase);
	}

	public Certificate[] getCertificateChain() throws KeyStoreException {
		return keyStore.getCertificateChain(alias);
	}

	public SSLContext createSSLContext(TrustManager trustManager, boolean installRevocationChecker,
			X509CertSelector selector) throws Exception {
		javax.net.ssl.TrustManager[] trustManagers;
		if (trustManager == null) {
			trustManagers = null;
		} else {
			TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
			trustManagerFactory.init(new CertPathTrustManagerParameters(
					trustManager.createPKIXBuilderParameters(selector, installRevocationChecker)));
			trustManagers = trustManagerFactory.getTrustManagers();
		}
		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(new KeyManager[] { new X509ExtendedKeyManager() {
			@Override
			public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
				return alias;
			}

			@Override
			public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
				return alias;
			}

			@Override
			public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
				return alias;
			}

			@Override
			public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
				return alias;
			}

			@Override
			public X509Certificate[] getCertificateChain(String alias) {
				try {
					Certificate[] chain = KeyInfo.this.getCertificateChain();
					return Arrays.stream(chain).limit(chain.length - 1).toArray(X509Certificate[]::new);
				} catch (KeyStoreException e) {
					return null;
				}
			}

			@Override
			public String[] getClientAliases(String keyType, Principal[] issuers) {
				return new String[] { alias };
			}

			@Override
			public PrivateKey getPrivateKey(String alias) {
				try {
					return KeyInfo.this.getPrivateKey();
				} catch (Exception e) {
					return null;
				}
			}

			@Override
			public String[] getServerAliases(String keyType, Principal[] issuers) {
				return new String[] { alias };
			}
		} }, trustManagers, null);
		return sslContext;
	}

	public void setKeyEntry(PrivateKey privateKey, Certificate[] chain) throws Exception {
		keyUpdater.save(privateKey, chain, passphrase, (privateKeyBytes, certificateChainBytes) -> {
			KeyContainer.setKeyEntry(keyStore, alias, privateKey, passphrase, chain);
			keyContainer.sync(path, alias, keyStore, passphrase, privateKeyBytes, certificateChainBytes);
		});
	}

	public static KeyInfo load(URI location, Function<String, char[]> passphraseCallback)
			throws UnrecoverableKeyException, Exception {
		String describe = location.getSchemeSpecificPart();
		int pos = describe.indexOf('@');
		String alias = pos == -1 ? null : describe.substring(0, pos);
		Path path = Paths.get(new URI("file", describe.substring(pos + 1), null));
		return KeyContainer.valueOf(location).load(new KeyUpdater(location), alias, path, passphraseCallback);
	}

	public static KeyInfo save(URI location, PrivateKey privateKey, Certificate[] chain,
			Function<String, char[]> passphraseCallback) throws Exception {
		String describe = location.getSchemeSpecificPart();
		int pos = describe.indexOf('@');
		String alias = pos == -1 ? null : describe.substring(0, pos);
		Path path = Paths.get(new URI("file", describe.substring(pos + 1), null));
		return KeyContainer.valueOf(location).save(new KeyUpdater(location), alias, path, privateKey, chain,
				passphraseCallback);
	}
}