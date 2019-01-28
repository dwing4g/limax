package limax.net.io;

public interface ServerContextMXBean extends ServerContext {
	int getRecvBufferSize();

	void setRecvBufferSize(int size);

	int getSendBufferSize();

	void setSendBufferSize(int size);

	int getBacklog();

	void setBacklog(int size);

	String getServiceName();

	int getTaskCount();
}