package limax.net.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

import javax.net.ssl.SSLContext;

class PollServerContext extends AbstractServerContext {
	PollServerContext(SocketAddress sa, int backlog, int rsize, int wsize, SSLContext sslContext, int sslMode,
			NetTaskConstructor constructor) {
		super(sa, backlog, rsize, wsize, sslContext, sslMode, constructor);
	}

	@Override
	public synchronized void open() throws IOException {
		if (channel == null) {
			ServerSocketChannel channel = ServerSocketChannel.open();
			channel.configureBlocking(false);
			channel.socket().setReuseAddress(true);
			channel.socket().bind(sa, backlog);
			NetModel.pollPolicy.addChannel(channel, SelectionKey.OP_ACCEPT, this);
			this.channel = channel;
		}
	}

	PollNetTask newInstance() {
		return (PollNetTask) constructor.newInstance(this);
	}
}