package limax.net.io;

public interface ServerContextMXBean extends ServerContext {
	int getReadBufferSize();

	void setReadBufferSize(int size);

	int getWriteBufferSize();

	void setWriteBufferSize(int size);

	int getBacklog();

	void setBacklog(int size);

	String getServiceName();

	int getTaskCount();
}