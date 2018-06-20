package limax.codec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public final class ChannelSource implements Source {
	private final Codec sink;
	private final ReadableByteChannel channel;

	public ChannelSource(ReadableByteChannel channel, Codec sink) {
		this.channel = channel;
		this.sink = sink;
	}

	@Override
	public void flush() throws CodecException {
		byte[] buffer = new byte[4096];
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		try {
			while (channel.read(bb) != -1) {
				sink.update(buffer, 0, bb.position());
				bb.position(0);
			}
		} catch (IOException e) {
			throw new CodecException(e);
		}
		sink.flush();
	}
}
