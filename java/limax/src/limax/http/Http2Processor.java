package limax.http;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import javax.net.ssl.SSLSession;

import limax.http.HttpServer.Parameter;
import limax.http.RFC7540.Connection;
import limax.http.RFC7540.Exchange;
import limax.http.RFC7540.Processor;
import limax.http.RFC7540.Settings;
import limax.http.RFC7540.Stream;
import limax.net.Engine;
import limax.net.io.NetTask;

class Http2Processor implements ProtocolProcessor {
	private final static byte[] PREFACE = new byte[] { 80, 82, 73, 32, 42, 32, 72, 84, 84, 80, 47, 50, 46, 48, 13, 10,
			13, 10, 83, 77, 13, 10, 13, 10 };
	private final NetTask nettask;
	private final Connection connection;
	private int stage;

	private Exchange createExchange(HttpProcessor processor, NetTask nettask, Headers headers) {
		return new Exchange() {
			@Override
			public Processor createProcessor(Stream stream) {
				return new Http2Exchange(processor, connection, stream, headers);
			}

			@Override
			public void sendFinal(long timeout) {
				nettask.sendFinal(timeout);
			}

			@Override
			public void send(ByteBuffer[] bbs) {
				nettask.send(bbs);
			}

			@Override
			public void send(ByteBuffer data) {
				nettask.send(data);
			}

			@Override
			public SSLSession getSSLSession() {
				return nettask.getSSLSession();
			}

			@Override
			public ScheduledExecutorService getScheduler() {
				return Engine.getProtocolScheduler();
			}

			@Override
			public void execute(Runnable r) {
				nettask.execute(r);
			}
		};
	}

	private Map<Settings, Long> initSettings(HttpProcessor processor) {
		EnumMap<Settings, Long> map = new EnumMap<>(Settings.class);
		for (Settings s : Settings.values())
			try {
				Object obj = processor.get(Parameter.valueOf("HTTP2_" + s.name()));
				long val;
				if (obj instanceof Integer)
					val = ((Integer) obj).longValue();
				else if (obj instanceof Long)
					val = (Long) obj;
				else
					val = s.def();
				map.put(s, s.validate(val) ? val : s.def());
			} catch (Exception e) {
			}
		return map;
	}

	Http2Processor(HttpProcessor processor, NetTask nettask, ByteBuffer preface) {
		this.nettask = nettask;
		this.connection = new Connection(true, createExchange(processor, nettask, null), initSettings(processor));
		this.stage = 18;
		process(preface);
	}

	Http2Processor(HttpProcessor processor, NetTask nettask, Headers headers) {
		this.nettask = nettask;
		this.connection = new Connection(createExchange(processor, nettask, headers),
				headers.getFirst("http2-settings"), initSettings(processor));
		try {
			connection.createPassiveStream(1, null);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		this.stage = 0;
	}

	@Override
	public void process(ByteBuffer in) {
		if (stage < 24)
			while (in.hasRemaining() && stage < 24)
				if (PREFACE[stage++] != in.get())
					throw new RuntimeException("malformed http2 preface");
		nettask.resetAlarm(connection.unwrap(in));
	}

	@Override
	public void shutdown(Throwable closeReason) {
		connection.shutdown(closeReason);
	}
}
