package limax.codec.asn1;

import java.util.ArrayList;
import java.util.Collection;

import limax.codec.Codec;
import limax.codec.CodecException;

public class DecodeBER implements Codec {
	private static final byte[] ZERO = { 0 };
	private final Collection<ASN1Object> objects;
	private final BERObject berObject;
	private BERStage stage = BERStage.INIT;
	private DecodeBER inner;
	private long counter;

	public DecodeBER(ASN1Object infrastructureObject) {
		this(new BERObjectRoot(infrastructureObject));
	}

	public DecodeBER(Collection<ASN1Object> objects) {
		this.berObject = new BERObject();
		this.objects = objects;
	}

	DecodeBER(BERObject berObject) {
		this.berObject = berObject;
		this.objects = new ArrayList<ASN1Object>();
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		for (int i = off, end = off + len; i < end; i++) {
			if (stage == BERStage.INIT) {
				stage = berObject.init();
				counter = 0;
			}
			if (stage == BERStage.IDENTIFIER) {
				stage = berObject.identifierNext(data[i]);
				continue;
			}
			if (stage == BERStage.LENGTH) {
				stage = berObject.lengthNext(data[i]);
				if (stage == BERStage.LENGTH)
					continue;
				inner = berObject.getReady();
				if (stage != BERStage.END)
					continue;
			}
			if (stage == BERStage.DEFINITE_CONTENT) {
				long length = berObject.getLength();
				int remain = end - i;
				if (remain + counter < length) {
					if (inner != null)
						inner.update(data, i, remain);
					else
						berObject.update(data, i, remain);
					counter += remain;
					i += remain - 1;
				} else {
					if (inner != null) {
						inner.update(data, i, (int) (length - counter));
						if (inner.stage == BERStage.INIT)
							berObject.update(inner.objects);
						else
							throw new BERException("error length");
					} else
						berObject.update(data, i, (int) (length - counter));
					i += length - counter - 1;
					stage = BERStage.END;
				}
			}
			if (stage == BERStage.INDEFINITE_CONTENT) {
				int mark = i;
				for (; i < end; i++) {
					if (data[i] == 0) {
						++counter;
						break;
					} else {
						if (counter == 1) {
							if (inner != null)
								inner.update(ZERO, 0, 1);
							else
								berObject.update(ZERO, 0, 1);
						}
						counter = 0;
					}
				}
				if (counter == 2) {
					if (inner != null) {
						if (inner.stage == BERStage.INIT) {
							berObject.update(inner.objects);
							stage = BERStage.END;
						} else {
							inner.update(ZERO, 0, 1);
							counter = 1;
						}
					} else {
						stage = BERStage.END;
					}
				} else {
					if (inner != null)
						inner.update(data, mark, i - mark);
					else
						berObject.update(data, mark, i - mark);
				}
			}
			if (stage == BERStage.END) {
				objects.add(berObject.endorse());
				stage = BERStage.INIT;
			}
		}
	}

	@Override
	public void update(byte c) throws CodecException {
		update(new byte[] { c }, 0, 1);
	}

	@Override
	public void flush() {
		if (stage != BERStage.INIT)
			throw new BERException("insufficent data. " + stage.name());
	}

	public static ASN1Object decode(byte[] data) throws CodecException {
		ASN1Any any = new ASN1Any();
		Codec codec = new DecodeBER(any);
		codec.update(data, 0, data.length);
		codec.flush();
		return any.get();
	}
}
