package limax.net.io;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

class PollNetTask extends AbstractNetTask implements Runnable {
	private enum State {
		CLOSED, BINDKEY, EXCHANGE
	}

	private volatile State state = State.CLOSED;
	private volatile boolean exchange = false;
	private volatile SelectionKey key;
	int readyOps = SelectionKey.OP_READ;

	PollNetTask(int rsize, int wsize, SSLExchange sslExchange, NetProcessor processor) {
		super(rsize, wsize, sslExchange, processor);
	}

	@Override
	public void attachSSL(SSLEngineDecorator decorator, byte[] negotiationData) {
		attachSSL((InetSocketAddress) ((SocketChannel) key.channel()).socket().getRemoteSocketAddress(), decorator,
				negotiationData);
	}

	@Override
	public void disable() {
		exchange = false;
	}

	@Override
	public void enable() {
		exchange = true;
		schedule();
	}

	@Override
	void flush() {
		interestOps(SelectionKey.OP_WRITE);
	}

	@Override
	void shutdown() throws IOException {
		((SocketChannel) key.channel()).socket().shutdownOutput();
	}

	@Override
	void close(Throwable e) {
		close(e, new Runnable() {
			@Override
			public void run() {
				state = State.CLOSED;
				try {
					key.channel().close();
				} catch (Throwable t) {
				}
			}
		});
	}

	@Override
	public void run() {
		switch (state) {
		case BINDKEY:
			state = State.EXCHANGE;
			try {
				Socket socket = ((SocketChannel) key.channel()).socket();
				socket.setReceiveBufferSize(rbuf.capacity());
				socket.setSendBufferSize(wsize);
				socket.setKeepAlive(true);
				startup(socket.getLocalSocketAddress(), socket.getRemoteSocketAddress());
			} catch (Throwable t) {
				close(t);
			}
			break;
		case EXCHANGE:
			try {
				int readyOps, interestOps = 0;
				synchronized (key) {
					readyOps = this.readyOps;
				}
				if ((readyOps & SelectionKey.OP_WRITE) != 0) {
					ByteBuffer[] bba = onCollect(null);
					if (bba != null) {
						try {
							onSent(((SocketChannel) key.channel()).write(bba));
							interestOps |= SelectionKey.OP_WRITE;
						} catch (IOException e) {
							close(e);
							break;
						}
					}
				}
				if (exchange && (readyOps & SelectionKey.OP_READ) != 0) {
					try {
						if (((SocketChannel) key.channel()).read(rbuf) == -1)
							close(new EOFException("the channel has reached end-of-stream"));
					} catch (IOException e) {
						close(e);
						break;
					}
					onExchange();
					if (exchange)
						interestOps |= SelectionKey.OP_READ;
				}
				interestOps(interestOps);
			} catch (CancelledKeyException e) {
			}
		case CLOSED:
		}
	}

	final void attachKey(SelectionKey key) {
		this.state = State.BINDKEY;
		this.key = key;
		schedule();
	}

	private void interestOps(int ops) {
		try {
			synchronized (key) {
				int origin = key.interestOps();
				int expect = origin | ops;
				if (expect != origin) {
					readyOps &= ~expect;
					key.interestOps(expect);
					key.selector().wakeup();
				}
			}
		} catch (Throwable t) {
		}
	}

	void schedule() {
		schedule(this);
	}
}