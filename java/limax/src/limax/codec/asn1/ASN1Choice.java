package limax.codec.asn1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import limax.codec.Codec;
import limax.codec.CodecException;

public class ASN1Choice extends ASN1Object {
	private List<ASN1Object> choices;
	private ASN1Object obj;

	public ASN1Choice(ASN1Object... objects) {
		this(Arrays.asList(objects));
	}

	public ASN1Choice(List<ASN1Object> objects) {
		this.choices = new ArrayList<>(objects);
	}

	public ASN1Choice(ASN1Object obj) {
		this.obj = obj;
	}

	@Override
	public boolean compareTag(ASN1Tag tag) {
		return obj != null ? obj.compareTag(tag) : choices.stream().anyMatch(obj -> obj.compareTag(tag));
	}

	@Override
	public String toString() {
		if (obj != null)
			return obj.toString();
		StringBuilder sb = new StringBuilder("ASN1Choice [");
		choices.forEach(obj -> sb.append(obj).append(","));
		sb.setCharAt(sb.length() - 1, ']');
		return sb.toString();
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		if (obj == null)
			throw new BERException("ASN1Choice must initialize object entity");
		return obj.getOID();
	}

	@Override
	public ASN1Tag getTag() {
		if (obj == null)
			throw new BERException("ASN1Choice must initialize object entity");
		return obj.getTag();
	}

	@Override
	public boolean isEmpty() {
		return obj != null ? obj.isEmpty() : true;
	}

	@Override
	public long renderLength() {
		return obj != null ? obj.renderLength() : 0;
	}

	@Override
	public void render(Codec c) throws CodecException {
		if (obj != null)
			obj.render(c);
	}

	@Override
	public void visit(Visitor v) {
		if (obj == null)
			super.visit(v);
		else
			obj.visit(v);
	}

	public void set(ASN1Object obj) {
		this.obj = obj;
	}

	public ASN1Object get() {
		return obj;
	}

	ASN1Object choose(ASN1Tag tag) {
		for (ASN1Object obj : choices)
			if (obj.compareTag(tag))
				return this.obj = obj;
		throw new BERException("choice don't except tag " + tag);
	}
}
