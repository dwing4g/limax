package limax.net.io;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class AsynchronousNetTask extends AbstractNetTask {
	private final static int ST_READY = 0;
	private final static int ST_RUNNING = 1;
	private final static int ST_DISABLE = 2;

	private int recving = ST_DISABLE;
	private final AtomicInteger sending = new AtomicInteger(ST_READY);
	private AsynchronousSocketChannel channel;

	AsynchronousNetTask(int rsize, int wsize, SSLExchange sslExchange, NetProcessor processor) {
		super(rsize, wsize, sslExchange, processor);
		NetModel.add(this);
	}

	@Override
	public void attachSSL(SSLEngineDecorator decorator, byte[] negotiationData) {
		try {
			attachSSL((InetSocketAddress) channel.getRemoteAddress(), decorator, negotiationData);
		} catch (IOException e) {
			close(e);
		}
	}

	@Override
	public void disable() {
		synchronized (sending) {
			recving |= ST_DISABLE;
		}
	}

	@Override
	public void enable() {
		schedule(() -> recving(false));
	}

	@Override
	void flush() {
		try {
			sending();
		} catch (Throwable t) {
		}
	}

	@Override
	void shutdown() throws IOException {
		channel.shutdownOutput();
	}

	@Override
	void close(Throwable e) {
		close(e, () -> {
			try {
				channel.close();
			} catch (Throwable t) {
			} finally {
				NetModel.remove(this);
			}
		});
	}

	void startup(AsynchronousSocketChannel channel) {
		this.channel = channel;
		try {
			channel.setOption(StandardSocketOptions.SO_RCVBUF, rbuf.capacity());
			channel.setOption(StandardSocketOptions.SO_SNDBUF, wsize);
			channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
			startup(channel.getLocalAddress(), channel.getRemoteAddress());
		} catch (Throwable t) {
			close(t);
		}
	}

	void connect(SocketAddress sa) throws IOException {
		AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(NetModel.group);
		channel.connect(sa, null, new CompletionHandler<Void, Object>() {
			@Override
			public void completed(Void result, Object attachment) {
				schedule(() -> startup(channel));
			}

			@Override
			public void failed(Throwable exc, Object attachment) {
				try {
					channel.close();
				} catch (Exception e) {
				} finally {
					close(exc);
				}
			}
		});
	}

	private void recving(boolean loopin) {
		if (loopin) {
			synchronized (sending) {
				if ((recving & ST_DISABLE) != 0) {
					recving = ST_DISABLE;
					return;
				}
				recving = ST_RUNNING;
			}
		} else {
			synchronized (sending) {
				if ((recving &= ~ST_DISABLE) == ST_RUNNING)
					return;
				recving = ST_RUNNING;
			}
		}
		onExchange();
		channel.read(rbuf, Long.MAX_VALUE, TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer, Object>() {
			@Override
			public void completed(Integer result, Object attachment) {
				if (result == -1)
					close(new EOFException("the channel has reached end-of-stream"));
				else
					schedule(() -> recving(true));
			}

			@Override
			public void failed(Throwable exc, Object attachment) {
				close(exc);
			}
		});
	}

	private void sending() {
		if (!sending.compareAndSet(ST_READY, ST_RUNNING))
			return;
		ByteBuffer[] bba = onCollect(() -> sending.set(ST_READY));
		if (bba == null)
			return;
		channel.write(bba, 0, bba.length, Long.MAX_VALUE, TimeUnit.MILLISECONDS, null,
				new CompletionHandler<Long, Object>() {
					@Override
					public void completed(Long result, Object attachment) {
						onSent(result);
						sending.set(ST_READY);
						sending();
					}

					@Override
					public void failed(Throwable exc, Object attachment) {
						close(exc);
					}
				});
	}
}
