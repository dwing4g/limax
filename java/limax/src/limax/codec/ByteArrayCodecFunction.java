package limax.codec;

public interface ByteArrayCodecFunction {
	byte[] apply(byte[] t) throws CodecException;
}
