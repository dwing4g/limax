package limax.http;

public interface ServerSentEvents {
	void emit(String data);

	void emit(String event, String id, String data);

	void emit(long milliseconds);

	void done();
}
