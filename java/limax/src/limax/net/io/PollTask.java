package limax.net.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Queue;

import limax.util.Trace;

class PollTask implements Runnable, Comparable<PollTask> {
	private final Selector sel;
	private volatile boolean closed;
	private volatile int size;

	int size() {
		return size;
	}

	@Override
	public int compareTo(PollTask op) {
		return size - op.size();
	}

	PollTask() throws IOException {
		this.sel = Selector.open();
	}

	synchronized SelectionKey register(SelectableChannel ch, int ops, Object att) throws ClosedChannelException {
		return ch.register(sel.wakeup(), ops, att);
	}

	void close() {
		closed = true;
		sel.wakeup();
	}

	private void doAccept(SelectionKey key) throws IOException {
		ServerContextImpl context = (ServerContextImpl) key.attachment();
		NetOperation op = context.newInstance();
		SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
		channel.configureBlocking(false);
		op.attachKey(NetModel.pollPolicy.addChannel(channel, 0, op));
	}

	private void doConnect(SelectionKey key) throws IOException {
		NetOperation op = (NetOperation) key.attachment();
		SocketChannel channel = (SocketChannel) key.channel();
		if (!channel.finishConnect())
			throw new IOException("impossibly, channel's socket is NOT connected");
		op.attachKey(key.interestOps(0));
	}

	private void doRead(SelectionKey key) throws IOException {
		NetOperation op = (NetOperation) key.attachment();
		SocketChannel channel = (SocketChannel) key.channel();
		ByteBuffer rbuf = op.getReaderBuffer();
		synchronized (rbuf) {
			if (channel.read(rbuf) == -1)
				throw new IOException("the channel has reached end-of-stream");
			if (!rbuf.hasRemaining()) {
				key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
				op.onReadBufferFull();
			}
		}
	}

	private void doWrite(SelectionKey key) throws IOException {
		NetOperation op = (NetOperation) key.attachment();
		SocketChannel channel = (SocketChannel) key.channel();
		Queue<ByteBuffer> vbuf = op.getWriteBuffer();
		synchronized (vbuf) {
			op.bytesSent(channel.write(vbuf.toArray(new ByteBuffer[0])));
			while (!vbuf.isEmpty() && !vbuf.peek().hasRemaining())
				vbuf.poll();
			if (vbuf.isEmpty()) {
				if (op.isFinalInitialized()) {
					op.setFinal();
					throw new IOException("channel closed manually");
				}
				key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
				op.onWriteBufferEmpty();
			}
		}
	}

	private void doClose(SelectionKey key, Throwable e) {
		Object att = key.attachment();
		if (att instanceof NetOperation)
			((NetOperation) att).close(e);
		else if (att instanceof ServerContextImpl)
			try {
				((ServerContextImpl) att).close();
			} catch (IOException e1) {
			}
	}

	@Override
	public final void run() {
		try {
			synchronized (this) {
			}
			sel.selectedKeys().clear();
			sel.select();
			for (SelectionKey key : sel.selectedKeys()) {
				try {
					if (key.isAcceptable()) {
						try {
							doAccept(key);
						} catch (Throwable e) {
						}
					} else if (key.isConnectable()) {
						try {
							doConnect(key);
						} catch (Throwable e) {
							doClose(key, e);
						}
					} else {
						Throwable catched = null;
						boolean needsche = false;
						if (key.isReadable()) {
							try {
								doRead(key);
								needsche = true;
							} catch (Throwable e) {
								catched = e;
							}
						}
						if (key.isWritable()) {
							try {
								doWrite(key);
								needsche = true;
							} catch (Throwable e) {
								if (catched == null)
									catched = e;
							}
						}
						if (catched != null)
							doClose(key, catched);
						else if (needsche)
							((NetOperation) key.attachment()).schedule();
					}
				} catch (CancelledKeyException e) {
				}
			}
		} catch (Throwable e) {
			if (Trace.isDebugEnabled())
				Trace.debug("limax.net.io.PollTask", e);
		} finally {
			if (closed) {
				IOException e = new IOException("channel closed manually");
				for (SelectionKey key : sel.keys())
					doClose(key, e);
				try {
					sel.close();
				} catch (IOException e1) {
				}
			} else {
				size = sel.keys().size();
				NetModel.pollPolicy.schedule(this);
			}
		}
	}
}