package limax.codec;

public class JSONException extends CodecException {
	private static final long serialVersionUID = 7067191163493197089L;

	JSONException(String message) {
		super(message);
	}

	JSONException(Throwable e) {
		super(e);
	}
}