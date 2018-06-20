package limax.pkix.tool;

import java.math.BigInteger;
import java.util.Set;

class OcspResponseInfo {
	private final Set<BigInteger> serialNumbers;
	private final long nextUpdate;
	private final byte[] response;

	OcspResponseInfo(Set<BigInteger> serialNumbers, long nextUpdate, byte[] response) {
		this.serialNumbers = serialNumbers;
		this.nextUpdate = nextUpdate;
		this.response = response;
	}

	Set<BigInteger> getSerialNumbers() {
		return serialNumbers;
	}

	long getNextUpdate() {
		return nextUpdate;
	}

	byte[] getResponse() {
		return response;
	}
}