package limax.codec.asn1;

import limax.codec.Codec;
import limax.codec.CodecException;
import limax.codec.Pump;

public final class ASN1HugeOctetString extends ASN1Object implements HugeObject {
	private static final byte[] UNKNOWNLENGTH = { (byte) 0x80, 0, 0 };
	private final ASN1Tag tag;
	private final Pump pump;
	private final Codec sink;

	public ASN1HugeOctetString(ASN1Tag tag, Pump pump) {
		this.tag = tag;
		this.pump = pump;
		this.sink = null;
	}

	public ASN1HugeOctetString(ASN1Tag tag, Codec sink) {
		this.tag = tag;
		this.pump = null;
		this.sink = sink;
	}

	public ASN1HugeOctetString(Pump pump) {
		this(ASN1OctetString.tag, pump);
	}

	public ASN1HugeOctetString(Codec sink) {
		this(ASN1OctetString.tag, sink);
	}

	@Override
	public void render(Codec c) throws CodecException {
		if (c instanceof EncodeDER)
			throw new BERException("ASN1HugeOctetString reject DER Output.");
		tag.render(c, false);
		c.update(UNKNOWNLENGTH, 0, 1);
		Codec thunk = new Chunk(c);
		pump.render(thunk);
		thunk.flush();
		c.update(UNKNOWNLENGTH, 1, 2);
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return ASN1OctetString.oid;
	}

	@Override
	public ASN1Tag getTag() {
		return tag;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public long renderLength() {
		throw new BERException("ASN1HugeOctetString reject DER Output.");
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		sink.update(data, off, len);
	}

	@Override
	public ASN1Object endorse() throws CodecException {
		sink.flush();
		return this;
	}

	private static class Chunk implements Codec {
		private final byte[] buffer = new byte[4096];
		private final Codec sink;
		private int pos = 0;

		private void output(byte[] data, int off, int len) throws CodecException {
			ASN1OctetString.tag.render(sink, true);
			byte[] blen = BERLength.render(len);
			sink.update(blen, 0, blen.length);
			sink.update(data, off, len);
		}

		public Chunk(Codec sink) {
			this.sink = sink;
		}

		@Override
		public void update(byte[] data, int off, int len) throws CodecException {
			if (len + pos < buffer.length) {
				System.arraycopy(data, off, buffer, pos, len);
				pos += len;
				return;
			}
			output(buffer, 0, pos);
			while (len >= buffer.length) {
				output(data, off, buffer.length);
				off += buffer.length;
				len -= buffer.length;
			}
			if ((pos = len) > 0)
				System.arraycopy(data, off, buffer, 0, pos);
		}

		@Override
		public void update(byte c) throws CodecException {
			update(new byte[] { c }, 0, 1);
		}

		@Override
		public void flush() throws CodecException {
			if (pos > 0) {
				output(buffer, 0, pos);
				pos = 0;
			}
		}
	}
}
