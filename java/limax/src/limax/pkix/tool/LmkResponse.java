package limax.pkix.tool;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;

import limax.codec.JSON;
import limax.codec.Octets;
import limax.codec.RFC2822Address;
import limax.codec.asn1.ASN1Object;
import limax.codec.asn1.ASN1OctetString;
import limax.codec.asn1.ASN1PrimitiveObject;
import limax.codec.asn1.ASN1Sequence;
import limax.codec.asn1.ASN1Tag;
import limax.codec.asn1.DecodeBER;
import limax.codec.asn1.TagClass;
import limax.pkix.GeneralName;

class LmkResponse {
	private static final ASN1Tag tagEmail = new ASN1Tag(TagClass.ContextSpecific, 1);
	private final RFC2822Address email;
	private final X500Principal subject;
	private final Collection<GeneralName> subjectAltNames;
	private final Octets passphrase;

	private LmkResponse(RFC2822Address email, X500Principal subject, Collection<GeneralName> subjectAltNames,
			String passphrase) {
		this.email = email;
		this.subject = subject;
		this.subjectAltNames = subjectAltNames;
		this.passphrase = Octets.wrap(passphrase.getBytes(StandardCharsets.UTF_8));
	}

	private static String toString(JSON json) {
		return json.isString() ? json.toString() : null;
	}

	private static String toStringLowerCase(JSON json) {
		return json.isString() ? json.toString() : null;
	}

	static LmkResponse buildResponse(X509Certificate cert, String query, Supplier<String> lmkDomainSupplier,
			int constraintNameLength, Supplier<Boolean> signedByCA) {
		try {
			boolean isLmkCert = cert.getExtendedKeyUsage().contains("1.3.6.1.5.5.7.3.4");
			JSON json = JSON.parse(URLDecoder.decode(query, "utf8"));
			String p = toString(json.get("p"));
			if (p == null)
				return null;
			String u = toStringLowerCase(json.get("u"));
			RFC2822Address email;
			X500Principal subject;
			Collection<GeneralName> subjectAltNames;
			if (u == null) {
				long notBefore = cert.getNotBefore().getTime();
				long notAfter = cert.getNotAfter().getTime();
				if ((notAfter - notBefore) / 2 + notBefore >= System.currentTimeMillis())
					return null;
				if (!isLmkCert || !signedByCA.get())
					return null;
				subject = cert.getSubjectX500Principal();
				ASN1Sequence seq = (ASN1Sequence) DecodeBER
						.decode(((ASN1OctetString) DecodeBER.decode(cert.getExtensionValue("2.5.29.17"))).get());
				subjectAltNames = new ArrayList<>();
				email = null;
				for (int i = 0; i < seq.size(); i++) {
					ASN1Object obj = seq.get(i);
					if (obj instanceof ASN1PrimitiveObject) {
						ASN1PrimitiveObject item = (ASN1PrimitiveObject) obj;
						if (item.getTag().equals(tagEmail))
							email = new RFC2822Address(new String(item.getData(), StandardCharsets.ISO_8859_1));
					}
					subjectAltNames.add(GeneralName.create(obj));
				}
			} else {
				if (isLmkCert)
					return null;
				if (u.isEmpty() || u.length() > constraintNameLength)
					return null;
				String d = toStringLowerCase(json.get("d"));
				if (d == null)
					d = lmkDomainSupplier.get();
				if (d == null)
					return null;
				email = new RFC2822Address(u, d);
				subject = new X500Principal(
						Stream.concat(Stream.of("cn=" + u), Arrays.stream(d.split("\\.")).map(dc -> "dc=" + dc))
								.collect(Collectors.joining(",")));
				subjectAltNames = Arrays.asList(GeneralName.createRFC822Name(email), GeneralName.createDirectoryName(
						"email=" + Rdn.escapeValue(email.toString()) + "," + cert.getSubjectX500Principal()));
			}
			if (email != null)
				return new LmkResponse(email, subject, subjectAltNames, p);
		} catch (Exception e) {
		}
		return null;
	}

	public String toFileNameString() {
		return email.getUsername() + "@" + email.getDomain() + ".lmk";
	}

	X500Principal getSubject() {
		return subject;
	}

	Collection<GeneralName> getSubjectAltNames(X500Principal sponsor) {
		return subjectAltNames;
	}

	Octets getPassphrase() {
		return passphrase;
	}
}
