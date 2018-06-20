package limax.zdb;

/**
 * CANNOT catch the Error, framework catch it
 * 
 * @see Procedure
 * 
 */
public class XError extends Error {
	private static final long serialVersionUID = -2753495176885937511L;

	public XError() {
	}

	public XError(String message) {
		super(message);
	}

	public XError(Throwable cause) {
		super(cause);
	}

	public XError(String message, Throwable cause) {
		super(message, cause);
	}
}
