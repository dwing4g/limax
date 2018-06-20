package limax.net;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;

/**
 * <p>
 * Specification
 * 
 * <p>
 * process throws Exception, the transport closed.
 *
 */

public abstract class Skeleton {
	private volatile Object note;

	Skeleton() {
	}

	abstract void setTransport(Transport transport);

	abstract void _unmarshal(OctetsStream os) throws MarshalException;

	abstract void dispatch();

	public abstract int getType();

	public abstract void process() throws Exception;

	public final Object getNote() {
		return note;
	}

	public final void setNote(Object note) {
		this.note = note;
	}

}
