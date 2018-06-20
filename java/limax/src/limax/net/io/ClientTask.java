package limax.net.io;

class ClientTask extends NetTaskImpl {
	public ClientTask(int rsize, int wsize, NetProcessor processor) {
		super(rsize, wsize, processor);
	}

	protected final void onBindKey() throws Exception {
		super.onBindKey();
	}

	protected final void onUnbindKey() {
	}
}