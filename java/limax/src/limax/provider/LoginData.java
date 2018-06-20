package limax.provider;

import limax.codec.Octets;

public final class LoginData {
	private final Integer label;
	private final Octets data;

	LoginData(Integer label, Octets data) {
		this.label = label;
		this.data = data;
	}

	LoginData(Octets data) {
		this(null, data);
	}

	public boolean isSafe() {
		return label != null;
	}

	public int getLabel() {
		return isSafe() ? label : 0;
	}

	public Octets getData() {
		return data;
	}
}
