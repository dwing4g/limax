package limax.http;

import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSession;

import limax.http.RFC6455.RFC6455Exception;
import limax.http.RFC7540.ErrorCode;
import limax.http.RFC7540.Processor;
import limax.http.RFC7540.Stream;
import limax.http.RFC7541.Entry;

class RFC8441 extends AbstractWebSocketExchange implements Processor {
	private final Stream stream;
	private List<ByteBuffer> delayed;
	private boolean cancelled;

	RFC8441(WebSocketHandler handler, AbstractHttpExchange exchange, Stream stream) {
		super(handler, exchange);
		this.stream = stream;
	}

	@Override
	void executeAlarmTask() {
		synchronized (stream) {
			cancelled = true;
			enable();
		}
		stream.sendReset(ErrorCode.CANCEL);
		processor.nettask().execute(() -> shutdown(new SocketTimeoutException("h2 websocket closed by alarm")));
	}

	@Override
	public void process(List<Entry> headers) {
	}

	@Override
	public void process(ByteBuffer in) {
		synchronized (stream) {
			if (delayed != null) {
				delayed.add(in);
			} else {
				int remaining = in.remaining();
				if (!cancelled)
					super.process(in);
				stream.windowUpdate(remaining);
			}
		}
	}

	@Override
	public void end() {
		reset(ErrorCode.STREAM_CLOSED);
	}

	@Override
	public void reset(ErrorCode errorCode) {
		shutdown(new RFC6455Exception(RFC6455.closeProtocol, errorCode.toString()));
	}

	@Override
	public void enable() {
		synchronized (stream) {
			if (delayed != null) {
				int remaining = 0;
				for (ByteBuffer in : delayed) {
					if (!cancelled)
						super.process(in);
					remaining += in.remaining();
				}
				stream.windowUpdate(remaining);
				delayed = null;
			}
		}
	}

	@Override
	public void disable() {
		synchronized (stream) {
			if (delayed == null)
				delayed = new ArrayList<>();
		}
	}

	@Override
	public SSLSession getSSLSession() {
		return processor.nettask().getSSLSession();
	}
}
