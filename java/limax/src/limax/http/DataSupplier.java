package limax.http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@FunctionalInterface
public interface DataSupplier {
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

	static DataSupplier from(Path path) throws IOException {
		try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
			long size = fc.size();
			if (size <= Integer.MAX_VALUE)
				return from(fc.map(MapMode.READ_ONLY, 0, size));
			int nblocks = (int) (size / 0x7F000000);
			ByteBuffer[] datas = new ByteBuffer[nblocks + (size % 0x7F000000 != 0 ? 1 : 0)];
			long pos = 0;
			for (int i = 0; i < nblocks; i++) {
				datas[i] = fc.map(MapMode.READ_ONLY, pos, 0x7F000000);
				pos += 0x7F000000;
			}
			if ((size -= pos) > 0)
				datas[nblocks] = fc.map(MapMode.READ_ONLY, pos, size);
			return DataSupplier.from(datas);
		}
	}

	static DataSupplier from(File file) throws IOException {
		return from(file.toPath());
	}

	static DataSupplier from(InputStream in, int buffersize) {
		return new InputStreamSupplier(in, buffersize);
	}

	static DataSupplier from(ReadableByteChannel ch, int buffersize) {
		return new ChannelSupplier(ch, buffersize);
	}
}

interface DeterministicDataSupplier extends DataSupplier {
	long getLength();
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

class InputStreamSupplier implements DataSupplier {
	private final InputStream in;
	private final int buffersize;

	InputStreamSupplier(InputStream in, int buffersize) {
		this.in = in;
		this.buffersize = buffersize;
	}

	@Override
	public ByteBuffer get() throws Exception {
		byte[] data = new byte[buffersize];
		int n = in.read(data);
		return n == -1 ? null : ByteBuffer.wrap(data, 0, n);
	}

	@Override
	public void done(HttpExchange exchange) throws Exception {
		in.close();
	}
}

class ChannelSupplier implements DataSupplier {
	private final ReadableByteChannel ch;
	private final int buffersize;

	ChannelSupplier(ReadableByteChannel ch, int buffersize) {
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

	@Override
	public void done(HttpExchange exchange) throws Exception {
		ch.close();
	}
}
