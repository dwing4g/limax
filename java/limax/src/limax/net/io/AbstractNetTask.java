package limax.net.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSession;

abstract class AbstractNetTask implements NetTask {
	final static AtomicLong total = new AtomicLong();
	final static AtomicLong running = new AtomicLong();

	private static class Notice {
		private final SendBufferNotice notice;
		private final Object attachment;

		Notice(SendBufferNotice notice, Object attachment) {
			this.notice = notice;
			this.attachment = attachment;
		}

		void accept(long size) {
			notice.accept(size, attachment);
		}
	}

	private final SSLExchange sslExchange;
	protected final ByteBuffer rbuf;
	private final Queue<ByteBuffer> vbuf = new ArrayDeque<ByteBuffer>();
	protected final int wsize;
	private boolean finalizing = false;
	private long fin = 0;
	private long wremain = 0;
	private volatile Notice wnotice;
	private volatile Runnable snotice;
	private final AtomicReference<Throwable> closeReason = new AtomicReference<Throwable>();
	private final NetProcessor processor;
	private boolean active;
	private final Alarm alarm = createAlarm("the channel closed by alarm");

	AbstractNetTask(int rsize, int wsize, SSLExchange sslExchange, NetProcessor processor) {
		this.sslExchange = sslExchange;
		this.rbuf = ByteBuffer.allocateDirect(rsize);
		this.wsize = wsize;
		this.processor = processor;
		total.incrementAndGet();
		running.incrementAndGet();
	}

	@Override
	public boolean isSSLSupported() {
		return sslExchange.isSSLSupported();
	}

	@Override
	public void attachSSL(byte[] negotiationData) {
		attachSSL(null, negotiationData);
	}

	final void attachSSL(InetSocketAddress addr, SSLEngineDecorator decorator, byte[] negotiationData) {
		synchronized (sslExchange) {
			if (!sslExchange.on())
				sslExchange.attach(this, addr.getHostName(), addr.getPort(), decorator, negotiationData);
		}
	}

	@Override
	public void detachSSL() {
		synchronized (sslExchange) {
			if (sslExchange.on())
				sslExchange.detach();
		}
	}

	@Override
	public void renegotiateSSL() {
		synchronized (sslExchange) {
			if (sslExchange.on())
				sslExchange.renegotiate();
		}
	}

	@Override
	public SSLSession getSSLSession() {
		synchronized (sslExchange) {
			return sslExchange.on() ? sslExchange.getSSLSession() : null;
		}
	}

	@Override
	public long getSendBufferSize() {
		synchronized (vbuf) {
			return wremain;
		}
	}

	@Override
	public void setSendBufferNotice(SendBufferNotice notice, Object attachment) {
		wnotice = notice != null ? new Notice(notice, attachment) : null;
	}

	@Override
	public void setServiceShutdownNotice(Runnable notice) {
		snotice = notice;
	}

	@Override
	public void cancel(Throwable closeReason) {
		close(closeReason);
	}

	@Override
	public Alarm createAlarm(final String description) {
		return NetModel.createAlarm(new Runnable() {
			@Override
			public void run() {
				close(new SocketTimeoutException(description));
			}
		});
	}

	@Override
	public void resetAlarm(long milliseconds) {
		if (milliseconds >= 0)
			alarm.reset(milliseconds);
	}

	@Override
	public void execute(final Runnable r) {
		schedule(new Runnable() {
			@Override
			public void run() {
				if (active)
					r.run();
			}
		});
	}

	@Override
	public void send(ByteBuffer[] bbs) {
		synchronized (sslExchange) {
			if (finalizing)
				return;
			if (sslExchange.on()) {
				sslExchange.send(bbs);
				return;
			}
		}
		output(bbs);
	}

	@Override
	public void send(ByteBuffer bb) {
		synchronized (sslExchange) {
			if (finalizing)
				return;
			if (sslExchange.on()) {
				sslExchange.send(bb);
				return;
			}
		}
		output(bb);
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
	public void sendFinal(long timeout) {
		synchronized (sslExchange) {
			if (finalizing)
				return;
			finalizing = true;
			if (sslExchange.on()) {
				sslExchange.sendFinal(timeout);
				return;
			}
		}
		outputFinal(timeout);
	}

	@Override
	public void sendFinal() {
		sendFinal(0);
	}

	private byte[] recv() {
		ByteBuffer bb;
		rbuf.flip();
		synchronized (sslExchange) {
			bb = sslExchange.on() ? sslExchange.recv(rbuf) : rbuf;
		}
		byte[] data = new byte[bb.remaining()];
		bb.get(data);
		bb.clear();
		return data;
	}

	final void onExchange() {
		byte[] data = recv();
		if (data.length == 0)
			return;
		try {
			processor.process(data);
		} catch (Throwable e) {
			close(e);
		}
	}

	final ByteBuffer[] onCollect(Runnable emptyAction) {
		synchronized (vbuf) {
			if (wremain > 0)
				return vbuf.toArray(new ByteBuffer[0]);
			if (fin != 0) {
				if (fin > 0)
					try {
						shutdown();
						NetModel.delayPool.schedule(new Runnable() {
							@Override
							public void run() {
								close(new IOException("the channel closed after delayed"));
							}
						}, fin, TimeUnit.MILLISECONDS);
						return null;
					} catch (Exception e) {
					}
				close(new IOException("the channel closed manually"));
			}
			if (emptyAction != null)
				emptyAction.run();
			return null;
		}
	}

	final void onSent(long nsent) {
		long remain;
		synchronized (vbuf) {
			if ((remain = wremain -= nsent) == 0) {
				vbuf.clear();
			} else {
				while (!vbuf.isEmpty() && !vbuf.peek().hasRemaining())
					vbuf.poll();
			}
		}
		Notice notice = wnotice;
		if (notice != null)
			try {
				notice.accept(remain);
			} catch (Throwable t) {
				close(t);
			}
	}

	void onClose() {
		processor.shutdown(closeReason.get());
	}

	final void onServiceShutdown() {
		if (snotice != null) {
			try {
				snotice.run();
				return;
			} catch (Throwable t) {
			}
		}
		close(new IOException("the channel closed by service shutdown"));
	}

	final void output(ByteBuffer[] bbs) {
		boolean flush = false;
		synchronized (vbuf) {
			for (ByteBuffer bb : bbs) {
				int remaining = bb.remaining();
				if (remaining > 0) {
					wremain += remaining;
					vbuf.offer(bb);
					flush = true;
				}
			}
		}
		if (flush)
			flush();
	}

	final void output(ByteBuffer bb) {
		int remaining = bb.remaining();
		if (remaining > 0) {
			synchronized (vbuf) {
				wremain += remaining;
				vbuf.offer(bb);
			}
			flush();
		}
	}

	final void outputFinal(long timeout) {
		synchronized (vbuf) {
			fin = timeout > 0 ? timeout : -1;
		}
		flush();
	}

	abstract void flush();

	abstract void shutdown() throws IOException;

	abstract void close(Throwable e);

	final void close(Throwable e, Runnable close) {
		if (!closeReason.compareAndSet(null, e))
			return;
		running.decrementAndGet();
		synchronized (sslExchange) {
			finalizing = true;
		}
		close.run();
		schedule(new Runnable() {
			@Override
			public void run() {
				active = false;
				onClose();
			}
		});
	}

	final void startup(SocketAddress local, SocketAddress peer) throws Exception {
		active = true;
		if (processor.startup(this, local, peer))
			enable();
	}

	final void schedule(Runnable r) {
		NetModel.processPool.execute(this, r);
	}
}
