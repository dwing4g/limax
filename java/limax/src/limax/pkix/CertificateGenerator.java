package limax.pkix;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.security.auth.x500.X500Principal;

import limax.codec.SHA1;
import limax.codec.asn1.ASN1BitString;
import limax.codec.asn1.ASN1Boolean;
import limax.codec.asn1.ASN1ConstructedObject;
import limax.codec.asn1.ASN1GeneralizedTime;
import limax.codec.asn1.ASN1Integer;
import limax.codec.asn1.ASN1Object;
import limax.codec.asn1.ASN1ObjectIdentifier;
import limax.codec.asn1.ASN1OctetString;
import limax.codec.asn1.ASN1RawData;
import limax.codec.asn1.ASN1Sequence;
import limax.codec.asn1.ASN1Tag;
import limax.codec.asn1.DecodeBER;
import limax.codec.asn1.TagClass;
import limax.util.Helper;
import limax.util.SecurityUtils.PublicKeyAlgorithm;

class CertificateGenerator {
	private static final ASN1Tag CtxTag0 = new ASN1Tag(TagClass.ContextSpecific, 0);
	private static final ASN1Tag CtxTag3 = new ASN1Tag(TagClass.ContextSpecific, 3);
	private static final ASN1Object V3 = new ASN1ConstructedObject(CtxTag0, new ASN1Integer(BigInteger.valueOf(2)));
	private static final ASN1ObjectIdentifier OID_BasicConstraints = new ASN1ObjectIdentifier("2.5.29.19");
	private static final ASN1ObjectIdentifier OID_CertificatePolicies = new ASN1ObjectIdentifier("2.5.29.32");
	private static final ASN1ObjectIdentifier OID_AuthorityInfoAccess = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.1");
	private static final ASN1ObjectIdentifier OID_AccessDescriptionOCSP = new ASN1ObjectIdentifier(
			"1.3.6.1.5.5.7.48.1");
	private static final ASN1ObjectIdentifier OID_AuthorityKeyIdentifier = new ASN1ObjectIdentifier("2.5.29.35");
	private static final ASN1ObjectIdentifier OID_IssuerAltName = new ASN1ObjectIdentifier("2.5.29.18");
	private static final ASN1ObjectIdentifier OID_SubjectKeyIdentifier = new ASN1ObjectIdentifier("2.5.29.14");
	private static final ASN1ObjectIdentifier OID_SubjectAltName = new ASN1ObjectIdentifier("2.5.29.17");
	private static final ASN1ObjectIdentifier OID_KeyUsage = new ASN1ObjectIdentifier("2.5.29.15");
	private static final ASN1ObjectIdentifier OID_ExtKeyUsage = new ASN1ObjectIdentifier("2.5.29.37");
	private static final ASN1ObjectIdentifier OID_CRLDistributionPoints = new ASN1ObjectIdentifier("2.5.29.31");
	private static final Collection<String> basicExtensionOIDs = Arrays.asList(OID_AuthorityKeyIdentifier.get(),
			OID_IssuerAltName.get(), OID_CRLDistributionPoints.get(), OID_SubjectKeyIdentifier.get());
	private static final Set<String> supportedExtensionOIDS = new HashSet<>(Arrays.asList(OID_BasicConstraints.get(),
			OID_CertificatePolicies.get(), OID_AuthorityInfoAccess.get(), OID_AccessDescriptionOCSP.get(),
			OID_AuthorityKeyIdentifier.get(), OID_IssuerAltName.get(), OID_SubjectKeyIdentifier.get(),
			OID_SubjectAltName.get(), OID_KeyUsage.get(), OID_ExtKeyUsage.get(), OID_CRLDistributionPoints.get()));
	private final PrivateKey privateKey;
	private final PublicKey publicKey;
	private final X509Certificate cacert;
	private final int signsize;
	private Instant notBefore;
	private Instant notAfter;
	private X500Principal subject;
	private ASN1Sequence basicConstraints;
	private URI ocspURI;
	private URI cRLDP;
	private X509CertificatePolicyCPS cps;
	private final ASN1Sequence subjectAltName = new ASN1Sequence();
	private final BitSet keyUsage = new BitSet();
	private final ASN1Sequence extKeyUsage = new ASN1Sequence();
	private Collection<ASN1Sequence> additionalExtensions;

	CertificateGenerator(PrivateKey privateKey, X509Certificate cacert, PublicKey publicKey, int signsize)
			throws Exception {
		this.privateKey = privateKey;
		this.publicKey = publicKey;
		this.cacert = cacert;
		this.signsize = signsize;
	}

	CertificateGenerator(KeyPair keyPair, int signsize) throws Exception {
		this(keyPair.getPrivate(), null, keyPair.getPublic(), signsize);
		setCA();
	}

