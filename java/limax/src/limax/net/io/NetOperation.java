package limax.net.io;

import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Queue;

interface NetOperation {
	ByteBuffer getReaderBuffer();

	Queue<ByteBuffer> getWriteBuffer();

	void attachKey(SelectionKey key) throws SocketException;

	void close(Throwable e);

	boolean isFinalInitialized();

	void setFinal();

	void bytesSent(long n);

	void schedule();

	void onReadBufferFull();

	void onWriteBufferEmpty();
}
