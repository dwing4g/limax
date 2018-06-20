package limax.node.js.modules.tls;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

class TLSKeyManager extends X509ExtendedKeyManager {
	private final VirtualServerKeyManager vsKeyManager;
	private final X509ExtendedKeyManager pkixKeyManager;

	private TLSKeyManager(KeyManager[] keyManagers, VirtualServerKeyManager dnsKeyManager) throws Exception {
		this.vsKeyManager = dnsKeyManager;
		for (KeyManager keyManager : keyManagers)
			if (keyManager instanceof X509ExtendedKeyManager) {
				this.pkixKeyManager = (X509ExtendedKeyManager) keyManager;
				return;
			}
		throw new Exception("no X509ExtendedKeyManager");
	}

	static KeyManager[] create(KeyManager[] keyManagers, VirtualServerKeyManager dnsKeyManager) throws Exception {
		return new KeyManager[] { new TLSKeyManager(keyManagers, dnsKeyManager) };
	}

	@Override
	public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
		return pkixKeyManager.chooseEngineClientAlias(keyType, issuers, engine);
	}

	@Override
	public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
		String alias = null;
		if (vsKeyManager != null)
			alias = engine.getSSLParameters().getServerNames().stream()
					.map(sn -> vsKeyManager.choose(keyType, new String(sn.getEncoded(), StandardCharsets.US_ASCII)))
					.filter(Objects::nonNull).findFirst().orElse(null);
		if (alias == null)
			alias = pkixKeyManager.chooseEngineServerAlias(keyType, issuers, engine);
		return alias;
	}

	@Override
	public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
		throw new UnsupportedOperationException();
	}

	@Override
	public X509Certificate[] getCertificateChain(String alias) {
		X509Certificate[] r = pkixKeyManager.getCertificateChain(alias);
		return r == null && vsKeyManager != null ? vsKeyManager.getCertificateChain(alias) : r;

	}

	@Override
	public String[] getClientAliases(String keyType, Principal[] issuers) {
		return pkixKeyManager.getClientAliases(keyType, issuers);
	}

	@Override
	public PrivateKey getPrivateKey(String alias) {
		PrivateKey r = pkixKeyManager.getPrivateKey(alias);
		return r == null && vsKeyManager != null ? vsKeyManager.getPrivateKey(alias) : r;
	}

	@Override
	public String[] getServerAliases(String keyType, Principal[] issuers) {
		return pkixKeyManager.getServerAliases(keyType, issuers);
	}
}