package limax.node.js.modules;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;

import limax.node.js.Buffer.Data;
import limax.node.js.Buffer;
import limax.node.js.EventLoop;
import limax.node.js.Module;

public final class Zlib implements Module {
	public final int Z_NO_FLUSH = Deflater.NO_FLUSH;
	public final int Z_PARTIAL_FLUSH = Deflater.SYNC_FLUSH;
	public final int Z_SYNC_FLUSH = Deflater.SYNC_FLUSH;
	public final int Z_FULL_FLUSH = Deflater.FULL_FLUSH;
	public final int Z_FINISH = Deflater.FULL_FLUSH;
	public final int Z_BLOCK = Deflater.SYNC_FLUSH;
	public final int Z_TREES = Deflater.SYNC_FLUSH;

	public final int Z_NO_COMPRESSION = Deflater.NO_COMPRESSION;
	public final int Z_BEST_SPEED = Deflater.BEST_SPEED;
	public final int Z_BEST_COMPRESSION = Deflater.BEST_COMPRESSION;
	public final int Z_DEFAULT_COMPRESSION = Deflater.DEFAULT_COMPRESSION;

	public final int Z_FILTERED = Deflater.FILTERED;
	public final int Z_HUFFMAN_ONLY = Deflater.HUFFMAN_ONLY;
	public final int Z_RLE = Deflater.DEFAULT_STRATEGY;
	public final int Z_FIXED = Deflater.DEFAULT_STRATEGY;
	public final int Z_DEFAULT_STRATEGY = Deflater.DEFAULT_STRATEGY;

	public final int Z_DEFLATED = Deflater.DEFLATED;

	private final EventLoop eventLoop;

	public Zlib(EventLoop eventLoop) {
		this.eventLoop = eventLoop;
	}

	private static boolean isGzipFormat(Buffer buf) {
		ByteBuffer bb = buf.toByteBuffer();
		return bb.get() == (byte) GZIPInputStream.GZIP_MAGIC && bb.get() == (byte) (GZIPInputStream.GZIP_MAGIC >> 8);
	}

	private static Buffer gather(ByteArrayConsumer bac, int eof) throws Exception {
		List<Data> list = new ArrayList<>();
		int total = 0;
		byte[] b = new byte[4096];
		for (int n; (n = bac.apply(b)) > eof; total += n) {
			list.add(new Data(b, 0, n));
			b = new byte[4096];
		}
		ByteBuffer bb = ByteBuffer.allocateDirect(total);
		list.forEach(d -> bb.put(d.buf, d.off, d.len));
		bb.flip();
		return new Buffer(bb);
	}

	private static Deflater createDeflater(boolean nowrap, Integer level, Integer strategy, Buffer dictionary) {
		if (level == null || level < Deflater.NO_COMPRESSION || level > Deflater.BEST_COMPRESSION)
			level = Deflater.DEFAULT_COMPRESSION;
		Deflater deflater = new Deflater(level, nowrap);
		if (strategy != null && (strategy == Deflater.FILTERED || strategy == Deflater.HUFFMAN_ONLY))
			deflater.setStrategy(strategy);
		if (dictionary != null) {
			Data data = dictionary.toData();
			deflater.setDictionary(data.buf, data.off, data.len);
		}
		return deflater;
	}

	private static Inflater createInflater(boolean nowrap, ByteBuffer dictionary) {
		Inflater inflater = new Inflater(nowrap);
		if (dictionary != null) {
			Data data = new Data(dictionary);
			inflater.setDictionary(data.buf, data.off, data.len);
		}
		return inflater;
	}

	private interface Transform {
		Buffer write(Buffer buf) throws Exception;

		Buffer flush() throws Exception;

		default Buffer flush(int kind) throws Exception {
			return null;
		}
	}

	@FunctionalInterface
	private interface TransformFactory {
		Transform get() throws Exception;
	}

	private class Deflate implements Transform {
		private final int flush;
		private final int finishFlush;
		private final Deflater deflater;

		Deflate(boolean nowrap, int flush, int finishFlush, Integer level, Integer strategy, Buffer dictionary) {
			this.flush = flush;
			this.finishFlush = finishFlush;
			this.deflater = createDeflater(nowrap, level, strategy, dictionary);
		}

