package limax.net;

import java.util.Objects;

import limax.codec.CodecException;
import limax.codec.Marshal;
import limax.codec.MarshalException;
import limax.codec.OctetsStream;
import limax.util.Trace;

public abstract class WebSocketProtocol implements Runnable, Marshal {
	private volatile WebSocketTransport transport;
	private String text;
	private byte[] binary;

	protected WebSocketProtocol(OctetsStream os) throws MarshalException {
		unmarshal(os);
	}

	protected WebSocketProtocol(String text) {
		this.text = Objects.requireNonNull(text);
		this.binary = null;
	}

	protected WebSocketProtocol(byte[] binary) {
		this.text = null;
		this.binary = Objects.requireNonNull(binary);
	}

	final void setTransport(WebSocketTransport transport) {
		this.transport = transport;
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		if (text != null)
			return os.marshal((byte) 1).marshal(text);
		return os.marshal((byte) 2).marshal(binary);
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		byte type = os.unmarshal_byte();
		if (type == 1) {
			text = os.unmarshal_String();
			binary = null;
		} else {
			text = null;
			binary = os.unmarshal_bytes();
		}
		return os;
	}

	public void send(Transport transport) throws CodecException, ClassCastException {
		try {
			if (text != null)
				((SupportWebSocketTransfer) transport).send(text);
			else
				((SupportWebSocketTransfer) transport).send(binary);
		} catch (ClassCastException e) {
			throw e;
		} catch (Throwable e) {
			throw new CodecException(e);
		}
	}

	public void broadcast(Manager manager) throws CodecException, ClassCastException {
		if (text != null)
			((SupportWebSocketBroadcast) manager).broadcast(text);
		else
			((SupportWebSocketBroadcast) manager).broadcast(binary);
	}

	public byte[] getBinary() {
		return binary;
	}

	public String getText() {
		return text;
	}

	public WebSocketTransport getTransport() {
		return transport;
	}

	public Manager getManager() {
		return transport.getManager();
	}

	@Override
	public void run() {
		try {
			process();
		} catch (Throwable e) {
			if (Trace.isInfoEnabled())
				Trace.info("WebSocketProtocol.run " + this, e);
			getManager().close(transport);
		}
	}

	public abstract void process() throws Exception;
}
