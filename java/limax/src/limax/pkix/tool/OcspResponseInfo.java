package limax.pkix.tool;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Set;

class OcspResponseInfo {
	private final Set<BigInteger> serialNumbers;
	private final Instant nextUpdate;
	private final byte[] response;

	OcspResponseInfo(Set<BigInteger> serialNumbers, Instant nextUpdate, byte[] response) {
		this.serialNumbers = serialNumbers;
		this.nextUpdate = nextUpdate;
		this.response = response;
	}

	Set<BigInteger> getSerialNumbers() {
		return serialNumbers;
	}

	Instant getNextUpdate() {
		return nextUpdate;
	}

	byte[] getResponse() {
		return response;
	}
}