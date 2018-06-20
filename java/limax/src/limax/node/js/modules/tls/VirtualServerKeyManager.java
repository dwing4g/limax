package limax.node.js.modules.tls;

import java.security.KeyStore.PrivateKeyEntry;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class VirtualServerKeyManager {
	private final Map<String, Item> map;
	private int aliasgen = 0;

	private static class Item {
		private final PrivateKey privateKey;
		private final X509Certificate[] certificateChain;
		private final Pattern pattern;
		private final String keyType;

		private Item(PrivateKeyEntry entry) {
			this.privateKey = entry.getPrivateKey();
			this.pattern = TLSConfig.getDNSPattern((X509Certificate) entry.getCertificate());
			this.certificateChain = (X509Certificate[]) entry.getCertificateChain();
			if (privateKey instanceof RSAPrivateKey)
				this.keyType = "RSA";
			else if (privateKey instanceof DSAPrivateKey)
				this.keyType = "DSA";
			else if (privateKey instanceof ECPrivateKey)
				this.keyType = "EC";
			else
				throw new RuntimeException("Unknown KeyType " + privateKey.getClass());
		}

		private boolean match(String keyType, String dnsName) {
			return keyType.equals(this.keyType) && pattern.matcher(dnsName).matches();
		}
	}

	VirtualServerKeyManager(List<PrivateKeyEntry> keyEntries) {
		this.map = keyEntries.stream().map(entry -> new Item(entry))
				.collect(Collectors.toMap(item -> "VirtualServerKey." + aliasgen++, Function.identity()));
	}

	PrivateKey getPrivateKey(String alias) {
		VirtualServerKeyManager.Item item = map.get(alias);
		return item == null ? null : item.privateKey;
	}

	X509Certificate[] getCertificateChain(String alias) {
		VirtualServerKeyManager.Item item = map.get(alias);
		return item == null ? null : item.certificateChain;
	}

	String choose(String keyType, String dnsName) {
		return map.entrySet().stream().filter(e -> e.getValue().match(keyType, dnsName)).map(Map.Entry::getKey)
				.findFirst().orElse(null);
	}
}