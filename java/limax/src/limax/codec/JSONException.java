package limax.codec;

public class JSONException extends CodecException {
	private static final long serialVersionUID = 7067191163493197089L;

	public JSONException(String message) {
		super(message);
	}

	public JSONException(Throwable e) {
		super(e);
	}

	public JSONException(String message, Throwable e) {
		super(message, e);
	}
}