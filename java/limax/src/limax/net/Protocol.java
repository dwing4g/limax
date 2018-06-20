package limax.net;

import limax.codec.CodecException;
import limax.codec.Marshal;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.util.Trace;

public abstract class Protocol extends Skeleton implements Marshal, Runnable {
	private volatile Transport transport;

	protected Protocol() {
	}

	@Override
	final void setTransport(Transport transport) {
		this.transport = transport;
	}

	@Override
	final void _unmarshal(OctetsStream os) throws MarshalException {
		unmarshal(os);
	}

	@Override
	public final void run() {
		try {
			process();
		} catch (Throwable e) {
			if (Trace.isInfoEnabled())
				Trace.info("Protocol.run " + this, e);
			getManager().close(transport);
		}
	}

	@Override
	final void dispatch() {
		((SupportDispatch) getManager()).dispatch(this, transport);
	}

	public final Transport getTransport() {
		return transport;
	}

	public final Manager getManager() {
		return transport.getManager();
	}

	@Override
	public String toString() {
		return getClass().getName();
	}

	public void send(Transport transport)
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
		try {
			final Octets data = new OctetsStream().marshal(this);
			if (transport instanceof SupportStateCheck)
				((SupportStateCheck) transport).check(getType(), data.size());
			((SupportTypedDataTransfer) transport).send(getType(), data);
		} catch (InstantiationException e) {
			throw e;
		} catch (SizePolicyException e) {
			throw e;
		} catch (CodecException e) {
			throw e;
		} catch (ClassCastException e) {
			throw e;
		} catch (Throwable e) {
			throw new CodecException(e);
		}
	}

	public void broadcast(Manager manager)
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
		((SupportTypedDataBroadcast) manager).broadcast(getType(), new OctetsStream().marshal(this));
	}
}
