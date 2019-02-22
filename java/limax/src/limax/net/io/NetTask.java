package limax.net.io;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLSession;

public interface NetTask {
	interface SendBufferNotice {
		void accept(long size, Object attachment);
	}

	void send(ByteBuffer[] bbs);

	void send(ByteBuffer bb);

	void send(byte[] data, int off, int len);

	void send(byte[] data);

	void sendFinal(long timeout);

	void sendFinal();

	void disable();

	void enable();

	long getSendBufferSize();

	void setSendBufferNotice(SendBufferNotice notice, Object attachment);

	void setServiceShutdownNotice(Runnable notice);

	void resetAlarm(long millisecond);

	boolean isSSLSupported();

	void attachSSL(byte[] negotiationData);

	void detachSSL();

	void renegotiateSSL();

	SSLSession getSSLSession();
}
