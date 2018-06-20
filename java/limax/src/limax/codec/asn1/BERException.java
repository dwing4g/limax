package limax.codec.asn1;

public class BERException extends RuntimeException {
	private static final long serialVersionUID = 8048521811471058218L;

	public BERException(String message) {
		super(message);
	}

	public BERException(Throwable cause) {
		super(cause);
	}
}
