package limax.codec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public final class SinkChannel implements Codec {
	private final WritableByteChannel channel;

	public SinkChannel(WritableByteChannel channel) {
		this.channel = channel;
	}

	@Override
	public void update(byte c) throws CodecException {
		update(new byte[] { c }, 0, 1);
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		try {
			channel.write(ByteBuffer.wrap(data, off, len));
		} catch (IOException e) {
			throw new CodecException(e);
		}
	}

	@Override
	public void flush() throws CodecException {
	}

}
