package limax.net.io;

import javax.net.ssl.SSLContext;

class AsynchronousClientTask extends AsynchronousNetTask {
	AsynchronousClientTask(int rsize, int wsize, SSLContext sslContext, NetProcessor processor) {
		super(rsize, wsize, new SSLExchange(sslContext, 0), processor);
	}
}
