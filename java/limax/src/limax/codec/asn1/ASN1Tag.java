package limax.codec.asn1;

import limax.codec.Codec;
import limax.codec.CodecException;

public final class ASN1Tag implements Comparable<ASN1Tag> {
	private final TagClass tagClass;
	private final long number;

	@Override
	public int hashCode() {
		return (int) (tagClass.hashCode() ^ number ^ (number >>> 32));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ASN1Tag) {
			ASN1Tag o = (ASN1Tag) obj;
			return tagClass.equals(o.tagClass) && number == o.number;
		}
		return false;
	}

	@Override
	public int compareTo(ASN1Tag o) {
		if (tagClass.ordinal() < o.tagClass.ordinal())
			return -1;
		if (tagClass.ordinal() > o.tagClass.ordinal())
			return 1;
		if (number < o.number)
			return -1;
		if (number > o.number)
			return 1;
		return 0;
	}

	public ASN1Tag(TagClass objectClass, long tag) {
		this.tagClass = objectClass;
		this.number = tag & Long.MAX_VALUE;
	}

	public String toString() {
		return tagClass.name() + "[" + number + "]";
	}

	public TagClass getTagClass() {
		return tagClass;
	}

	public long getNumber() {
		return number;
	}

	public int renderLength() {
		if (number < 31)
			return 1;
		int i, j = 1;
		for (i = 56; i > 0 && ((number >>> i) & 0x7f) == 0; i -= 7)
			;
		for (j = 0; i >= 0; i -= 7)
			j++;
		return j;
	}

	public void render(Codec c, boolean isPrimitive) throws CodecException {
		byte[] r = new byte[10];
		byte pc = (byte) (isPrimitive ? 0 : 0x20);
		if (number < 31) {
			r[0] = (byte) (tagClass.mask((byte) number) | pc);
			c.update(r, 0, 1);
			return;
		}
		int i, j = 0;
		r[j++] = (byte) (tagClass.mask((byte) 31) | pc);
		for (i = 56; i > 0 && ((number >>> i) & 0x7f) == 0; i -= 7)
			;
		for (j = 0; i > 0; i -= 7)
			r[j++] = (byte) (((number >>> i) & 0x7f) | 0x80);
		r[j++] = (byte) (number & 0x7f);
		c.update(r, 0, j);
	}

}
