package limax.http;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
	private final Connection connection;
	private final long HTTP2_INCOMING_FRAME_TIMEOUT;
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
			public Future<?> schedulePeriodically(Runnable r, long period) {
				return Engine.getProtocolScheduler().scheduleAtFixedRate(r, period, period, TimeUnit.MILLISECONDS);
			}

			@Override
			public Future<?> schedule(Runnable r, long delay) {
				return Engine.getProtocolScheduler().schedule(r, delay, TimeUnit.MILLISECONDS);
			}
		};
	}

	private Map<Settings, Integer> initSettings(HttpProcessor processor) {
		Map<Settings, Integer> map = new HashMap<>();
		for (Settings s : Settings.values())
			try {
				map.put(s, (Integer) processor.get(Parameter.valueOf("HTTP2_" + s.name())));
			} catch (Exception e) {
			}
		return map;
	}

	Http2Processor(HttpProcessor processor, NetTask nettask, ByteBuffer preface) {
		this.connection = new Connection(createExchange(processor, nettask, null), true,
				(Long) processor.get(Parameter.HTTP2_RTT_MEASURE_PERIOD),
				(Integer) processor.get(Parameter.HTTP2_RTT_SAMPLES));
		this.connection.sendSettings(initSettings(processor), (Long) processor.get(Parameter.HTTP2_SETTINGS_TIMEOUT));
		this.HTTP2_INCOMING_FRAME_TIMEOUT = (Long) processor.get(Parameter.HTTP2_INCOMING_FRAME_TIMEOUT);
		this.stage = 18;
		process(preface);
	}

	Http2Processor(HttpProcessor processor, NetTask nettask, Headers headers) {
		this.connection = new Connection(createExchange(processor, nettask, headers),
				headers.getFirst("http2-settings"), (Long) processor.get(Parameter.HTTP2_RTT_MEASURE_PERIOD),
				(Integer) processor.get(Parameter.HTTP2_RTT_SAMPLES));
		this.connection.sendSettings(initSettings(processor), (Long) processor.get(Parameter.HTTP2_SETTINGS_TIMEOUT));
		try {
			connection.createPassiveStream(1);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		this.HTTP2_INCOMING_FRAME_TIMEOUT = (Long) processor.get(Parameter.HTTP2_INCOMING_FRAME_TIMEOUT);
		this.stage = 0;
	}

	@Override
	public long process(ByteBuffer in) {
		if (stage < 24)
			while (in.hasRemaining() && stage < 24)
				if (PREFACE[stage++] != in.get())
					throw new RuntimeException("malformed http20 preface");
		return connection.unwrap(in) ? HTTP2_INCOMING_FRAME_TIMEOUT : NO_RESET;
	}

	@Override
	public void shutdown(Throwable closeReason) {
		connection.shutdown(closeReason);
	}
}