	void setValidity(Instant notBefore, Instant notAfter) {
		if (notBefore.isAfter(notAfter))
			throw new IllegalArgumentException("notBefore after notAfter");
		this.notBefore = notBefore;
		this.notAfter = notAfter;
	}

	void setSubject(X500Principal subject) {
		this.subject = subject;
	}

	void addSubjectAltName(GeneralName name) {
		subjectAltName.addChild(name.get());
	}

	void setBasicConstraints(int pathLen) {
		if (pathLen < 0)
			throw new IllegalArgumentException("pathLen = " + pathLen + "[<0]");
		setCA();
		this.basicConstraints.addChild(new ASN1Integer(BigInteger.valueOf(pathLen)));
	}

	private void setCA() {
		this.basicConstraints = new ASN1Sequence(new ASN1Boolean(true));
	}

	void setOcspURI(URI ocspURI) {
		this.ocspURI = ocspURI;
	}

	void setCRLDP(URI cRLDP) {
		this.cRLDP = cRLDP;
	}

	void setCertificatePolicyCPS(X509CertificatePolicyCPS cps) {
		this.cps = cps;
	}

	void setKeyUsage(EnumSet<KeyUsage> keyUsages) {
		keyUsages.forEach(u -> u.update(keyUsage));
	}

	void setExtKeyUsage(EnumSet<ExtKeyUsage> extKeyUsages) {
		extKeyUsages.forEach(u -> extKeyUsage.addChild(u.oid()));
	}

	void setAdditionalExtensions(Collection<X509Extension> additionalX509Extensions) {
		additionalExtensions = additionalX509Extensions.stream()
				.filter(e -> !supportedExtensionOIDS.contains(e.getOID())).map(X509Extension::assemble)
				.collect(Collectors.toList());
	}

	private static ASN1Integer generateSerialNumber() {
		byte[] data = Helper.makeRandValues(20);
		if ((data[0] & 0xf0) == 0)
			data[0] |= 0x10;
		return new ASN1Integer(new BigInteger(1, data));
	}

	private void prepareBasicExtensions(ASN1Sequence extensions) throws Exception {
		extensions.addChild(new ASN1Sequence(OID_AuthorityKeyIdentifier,
				new ASN1OctetString(new ASN1Sequence(new ASN1OctetString(CtxTag0, ((ASN1OctetString) DecodeBER.decode(
						((ASN1OctetString) DecodeBER.decode(cacert.getExtensionValue(OID_SubjectKeyIdentifier.get())))
								.get())).get())).toDER())));
		byte[] issuerAltName = cacert.getExtensionValue(OID_SubjectAltName.get());
		if (issuerAltName != null)
			extensions.addChild(new ASN1Sequence(OID_IssuerAltName, new ASN1RawData(issuerAltName)));
		if (cRLDP != null)
			extensions
					.addChild(new ASN1Sequence(OID_CRLDistributionPoints,
							new ASN1OctetString(
									new ASN1Sequence(new ASN1Sequence(new ASN1ConstructedObject(CtxTag0,
											new ASN1ConstructedObject(CtxTag0,
													GeneralName.createUniformResourceIdentifier(cRLDP).get()))))
															.toDER())));
		extensions.addChild(new ASN1Sequence(OID_SubjectKeyIdentifier,
				new ASN1OctetString(new ASN1OctetString(SHA1.digest(publicKey.getEncoded())).toDER())));
	}

	private byte[] createTBSCertificate(ASN1Object signatureAlgorithm) throws Exception {
		if (basicConstraints == null)
			basicConstraints = new ASN1Sequence(new ASN1Boolean(false));
		ASN1Sequence tbsCertificate = new ASN1Sequence();
		tbsCertificate.addChild(V3);
		tbsCertificate.addChild(generateSerialNumber());
		tbsCertificate.addChild(signatureAlgorithm);
		tbsCertificate
				.addChild(new ASN1RawData((cacert != null ? cacert.getSubjectX500Principal() : subject).getEncoded()));
		tbsCertificate
				.addChild(new ASN1Sequence(new ASN1GeneralizedTime(notBefore), new ASN1GeneralizedTime(notAfter)));
		tbsCertificate.addChild(new ASN1RawData(subject.getEncoded()));
		tbsCertificate.addChild(new ASN1RawData(publicKey.getEncoded()));
		ASN1Sequence extensions = new ASN1Sequence();
		if (cacert != null)
			prepareBasicExtensions(extensions);
		extensions.addChild(new ASN1Sequence(OID_BasicConstraints, new ASN1Boolean(true),
				new ASN1OctetString(basicConstraints.toDER())));
		if (ocspURI != null)
			extensions.addChild(new ASN1Sequence(OID_AuthorityInfoAccess,
					new ASN1OctetString(new ASN1Sequence(new ASN1Sequence(OID_AccessDescriptionOCSP,
							GeneralName.createUniformResourceIdentifier(ocspURI).get())).toDER())));
		if (!subjectAltName.isEmpty())
			extensions.addChild(new ASN1Sequence(OID_SubjectAltName, new ASN1OctetString(subjectAltName.toDER())));
		if (!keyUsage.isEmpty())
			extensions.addChild(new ASN1Sequence(OID_KeyUsage, new ASN1Boolean(true),
					new ASN1OctetString(new ASN1BitString(keyUsage.toByteArray()).toDER())));
		if (!extKeyUsage.isEmpty())
			extensions.addChild(new ASN1Sequence(OID_ExtKeyUsage, new ASN1OctetString(extKeyUsage.toDER())));
		if (cps != null)
			extensions.addChild(new ASN1Sequence(OID_CertificatePolicies, cps.toPolicyQualifierInfo()));
		if (additionalExtensions != null)
			additionalExtensions.forEach(e -> extensions.addChild(e));
		tbsCertificate.addChild(new ASN1ConstructedObject(CtxTag3, extensions));
		return tbsCertificate.toDER();
	}

