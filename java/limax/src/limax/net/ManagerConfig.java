package limax.net;

import limax.util.Dispatcher;

public interface ManagerConfig extends Config {
	String getName();

	int getInputBufferSize();

	int getOutputBufferSize();

	boolean isCheckOutputBuffer();

	byte[] getOutputSecurityBytes();

	byte[] getInputSecurityBytes();

	boolean isOutputCompress();

	boolean isInputCompress();

	State getDefaultState();

	Dispatcher getDispatcher();

	boolean isAsynchronous();
}
