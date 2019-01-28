package limax.net.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

import javax.net.ssl.SSLContext;

class AsynchronousServerContext extends AbstractServerContext {
	AsynchronousServerContext(SocketAddress sa, int backlog, int rsize, int wsize, SSLContext sslContext, int sslMode,
			NetTaskConstructor constructor) {
		super(sa, backlog, rsize, wsize, sslContext, sslMode, constructor);
	}

	private void accept(AsynchronousServerSocketChannel channel) {
		channel.accept(this, new CompletionHandler<AsynchronousSocketChannel, AsynchronousServerContext>() {
			@Override
			public void completed(AsynchronousSocketChannel result, AsynchronousServerContext attachment) {
				accept(channel);
				((AsynchronousNetTask) constructor.newInstance(attachment)).startup(result);
			}

			@Override
			public void failed(Throwable exc, AsynchronousServerContext attachment) {
			}
		});
	}

	@Override
	public synchronized void open() throws IOException {
		if (channel == null) {
			AsynchronousServerSocketChannel channel = AsynchronousServerSocketChannel.open(NetModel.group);
			channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			channel.bind(sa, backlog);
			accept(channel);
			this.channel = channel;
		}
	}
}
