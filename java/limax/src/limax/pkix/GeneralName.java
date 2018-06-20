package limax.pkix;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

import javax.security.auth.x500.X500Principal;

import limax.codec.RFC2822Address;
import limax.codec.asn1.ASN1ConstructedObject;
import limax.codec.asn1.ASN1IA5String;
import limax.codec.asn1.ASN1Object;
import limax.codec.asn1.ASN1ObjectIdentifier;
import limax.codec.asn1.ASN1OctetString;
import limax.codec.asn1.ASN1RawData;
import limax.codec.asn1.ASN1Tag;
import limax.codec.asn1.TagClass;

public class GeneralName {
	public enum Type {
		otherName, rfc822Name, dNSName, x400Address, directoryName, ediPartyName, uniformResourceIdentifier, iPAddress, registeredID;
		private final ASN1Tag tag = new ASN1Tag(TagClass.ContextSpecific, ordinal());
	}

	private final ASN1Object obj;

	private GeneralName(ASN1Object obj) {
		this.obj = obj;
	}

	ASN1Object get() {
		return obj;
	}

	public static GeneralName createRFC822Name(RFC2822Address email) {
		return new GeneralName(new ASN1IA5String(Type.rfc822Name.tag, email.toString()));
	}

	public static GeneralName createRFC822Name(String email) {
		return createRFC822Name(new RFC2822Address(email));
	}

	public static GeneralName createDNSName(String domain) {
		return new GeneralName(new ASN1IA5String(Type.dNSName.tag, IDN.toASCII(domain)));
	}

	public static GeneralName createDirectoryName(X500Principal dn) {
		return new GeneralName(new ASN1ConstructedObject(Type.directoryName.tag, new ASN1RawData(dn.getEncoded())));
	}

	public static GeneralName createDirectoryName(String dn) {
		return createDirectoryName(new X500Principal(dn));
	}

	public static GeneralName createUniformResourceIdentifier(URI uri) {
		return new GeneralName(new ASN1IA5String(Type.uniformResourceIdentifier.tag, uri.normalize().toString()));
	}

	public static GeneralName createUniformResourceIdentifier(String uri) throws URISyntaxException {
		return createUniformResourceIdentifier(new URI(uri));
	}

	public static GeneralName createIPAddress(InetAddress ip) {
		return new GeneralName(new ASN1OctetString(Type.iPAddress.tag, ip.getAddress()));
	}

	public static GeneralName createRegisteredID(String oid) {
		return new GeneralName(new ASN1ObjectIdentifier(Type.registeredID.tag, oid));
	}

	public static GeneralName createRegisteredID(ASN1ObjectIdentifier oid) {
		return createRegisteredID(oid.get());
	}

	public static GeneralName create(ASN1Object obj) {
		return new GeneralName(obj);
	}
}
