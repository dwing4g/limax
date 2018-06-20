package limax.codec;

import java.util.Arrays;
import java.util.Collection;

public final class CodecCollection implements Codec {
	private final Collection<Codec> sinks;

	public CodecCollection(Codec... sinks) {
		this.sinks = Arrays.asList(sinks);
	}

	@Override
	public void update(byte c) throws CodecException {
		for (Codec sink : sinks)
			sink.update(c);
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		for (Codec sink : sinks)
			sink.update(data, off, len);
	}

	@Override
	public void flush() throws CodecException {
		for (Codec sink : sinks)
			sink.flush();
	}

}
