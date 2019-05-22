package limax.http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import limax.http.AbstractHttpExchange.CustomDataSupplier;

@FunctionalInterface
public interface DataSupplier {
	interface Done {
		void done(HttpExchange exchange) throws Exception;
	}

	ByteBuffer get() throws Exception;

	default void done(HttpExchange exchange) throws Exception {
	}

	static DataSupplier from(ByteBuffer[] datas) {
		return new MultipleByteBufferDataSupplier(datas);
	}

	static DataSupplier from(ByteBuffer data) {
		return new SingleByteBufferDataSupplier(data);
	}

	static DataSupplier from(byte[] data) {
		return from(ByteBuffer.wrap(data));
	}

	static DataSupplier from(String text, Charset charset) {
		return from(charset.encode(text));
	}

	static DataSupplier from(FileChannel fc, long begin, long end) throws IOException {
		long pos = begin;
		long size = end - begin;
		if (size <= Integer.MAX_VALUE)
			return from(fc.map(MapMode.READ_ONLY, pos, size));
		int nblocks = (int) (size / 0x7F000000);
		ByteBuffer[] datas = new ByteBuffer[nblocks + (size % 0x7F000000 != 0 ? 1 : 0)];
		for (int i = 0; i < nblocks; i++) {
			datas[i] = fc.map(MapMode.READ_ONLY, pos, 0x7F000000);
			pos += 0x7F000000;
		}
		if ((size -= pos) > 0)
			datas[nblocks] = fc.map(MapMode.READ_ONLY, pos, size);
		return from(datas);
	}

	static DataSupplier from(Path path) throws IOException {
		try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
			return from(fc, 0, fc.size());
		}
	}

	static DataSupplier from(File file) throws IOException {
		return from(file.toPath());
	}

	static DataSupplier from(InputStream in, int buffersize) {
		return new InputStreamDataSupplier(in, buffersize);
	}

	static DataSupplier from(ReadableByteChannel ch, int buffersize) {
		return new ChannelDataSupplier(ch, buffersize);
	}

	static DataSupplier from(DataSupplier supplier, Done done) {
		return new DoneDecorateDataSupplier(supplier, done);
	}

	static DataSupplier from(Consumer<CustomSender> consumer, Runnable onSendReady, Runnable onClose) {
		return new CustomDataSupplier(consumer, onSendReady, onClose);
	}

	static DataSupplier from(HttpExchange exchange, BiConsumer<String, ServerSentEvents> consumer, Runnable onSendReady,
			Runnable onClose) {
		String lastEventId = exchange.getRequestHeaders().getFirst("last-event-id");
		exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		return DataSupplier.from(sender -> {
			consumer.accept(lastEventId, new ServerSentEvents() {
				@Override
				public void emit(String data) {
					emit(null, null, data);
				}

				@Override
				public void emit(String event, String id, String data) {
					StringBuilder sb = new StringBuilder();
					if (event != null)
						sb.append("event: ").append(event).append("\n");
					if (id != null)
						sb.append("id: ").append(id).append("\n");
					sb.append("data: ").append(data).append("\n\n");
					sender.send(StandardCharsets.UTF_8.encode(sb.toString()));
				}

				@Override
				public void emit(long milliseconds) {
					sender.send(StandardCharsets.UTF_8.encode("retry: " + milliseconds + "\n"));
				}

				@Override
				public void done() {
					sender.sendFinal(0);
				}
			});
		}, onSendReady, onClose);
	}

	static DataSupplier async() {
		return new AsyncDataSupplier();
	}
}

interface DeterministicDataSupplier extends DataSupplier {
	long getLength();
}

class DoneDecorateDataSupplier implements DataSupplier {
	private final DataSupplier supplier;
	private final Done done;

	DoneDecorateDataSupplier(DataSupplier supplier, Done done) {
		if (supplier instanceof CustomDataSupplier || supplier instanceof DoneDecorateDataSupplier)
			throw new UnsupportedOperationException();
		this.supplier = supplier;
		this.done = done;
	}

	@Override
	public ByteBuffer get() throws Exception {
		return supplier.get();
	}

	@Override
	public void done(HttpExchange exchange) throws Exception {
		done.done(exchange);
	}
}

class AsyncDataSupplier implements DataSupplier {
	@Override
	public ByteBuffer get() throws Exception {
		throw new UnsupportedOperationException();
	}
}

class SingleByteBufferDataSupplier implements DeterministicDataSupplier {
	private volatile ByteBuffer bb;

	public SingleByteBufferDataSupplier(ByteBuffer bb) {
		this.bb = bb;
	}

	@Override
	public ByteBuffer get() throws Exception {
		ByteBuffer tmp = bb;
		bb = null;
		return tmp;
	}

	public long getLength() {
		return bb.remaining();
	}
}

class MultipleByteBufferDataSupplier implements DeterministicDataSupplier {
	private final ByteBuffer[] bbs;
	private int index = 0;

	public MultipleByteBufferDataSupplier(ByteBuffer[] bbs) {
		this.bbs = bbs;
	}

	@Override
	public ByteBuffer get() throws Exception {
		return index == bbs.length ? null : bbs[index++];
	}

	public long getLength() {
		long remaining = 0;
		for (ByteBuffer bb : bbs)
			remaining += bb.remaining();
		return remaining;
	}
}

class InputStreamDataSupplier implements DataSupplier {
	private final InputStream in;
	private final int buffersize;

	InputStreamDataSupplier(InputStream in, int buffersize) {
		this.in = in;
		this.buffersize = buffersize;
	}

	@Override
	public ByteBuffer get() throws Exception {
		byte[] data = new byte[buffersize];
		int n = in.read(data);
		return n == -1 ? null : ByteBuffer.wrap(data, 0, n);
	}
}

class ChannelDataSupplier implements DataSupplier {
	private final ReadableByteChannel ch;
	private final int buffersize;

	ChannelDataSupplier(ReadableByteChannel ch, int buffersize) {
		this.ch = ch;
		this.buffersize = buffersize;
	}

	@Override
	public ByteBuffer get() throws Exception {
		ByteBuffer bb = ByteBuffer.allocate(buffersize);
		if (ch.read(bb) == -1)
			return null;
		bb.flip();
		return bb;
	}
}
