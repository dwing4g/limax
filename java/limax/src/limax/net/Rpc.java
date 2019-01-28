package limax.net;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import limax.codec.CodecException;
import limax.codec.Marshal;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.util.Closeable;
import limax.util.Trace;

public abstract class Rpc<A extends Marshal, R extends Marshal> extends Skeleton {

	public interface Listener<A extends Marshal, R extends Marshal> {
		void onTimeout(Rpc<A, R> rpc);

		void onClient(Rpc<A, R> rpc) throws Exception;

		void onCancel(Rpc<A, R> rpc);
	}

	private volatile boolean isRequest;
	private volatile int sid;
	private volatile A argument;
	private volatile R result;
	private final Listener<A, R> listener;

	protected Rpc() {
		this(null);
	}

	protected Rpc(Listener<A, R> listener) {
		this.listener = listener;
	}

	private final InnerProtocol protocol = new InnerProtocol();

	private final class InnerProtocol extends Protocol implements Closeable {
		private ScheduledFuture<?> future;

		@Override
		public final void close() {
			if (null != Rpc.this.future)
				Rpc.this.future.setException(new IOException("AsynchronousClose"));
			else {
				future.cancel(false);
				_onCancel();
			}
		}

		@Override
		public final OctetsStream marshal(OctetsStream os) {
			return isRequest ? os.marshal(sid | 0x80000000).marshal(argument) : os.marshal(sid).marshal(result);
		}

		@Override
		public final OctetsStream unmarshal(OctetsStream os) throws MarshalException {
			sid = os.unmarshal_int();
			isRequest = (sid & 0x80000000) != 0;
			if (isRequest) {
				sid = sid & 0x7fffffff; // clear request mask
				return argument.unmarshal(os);
			} else {
				return result.unmarshal(os);
			}
		}

		Rpc<A, R> getRpc() {
			return Rpc.this;
		}

		@Override
		public final void process() throws Exception {
			Rpc.this.process();
		}

		@Override
		public String toString() {
			return Rpc.this.getClass().getName() + "(" + argument + ", " + result + ")";
		}

		@Override
		public int getType() {
			return Rpc.this.getType();
		}
	}

	@Override
	final void setTransport(Transport transport) {
		protocol.setTransport(transport);
	}

	@Override
	final void _unmarshal(OctetsStream os) throws MarshalException {
		protocol.unmarshal(os);
	}

	@Override
	final void dispatch() {
		if (isRequest) {
			protocol.dispatch();
			return;
		}
		// response
		InnerProtocol inner = ((SupportRpcContext) protocol.getManager()).removeContext(sid, protocol);
		if (null == inner) {
			if (Trace.isInfoEnabled())
				Trace.info("limax.net.Rpc.response context lost! " + this);
			return;
		}
		if (inner.future != null)
			inner.future.cancel(false);
		Rpc<A, R> rpc = inner.getRpc().clearRequest().setResult(result);
		rpc.setTransport(getTransport());
		if (rpc.future != null) {
			if (Trace.isDebugEnabled())
				Trace.debug("limax.net.Rpc.response with submit " + this);
			rpc.future.set(result);
			return;
		}
		if (Trace.isDebugEnabled())
			Trace.debug("limax.net.Rpc.response execute " + this);
		inner.dispatch();
	}

	public void process() throws Exception {
		if (isRequest) {
			onServer();
			response();
		} else {
			_onClient();
		}
	}

	private Future<R> future;

	public final A getArgument() {
		return argument;
	}

	public final R getResult() {
		return result;
	}

	public final Rpc<A, R> setArgument(A argument) {
		this.argument = argument;
		return this;
	}

	public final Rpc<A, R> setResult(R result) {
		this.result = result;
		return this;
	}

	private Rpc<A, R> clearRequest() {
		isRequest = false;
		return this;
	}

	public final Transport getTransport() {
		return protocol.getTransport();
	}

	public final Manager getManager() {
		return protocol.getTransport().getManager();
	}

	private final void _onCancel() {
		if (listener != null)
			listener.onCancel(this);
		else
			onCancel();
	}

	private final void _onTimeout() {
		if (listener != null)
			listener.onTimeout(this);
		else
			onTimeout();
	}

	private final void _onClient() throws Exception {
		if (listener != null)
			listener.onClient(this);
		else
			onClient();
	}

	private static class Timeout implements Runnable {
		private final Manager manager;
		private final int sid;

		Timeout(Manager manager, int sid) {
			this.manager = manager;
			this.sid = sid;
		}

		public void run() {
			Closeable c = ((SupportRpcContext) manager).removeContext(sid);
			if (c instanceof Rpc<?, ?>.InnerProtocol) {
				Rpc<?, ?> rpc = ((Rpc<?, ?>.InnerProtocol) c).getRpc();
				rpc._onTimeout();
			}
		}
	}

	private void checkSend(Transport transport, Octets data)
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
		if (transport instanceof SupportStateCheck)
			((SupportStateCheck) transport).check(getType(), data.size());
		((SupportTypedDataTransfer) transport).send(getType(), data);
	}

	public final void send(Transport transport)
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
		try {
			isRequest = true;
			Manager manager = transport.getManager();
			sid = ((SupportRpcContext) manager).addContext(protocol);
			checkSend(transport, new OctetsStream().marshal(protocol));
			if (future == null)
				protocol.future = Engine.getProtocolScheduler().schedule(new Timeout(manager, sid), getTimeout(),
						TimeUnit.MILLISECONDS);
		} catch (RuntimeException e) {
			throw new CodecException(e);
		}
	}

	public final void response()
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
		try {
			clearRequest().checkSend(protocol.getTransport(), new OctetsStream().marshal(protocol));
		} catch (RuntimeException e) {
			throw new CodecException(e);
		}
	}

	public final Future<R> submit(Transport transport)
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
		try {
			future = new Future<R>(this.getTimeout());
			send(transport);
			return future;
		} catch (InstantiationException e) {
			future = null;
			throw e;
		} catch (SizePolicyException e) {
			future = null;
			throw e;
		} catch (CodecException e) {
			future = null;
			throw e;
		}
	}

	protected void onTimeout() {
	}

	protected void onCancel() {
	}

	protected void onServer() throws Exception {
		throw new UnsupportedOperationException("onServer of " + getClass().getName());
	}

	protected void onClient() throws Exception {
		throw new UnsupportedOperationException("onClient of " + getClass().getName());
	}

	protected abstract long getTimeout();

	@Override
	public String toString() {
		return getClass().getName();
	}

	public final static class Future<R> extends FutureTask<R> {
		private final static Runnable dummy = new Runnable() {
			public void run() {
			}
		};
		private final long defaulttimeout;

		public Future(long timeout) {
			super(dummy, null);
			this.defaulttimeout = timeout;
		}

		@Override
		public R get() throws InterruptedException, ExecutionException {
			try {
				return get(defaulttimeout, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				throw new ExecutionException(e);
			}
		}

		@Override
		public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			return super.get(timeout, unit);
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			throw new UnsupportedOperationException();
		}

		@Override
		protected void set(R v) {
			super.set(v);
		}

		@Override
		protected void setException(Throwable t) {
			super.setException(t);
		}
	}
}
