package limax.codec;

public interface Pump {
	void render(Codec out) throws CodecException;
}
