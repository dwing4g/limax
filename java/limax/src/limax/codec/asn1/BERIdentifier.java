package limax.codec.asn1;

class BERIdentifier {
	private int nbytes;
	private byte identifier;
	private long number;

	public BERIdentifier() {
		this.nbytes = 0;
	}

	public BERStage next(byte in) {
		if (nbytes++ == 0) {
			identifier = in;
			number = in & 0x1f;
			return (identifier & 0x1f) == 0x1f ? BERStage.IDENTIFIER : BERStage.LENGTH;
		}
		if (nbytes == 10)
			throw new BERException("identifier too long");
		number = (number << 7) | (in & 0x7f);
		return (number & 0x80) != 0 ? BERStage.IDENTIFIER : BERStage.LENGTH;
	}

	public boolean isPrimitive() {
		return (identifier & 0x20) == 0;
	}

	public boolean isConstructed() {
		return (identifier & 0x20) != 0;
	}

	public byte getIdentifier() {
		return identifier;
	}

	public ASN1Tag getTag() {
		return new ASN1Tag(TagClass.unmask(identifier), number);
	}
}