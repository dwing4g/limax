
package limax.codec;

public final class MarshalException extends Exception {
	private static final long serialVersionUID = 3767710265413080217L;

	MarshalException(Throwable e) {
		super(e);
	}

	MarshalException() {
	}
}