		@Override
		public Buffer write(Buffer buf) throws Exception {
			Data data = buf.toData();
			deflater.setInput(data.buf, data.off, data.len);
			return gather(b -> deflater.deflate(b, 0, b.length, flush), 0);
		}

		@Override
		public Buffer flush() throws Exception {
			try {
				deflater.finish();
				return gather(b -> deflater.deflate(b, 0, b.length, finishFlush), 0);
			} finally {
				deflater.end();
			}
		}

		@Override
		public Buffer flush(int kind) throws Exception {
			return gather(b -> deflater.deflate(b, 0, b.length, kind), 0);
		}
	}

	private class Gunzip implements Transform {
		private final PipedOutputStream ppos;
		private final Future<?> future;
		private final LinkedBlockingQueue<Data> queue = new LinkedBlockingQueue<>();
		private volatile Exception exception;

		Gunzip() throws Exception {
			PipedInputStream ppis = new PipedInputStream(this.ppos = new PipedOutputStream());
			this.future = eventLoop.submit(() -> {
				try (GZIPInputStream gzis = new GZIPInputStream(ppis)) {
					byte[] buffer = new byte[4096];
					for (int nread; (nread = gzis.read(buffer)) != -1;) {
						queue.add(new Data(buffer, 0, nread));
						buffer = new byte[4096];
					}
				} catch (Exception e) {
					exception = e;
				}
			});
		}

		private Buffer gather() throws Exception {
			if (exception != null)
				throw exception;
			List<Data> list = new ArrayList<>();
			int length = 0;
			for (Data data; (data = queue.poll()) != null;) {
				list.add(data);
				length += data.len;
			}
			ByteBuffer bb = ByteBuffer.allocateDirect(length);
			list.forEach(d -> bb.put(d.buf, d.off, d.len));
			bb.flip();
			return new Buffer(bb);
		}

		@Override
		public Buffer write(Buffer buf) throws Exception {
			Data data = buf.toData();
			ppos.write(data.buf, data.off, data.len);
			return gather();
		}

		@Override
		public Buffer flush() throws Exception {
			ppos.close();
			future.get();
			return gather();
		}
	}

	private class Gzip implements Transform {
		private final ByteArrayOutputStream baos;
		private final GZIPOutputStream gzos;

		Gzip(Integer flush) throws Exception {
			this.gzos = new GZIPOutputStream(this.baos = new ByteArrayOutputStream(), flush == Deflater.SYNC_FLUSH);
		}

		@Override
		public Buffer write(Buffer buf) throws Exception {
			try {
				Data data = buf.toData();
				gzos.write(data.buf, data.off, data.len);
				return new Buffer(baos.toByteArray());
			} finally {
				baos.reset();
			}
		}

		@Override
		public Buffer flush() throws Exception {
			gzos.finish();
			return new Buffer(baos.toByteArray());
		}
	}

	private class Inflate implements Transform {
		private final Inflater inflater;

		Inflate(boolean nowrap, ByteBuffer dictionary) {
			this.inflater = createInflater(nowrap, dictionary);
		}

		@Override
		public Buffer write(Buffer buf) throws Exception {
			Data data = buf.toData();
			inflater.setInput(data.buf, data.off, data.len);
			return gather(b -> inflater.inflate(b), 0);
		}

		@Override
		public Buffer flush() throws Exception {
			inflater.end();
			return null;
		}
	}

	private class Unzip implements Transform {
		private Transform inner;
		private Byte b0;

		@Override
		public Buffer write(Buffer buf) throws Exception {
			ByteBuffer bb = buf.toByteBuffer();
			if (inner == null) {
				switch (bb.remaining()) {
				case 1:
					if (b0 == null)
						b0 = bb.duplicate().get();
					else
						break;
				case 0:
					return null;
				}
				ByteBuffer ib;
				if (b0 == null) {
					ib = bb;
				} else {
					ib = ByteBuffer.allocate(bb.remaining() + 1);
					ib.put(b0);
					ib.put(bb.duplicate());
					ib.flip();
				}
				inner = isGzipFormat(new Buffer(ib)) ? new Gunzip() : new Inflate(false, null);
			}
			return inner.write(buf);
		}

