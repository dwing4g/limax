package limax.pkix.tool;

import java.util.Arrays;
import java.util.Objects;

class OcspIssuerKey {
	private final String hashAlgorithm;
	private final byte[] issuerNameHash;
	private final byte[] issuerKeyHash;

	OcspIssuerKey(String hashAlgorithm, byte[] issuerNameHash, byte[] issuerKeyHash) {
		this.hashAlgorithm = hashAlgorithm;
		this.issuerNameHash = issuerNameHash;
		this.issuerKeyHash = issuerKeyHash;
	}

	@Override
	public int hashCode() {
		return Objects.hash(hashAlgorithm, Arrays.hashCode(issuerNameHash), Arrays.hashCode(issuerKeyHash));
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof OcspIssuerKey))
			return false;
		OcspIssuerKey o = (OcspIssuerKey) obj;
		return hashAlgorithm.equals(o.hashAlgorithm) && Arrays.equals(issuerNameHash, o.issuerNameHash)
				&& Arrays.equals(issuerKeyHash, o.issuerKeyHash);
	}
}