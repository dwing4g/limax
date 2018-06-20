package limax.endpoint.script;

public interface LmkDataReceiver {
	void onLmkData(String lmkdata, Runnable done) throws Exception;
}