		@Override
		public Buffer flush() throws Exception {
			return inner != null ? inner.flush() : null;
		}
	}

	public class Impl {
		private final TransformFactory factory;
		private Transform transform;

		Impl(TransformFactory factory) throws Exception {
			this.factory = factory;
			reset();
		}

		public void write(Buffer buf, Object callback) {
			eventLoop.execute(callback, r -> r.add(transform.write(buf)));
		}

		public void flush(Object callback) {
			eventLoop.execute(callback, r -> r.add(transform.flush()));
		}

		public void flush(Integer kind, Object callback) {
			eventLoop.execute(callback, r -> r.add(transform.flush(kind)));
		}

		public void params(Object level, Object strategy, Object callback) {
			if (transform instanceof Deflate) {
				eventLoop.execute(callback, r -> {
					Deflate d = (Deflate) transform;
					d.deflater.setLevel((Integer) level);
					d.deflater.setStrategy((Integer) strategy);
				});
			}
		}

		public void reset() throws Exception {
			transform = factory.get();
		}
	}

	public Impl createDeflate(boolean nowrap, int flush, int finishFlush, Integer level, Integer strategy,
			Buffer dictionary) throws Exception {
		return new Impl(() -> new Deflate(nowrap, flush, finishFlush, level, strategy, dictionary));
	}

	public Impl createGunzip() throws Exception {
		return new Impl(() -> new Gunzip());
	}

	public Impl createGzip(Integer flush) throws Exception {
		return new Impl(() -> new Gzip(flush));
	}

	public Impl createInflate(boolean nowrap, ByteBuffer dictionary) throws Exception {
		return new Impl(() -> new Inflate(nowrap, dictionary));
	}

	public Impl createUnzip() throws Exception {
		return new Impl(() -> new Unzip());
	}

	public void deflate(Buffer buf, boolean nowrap, Integer level, Integer strategy, Buffer dictionary,
			Object callback) {
		eventLoop.execute(callback, r -> r.add(deflateSync(buf, nowrap, level, strategy, dictionary)));
	}

	public Buffer deflateSync(Buffer buf, boolean nowrap, Integer level, Integer strategy, Buffer dictionary)
			throws Exception {
		Deflater deflater = createDeflater(nowrap, level, strategy, dictionary);
		Data data = buf.toData();
		deflater.setInput(data.buf, data.off, data.len);
		deflater.finish();
		return gather(b -> deflater.deflate(b), 0);
	}

	public void gunzip(Buffer buf, Object callback) {
		eventLoop.execute(callback, r -> r.add(gunzipSync(buf)));
	}

	@FunctionalInterface
	private interface ByteArrayConsumer {
		int apply(byte[] buf) throws Exception;
	}

	public Buffer gunzipSync(Buffer buf) throws Exception {
		Data data = buf.toData();
		try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(data.buf, data.off, data.len))) {
			return gather(b -> gzis.read(b), -1);
		}
	}

	public void gzip(Buffer buf, Object callback) {
		eventLoop.execute(callback, r -> r.add(gzipSync(buf)));
	}

	public Buffer gzipSync(Buffer buf) throws Exception {
		Data data = buf.toData();
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
			gzos.write(data.buf, data.off, data.len);
			gzos.finish();
			return new Buffer(baos.toByteArray());
		}
	}

	public void inflate(Buffer buf, boolean nowrap, ByteBuffer dictionary, Object callback) {
		eventLoop.execute(callback, r -> r.add(inflateSync(buf, nowrap, dictionary)));
	}

	public Buffer inflateSync(Buffer buf, boolean nowrap, ByteBuffer dictionary) throws Exception {
		Inflater inflater = createInflater(nowrap, dictionary);
		Data data = buf.toData();
		inflater.setInput(data.buf, data.off, data.len);
		return gather(b -> inflater.inflate(b), 0);
	}

	public void unzip(Buffer buf, Object callback) {
		eventLoop.execute(callback, r -> r.add(unzipSync(buf)));
	}

	public Buffer unzipSync(Buffer buf) throws Exception {
		return isGzipFormat(buf) ? gunzipSync(buf) : inflateSync(buf, false, null);
	}
}
