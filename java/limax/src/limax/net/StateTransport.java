package limax.net;

public interface StateTransport extends Transport {
	void setOutputSecurityCodec(byte[] key, boolean compress);

	void setInputSecurityCodec(byte[] key, boolean compress);

	void setState(State state);

	void resetAlarm(long milliseconds);
}
