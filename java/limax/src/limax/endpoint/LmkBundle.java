package limax.endpoint;

import java.math.BigInteger;

import limax.codec.Base64Encode;
import limax.codec.Codec;
import limax.codec.CodecException;
import limax.codec.Decrypt;
import limax.codec.Encrypt;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.codec.SHA256;
import limax.codec.SinkOctets;

public class LmkBundle {
	private static final int MAGIC = 0x4c4d4b30;
	private final int magic;
	protected Octets chain;
	protected BigInteger n;
	protected BigInteger d;
	protected BigInteger p;
	protected BigInteger q;
	protected BigInteger exp1;
	protected BigInteger exp2;
	protected BigInteger coef;
	private Octets passphrase;

	private BigInteger encrypt(BigInteger message) {
		if (coef == null)
			return message.modPow(d, n);
		BigInteger c1 = message.mod(p).modPow(exp1, p);
		BigInteger c2 = message.mod(q).modPow(exp2, q);
		return c1.subtract(c2).multiply(coef).mod(p).multiply(q).add(c2);
	}

	protected LmkBundle(Octets lmkdata, Octets passphrase) throws CodecException, MarshalException {
		if ((this.magic = OctetsStream.wrap(lmkdata).unmarshal_int()) != MAGIC)
			throw new CodecException();
		OctetsStream os = new OctetsStream();
		Codec codec = new Decrypt(new SinkOctets(os), passphrase.getBytes());
		codec.update(lmkdata.array(), 4, lmkdata.size() - 4);
		codec.flush();
		this.chain = os.unmarshal_Octets();
		if (os.unmarshal_boolean()) {
			n = new BigInteger(os.unmarshal_bytes());
			d = new BigInteger(os.unmarshal_bytes());
		} else {
			p = new BigInteger(os.unmarshal_bytes());
			q = new BigInteger(os.unmarshal_bytes());
			exp1 = new BigInteger(os.unmarshal_bytes());
			exp2 = new BigInteger(os.unmarshal_bytes());
			coef = new BigInteger(os.unmarshal_bytes());
		}
		this.passphrase = new Octets(passphrase);
	}

	public static LmkBundle createInstance(Octets lmkdata, Octets passphrase) throws CodecException, MarshalException {
		return new LmkBundle(lmkdata, passphrase);
	}

	public Octets save(Octets passphrase) throws CodecException {
		OctetsStream os = new OctetsStream();
		os.marshal(chain);
		if (coef == null) {
			os.marshal(true);
			os.marshal(n.toByteArray());
			os.marshal(d.toByteArray());
		} else {
			os.marshal(false);
			os.marshal(p.toByteArray());
			os.marshal(q.toByteArray());
			os.marshal(exp1.toByteArray());
			os.marshal(exp2.toByteArray());
			os.marshal(coef.toByteArray());
		}
		byte[] data = os.getBytes();
		os.clear();
		os.marshal(magic);
		Codec codec = new Encrypt(new SinkOctets(os), passphrase.getBytes());
		codec.update(data, 0, data.length);
		codec.flush();
		return os;
	}

	public String sign(Octets message) {
		OctetsStream os = new OctetsStream();
		os.marshal(encrypt(new BigInteger(1, SHA256.digest(message.getBytes()))).toByteArray());
		os.marshal(passphrase);
		return "LMK0" + new String(Base64Encode.transform(os.getBytes()));
	}

	public String x509() {
		return new String(Base64Encode.transform(chain.getBytes()));
	}

	protected LmkBundle() {
		this.magic = MAGIC;
	}
}
