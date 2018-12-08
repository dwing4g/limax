package limax.net.io;

import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

abstract class NetTaskImpl implements Runnable, NetTask, NetOperation {
	private enum State {
		CLOSED, BINDKEY, EXCHANGE, ABORT, CLOSING_FLUSH, CLOSING
	}

	private volatile State state = State.CLOSED;
	private final static byte zero[] = new byte[0];
	final static AtomicLong total = new AtomicLong();
	final static AtomicLong running = new AtomicLong();
	private final ByteBuffer rbuf;
	private final Queue<ByteBuffer> vbuf;
	private final int wsize;
	private long wremain = 0;
	private int fin = 0;
	private volatile Throwable closeReason;
	private volatile boolean suspended = false;
	private volatile SelectionKey key;
	private final NetProcessor processor;
	private final Alarm alarm = new Alarm(new Runnable() {
		@Override
		public void run() {
			close(new SocketTimeoutException());
		}
	});

	@Override
	public ByteBuffer getReaderBuffer() {
		return rbuf;
	}

	@Override
	public Queue<ByteBuffer> getWriteBuffer() {
		return vbuf;
	}

	@Override
	public boolean isFinalInitialized() {
		return fin == 1;
	}

	@Override
	public void setFinal() {
		this.fin = 2;
	}

	@Override
	public void bytesSent(long n) {
		this.wremain -= n;
	}

	public void attachKey(SelectionKey key) throws SocketException {
		synchronized (rbuf) {
			if (closeReason != null)
				return;
			state = State.BINDKEY;
		}
		this.key = key;
		Socket socket = ((SocketChannel) key.channel()).socket();
		socket.setReceiveBufferSize(rbuf.capacity());
		socket.setSendBufferSize(wsize);
		socket.setKeepAlive(true);
		schedule();
	}

	private void close(Throwable e, State s) {
		synchronized (rbuf) {
			if (closeReason != null)
				return;
			closeReason = e;
			state = state == State.EXCHANGE ? s : State.ABORT;
			try {
				key.channel().close();
			} catch (Throwable t) {
			}
		}
		schedule();
	}

	@Override
	public void close(Throwable e) {
		close(e, State.CLOSING_FLUSH);
	}

	@Override
	public void run() {
		switch (state) {
		case BINDKEY:
			try {
				synchronized (rbuf) {
					onBindKey();
					state = State.EXCHANGE;
				}
				total.incrementAndGet();
				running.incrementAndGet();
			} catch (Throwable t) {
				close(t);
			}
			break;
		case EXCHANGE:
			onExchange();
			if (!suspended)
				enableRead();
			break;
		case ABORT:
			state = State.CLOSED;
			onAbort();
			break;
		case CLOSING_FLUSH:
			int position = rbuf.position();
			if (position > 0)
				onExchange();
		case CLOSING:
			state = State.CLOSED;
			try {
				running.decrementAndGet();
				onUnbindKey();
			} catch (Throwable t) {
			}
			onClose();
		default:
		}
	}

	void interestOps(int ops, boolean bSet) {
		try {
			synchronized (key) {
				int origin = key.interestOps();
				int expect = bSet ? origin | ops : origin & ~ops;
				if (expect != origin) {
					key.interestOps(expect);
					key.selector().wakeup();
				}
			}
		} catch (Throwable t) {
			// Actually CancelledKeyException
			// When asynchronous closing happen, channel closed, the key
			// cancelled, ignore this, succeeded operation has no effect.
		}
	}

	@Override
	public void onReadBufferFull() {
	}

	@Override
	public void onWriteBufferEmpty() {
	}

	@Override
	public boolean hasReadBufferRemain() {
		synchronized (rbuf) {
			return rbuf.hasRemaining();
		}
	}

	@Override
	public long getSendBufferSize() {
		synchronized (vbuf) {
			return wremain;
		}
	}

	@Override
	public void enableRead() {
		if (hasReadBufferRemain())
			interestOps(SelectionKey.OP_READ, true);
	}

	@Override
	public void enableWrite() {
		interestOps(SelectionKey.OP_WRITE, true);
	}

	@Override
	public void disableRead() {
		interestOps(SelectionKey.OP_READ, false);
	}

	@Override
	public void disableWrite() {
		interestOps(SelectionKey.OP_WRITE, false);
	}

	@Override
	public void enableReadWrite() {
		int expect = 0;
		if (hasReadBufferRemain())
			expect |= SelectionKey.OP_READ;
		if (getSendBufferSize() > 0 || fin == 1)
			expect |= SelectionKey.OP_WRITE;
		interestOps(expect, true);
	}

	@Override
	public void disableReadWrite() {
		interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE, false);
	}

	@Override
	public void suspend(long millisecond, final Runnable finishSuspend) {
		disableRead();
		suspended = true;
		resetAlarm(0);
		NetModel.delayPool.schedule(new Runnable() {
			public void run() {
				schedule(new Runnable() {
					@Override
					public void run() {
						if (state == State.EXCHANGE) {
							finishSuspend.run();
							suspended = false;
							enableRead();
						}
					}
				});
			}
		}, millisecond, TimeUnit.MILLISECONDS);
	}

	byte[] recv() {
		byte data[];
		synchronized (rbuf) {
			if (rbuf.position() == 0)
				return zero;
			rbuf.flip();
			data = new byte[rbuf.limit()];
			rbuf.get(data);
			rbuf.clear();
		}
		return data;
	}

	protected NetTaskImpl(int rsize, int wsize, NetProcessor processor) {
		this.rbuf = ByteBuffer.allocateDirect(rsize);
		this.wsize = wsize;
		this.vbuf = new ArrayDeque<ByteBuffer>();
		(this.processor = processor).setup(this);
	}

	void send(ByteBuffer buffer) {
		boolean needWrite;
		synchronized (vbuf) {
			if (fin == 0) {
				wremain += buffer.limit();
				vbuf.offer(buffer);
			}
			needWrite = wremain > 0 || fin == 1;
		}
		if (needWrite)
			enableWrite();
	}

	@Override
	public void send(byte[] data, int off, int len) {
		if (len > 0) {
			ByteBuffer bb = ByteBuffer.allocateDirect(len);
			bb.put(data, off, len).rewind();
			send(bb);
		}
	}

	@Override
	public void send(byte[] data) {
		send(data, 0, data.length);
	}

	@Override
	public void sendFinal() {
		synchronized (vbuf) {
			if (fin == 0) {
				fin = 1;
				enableWrite();
			}
		}
	}

	protected void onBindKey() throws Exception {
		Socket socket = ((SocketChannel) key.channel()).socket();
		if (processor.setup(socket.getLocalSocketAddress(), socket.getRemoteSocketAddress()))
			enableReadWrite();
	}

	protected abstract void onUnbindKey() throws Exception;

	void onExchange() {
		try {
			processor.process(recv());
		} catch (Throwable e) {
			close(e, State.CLOSING);
		}
	}

	protected void onAbort() {
		processor.shutdown(false, closeReason);
	}

	protected void onClose() {
		processor.shutdown(true, closeReason);
	}

	@Override
	public void schedule() {
		NetModel.processPool.execute(this, this);
	}

	@Override
	public void schedule(Runnable r) {
		NetModel.processPool.execute(this, r);
	}

	@Override
	public void resetAlarm(long millisecond) {
		if (millisecond >= 0)
			alarm.reset(millisecond);
		else
			close(new SocketTimeoutException("millisecond=" + millisecond));
	}
}