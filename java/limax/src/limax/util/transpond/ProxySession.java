package limax.util.transpond;

abstract class ProxySession implements FlowControlProcessor {

	private final int bufferhalfsize;
	protected NetStation nettask = null;
	protected ProxySession tosession = null;

	ProxySession(int buffersize) {
		this.bufferhalfsize = buffersize / 2;
	}

	@Override
	public void shutdown(boolean eventually) {
		tosession.closeSession();
	}

	private void closeSession() {
		nettask.closeSession();
	}

	void setAssocNetTask(NetStation nettask) {
		this.nettask = nettask;
	}

	void setProxySession(ProxySession ps) {
		tosession = ps;
	}

	@Override
	public void sendDataTo(byte[] data) throws Exception {
		tosession.nettask.sendData(data);
		synchronized (nettask) {
			if (tosession.nettask.isSendBusy())
				nettask.disableReceive();
		}
	}

	@Override
	public void sendDone(long leftsize) throws Exception {
		if (leftsize < bufferhalfsize)
			synchronized (tosession.nettask) {
				tosession.nettask.enableReceive();
			}
	}

}
