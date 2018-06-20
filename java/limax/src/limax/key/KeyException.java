package limax.key;

public class KeyException extends Exception {
	public static enum Type {
		SubjectAltNameWithoutDNSName, SubjectAltNameWithoutURI, MalformedKeyIdent, UnsupportedURI, ServerRekeyed
	}

	private static final long serialVersionUID = -1277781410788961548L;
	private final Type type;

	KeyException(Type type, String message) {
		super(type.toString() + "/" + message);
		this.type = type;
	}

	KeyException(Type type) {
		super(type.toString());
		this.type = type;
	}

	public Type getType() {
		return type;
	}
}
