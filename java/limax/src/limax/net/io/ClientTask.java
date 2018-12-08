package limax.net.io;

class ClientTask extends NetTaskImpl {
	public ClientTask(int rsize, int wsize, NetProcessor processor) {
		super(rsize, wsize, processor);
	}

	@Override
	protected final void onBindKey() throws Exception {
		super.onBindKey();
	}

	@Override
	protected final void onUnbindKey() {
	}
}