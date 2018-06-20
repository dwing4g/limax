package limax.key.ed;

import limax.key.KeyDesc;

class KeyRep {
	private final byte[] ident;
	private final byte[] key;

	KeyRep(byte[] ident, byte[] key) {
		this.ident = ident;
		this.key = key;
	}

	KeyRep(KeyDesc keyDesc) {
		this(keyDesc.getIdent(), keyDesc.getKey());
	}

	byte[] getIdent() {
		return ident;
	}

	byte[] getKey() {
		return key;
	}
}
