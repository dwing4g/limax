package limax.net;

import limax.codec.CodecException;
import limax.codec.Octets;

public abstract class UnknownProtocolHandler {
	protected UnknownProtocolHandler() {
	}

	protected void check(int type, int size, Transport transport) throws InstantiationException, SizePolicyException {
		if (transport instanceof SupportStateCheck)
			((SupportStateCheck) transport).check(type, size);
	}

	protected void dispatch(int type, Octets data, Transport transport) throws CodecException {
	}

	public final void send(int type, Octets data, Transport transport)
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
		check(type, data.size(), transport);
		try {
			((SupportTypedDataTransfer) transport).send(type, data);
		} catch (ClassCastException e) {
			throw e;
		} catch (Exception e) {
			throw new CodecException(e);
		}
	}

	public final void broadcast(int type, Octets data, Manager manager)
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
		((SupportTypedDataBroadcast) manager).broadcast(type, data);
	}

}
