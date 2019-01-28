package limax.net.io;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import limax.util.Trace;

class PollTask implements Runnable, Comparable<PollTask> {
	private final Selector sel;
	private volatile int closing;
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

	void shutdown() {
		closing = 1;
		sel.wakeup();
	}

	synchronized void awaitTermination() {
		while (closing != 2 || size != 0)
			try {
				wait();
			} catch (InterruptedException e) {
			}
		try {
			sel.close();
		} catch (IOException e) {
		}
	}

	@Override
	public void run() {
		try {
			synchronized (this) {
			}
			sel.selectedKeys().clear();
			sel.select();
			for (SelectionKey key : sel.selectedKeys()) {
				try {
					int readyOps = key.readyOps();
					if ((readyOps & SelectionKey.OP_ACCEPT) != 0) {
						try {
							PollNetTask task = ((PollServerContext) key.attachment()).newInstance();
							task.attachKey(NetModel.pollPolicy.addChannel(
									((ServerSocketChannel) key.channel()).accept().configureBlocking(false), 0, task));
						} catch (Exception e) {
						}
					} else if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
						PollNetTask task = (PollNetTask) key.attachment();
						try {
							((SocketChannel) key.channel()).finishConnect();
							task.attachKey(key.interestOps(0));
						} catch (Exception e) {
							task.close(e);
						}
					} else {
						PollNetTask task = (PollNetTask) key.attachment();
						synchronized (key) {
							key.interestOps(key.interestOps() & ~readyOps);
							task.readyOps |= readyOps;
						}
						task.schedule();
					}
				} catch (CancelledKeyException e) {
				}
			}
		} catch (Throwable e) {
			if (Trace.isDebugEnabled())
				Trace.debug("limax.net.io.PollTask", e);
		} finally {
			switch (closing) {
			case 1:
				for (SelectionKey key : sel.keys()) {
					Object att = key.attachment();
					if (att instanceof PollNetTask)
						((PollNetTask) att).onServiceShutdown();
				}
				try {
					sel.selectNow();
				} catch (IOException e) {
				}
				closing = 2;
			case 2:
				if ((size = sel.keys().size()) > 0)
					NetModel.pollPolicy.schedule(this);
				else
					synchronized (this) {
						notify();
					}
				break;
			default:
				size = sel.keys().size();
				NetModel.pollPolicy.schedule(this);
			}
		}
	}
}