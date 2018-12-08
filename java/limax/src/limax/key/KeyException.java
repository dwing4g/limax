package limax.key;

import limax.codec.CodecException;

public class KeyException extends CodecException {
	public static enum Type {
		SubjectAltNameWithoutDNSName, SubjectAltNameWithoutURI, MalformedKeyIdent, UnsupportedURI, ServerRekeyed
	}

	private static final long serialVersionUID = -1277781410788961548L;
	private final Type type;

	public KeyException(Type type, String message) {
		super(type.toString() + "/" + message);
		this.type = type;
	}

	public KeyException(Type type) {
		super(type.toString());
		this.type = type;
	}

	public Type getType() {
		return type;
	}
}
