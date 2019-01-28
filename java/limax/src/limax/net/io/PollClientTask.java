package limax.net.io;

import javax.net.ssl.SSLContext;

class PollClientTask extends PollNetTask {
	PollClientTask(int rsize, int wsize, SSLContext sslContext, NetProcessor processor) {
		super(rsize, wsize, new SSLExchange(sslContext, 0), processor);
	}
}