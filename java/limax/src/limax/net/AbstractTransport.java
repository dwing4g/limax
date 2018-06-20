package limax.net;

abstract class AbstractTransport implements Transport {
	private volatile Object sessionobj;
	private volatile Throwable closeReason;

	@Override
	public void setSessionObject(Object sessionobj) {
		this.sessionobj = sessionobj;
	}

	@Override
	public Object getSessionObject() {
		return sessionobj;
	}

	@Override
	public Throwable getCloseReason() {
		return closeReason;
	}

	void setCloseReason(Throwable closeReason) {
		this.closeReason = closeReason;
	}

	abstract void close();
}