	private byte[] renewTBSCertificate(ASN1Object signatureAlgorithm, X509Certificate cert) throws Exception {
		Instant now = Instant.now();
		ASN1Sequence tbsCertificate = new ASN1Sequence();
		tbsCertificate.addChild(V3);
		tbsCertificate.addChild(generateSerialNumber());
		tbsCertificate.addChild(signatureAlgorithm);
		tbsCertificate.addChild(new ASN1RawData(cert.getIssuerX500Principal().getEncoded()));
		tbsCertificate.addChild(new ASN1Sequence(new ASN1GeneralizedTime(now),
				new ASN1GeneralizedTime(now.plusMillis(cert.getNotAfter().getTime() - cert.getNotBefore().getTime()))));
		tbsCertificate.addChild(new ASN1RawData(cert.getSubjectX500Principal().getEncoded()));
		tbsCertificate.addChild(new ASN1RawData(publicKey.getEncoded()));
		ASN1Sequence extensions = new ASN1Sequence();
		prepareBasicExtensions(extensions);
		cert.getCriticalExtensionOIDs().stream().filter(oid -> !basicExtensionOIDs.contains(oid))
				.map(oid -> new ASN1Sequence(new ASN1ObjectIdentifier(oid), new ASN1Boolean(true),
						new ASN1RawData(cert.getExtensionValue(oid))))
				.forEach(ext -> extensions.addChild(ext));
		cert.getNonCriticalExtensionOIDs().stream().filter(oid -> !basicExtensionOIDs.contains(oid)).map(
				oid -> new ASN1Sequence(new ASN1ObjectIdentifier(oid), new ASN1RawData(cert.getExtensionValue(oid))))
				.forEach(ext -> extensions.addChild(ext));
		tbsCertificate.addChild(new ASN1ConstructedObject(CtxTag3, extensions));
		return tbsCertificate.toDER();
	}

	@FunctionalInterface
	private interface TBSCertificateGenerator {
		byte[] apply(ASN1Object signatureAlgorithm) throws Exception;
	}

	private X509Certificate[] sign(Certificate[] ancestors, TBSCertificateGenerator tbsCertificateSupplier)
			throws Exception {
		PublicKeyAlgorithm publicKeyAlgorithm = PublicKeyAlgorithm.valueOf(privateKey);
		ASN1ObjectIdentifier signature = publicKeyAlgorithm.getSignatureAlgorithm(
				signsize > 0 ? signsize : PublicKeyAlgorithm.getSignatureSize(cacert.getSigAlgOID()));
		ASN1Object signatureAlgorithm = publicKeyAlgorithm.createAlgorithmIdentifier(signature);
		byte[] tbsCertificate = tbsCertificateSupplier.apply(signatureAlgorithm);
		Signature signer = Signature.getInstance(signature.get());
		signer.initSign(privateKey);
		signer.update(tbsCertificate);
		return Stream.concat(
				Stream.of(CertificateFactory.getInstance("X.509")
						.generateCertificate(new ByteArrayInputStream(new ASN1Sequence(new ASN1RawData(tbsCertificate),
								signatureAlgorithm, new ASN1BitString(signer.sign())).toDER()))),
				Arrays.stream(ancestors)).toArray(X509Certificate[]::new);
	}

	X509Certificate[] sign(Certificate[] ancestors) throws Exception {
		return sign(ancestors, signatureAlgorithm -> createTBSCertificate(signatureAlgorithm));
	}

	X509Certificate[] sign(Certificate[] ancestors, X509Certificate cert) throws Exception {
		return sign(ancestors, signatureAlgorithm -> renewTBSCertificate(signatureAlgorithm, cert));
	}
}
