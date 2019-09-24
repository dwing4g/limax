package limax.pkix;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Map;

import limax.codec.asn1.ASN1BitString;
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
import limax.util.SecurityUtils.PublicKeyAlgorithm;

class CRLGenerator {
	private static final ASN1Tag CtxTag0 = new ASN1Tag(TagClass.ContextSpecific, 0);
	private static final ASN1ObjectIdentifier OID_AuthorityKeyIdentifier = new ASN1ObjectIdentifier("2.5.29.35");
	private static final ASN1ObjectIdentifier OID_IssuerAltName = new ASN1ObjectIdentifier("2.5.29.18");
	private static final ASN1ObjectIdentifier OID_SubjectKeyIdentifier = new ASN1ObjectIdentifier("2.5.29.14");
	private static final ASN1ObjectIdentifier OID_SubjectAltName = new ASN1ObjectIdentifier("2.5.29.17");
	private static final ASN1ObjectIdentifier OID_CRLNumber = new ASN1ObjectIdentifier("2.5.29.20");

	private final PrivateKey privateKey;
	private final X509Certificate cacert;
	private final Map<BigInteger, Long> revokes;
	private final long nextUpdateDelay;
	private final int signsize;

	CRLGenerator(PrivateKey privateKey, X509Certificate cacert, Map<BigInteger, Long> revokes, long nextUpdateDelay,
			int signsize) throws Exception {
		this.privateKey = privateKey;
		this.cacert = cacert;
		this.revokes = revokes;
		this.nextUpdateDelay = nextUpdateDelay;
		this.signsize = signsize;
	}

	X509CRL sign() throws Exception {
		PublicKeyAlgorithm publicKeyAlgorithm = PublicKeyAlgorithm.valueOf(privateKey);
		ASN1ObjectIdentifier signature = publicKeyAlgorithm.getSignatureAlgorithm(
				signsize > 0 ? signsize : PublicKeyAlgorithm.getSignatureSize(cacert.getSigAlgOID()));
		ASN1Object algorithmIdentifier = publicKeyAlgorithm.createAlgorithmIdentifier(signature);
		ASN1Sequence tbsCertList = new ASN1Sequence();
		tbsCertList.addChild(new ASN1Integer(BigInteger.ONE));
		tbsCertList.addChild(algorithmIdentifier);
		tbsCertList.addChild(new ASN1RawData(cacert.getSubjectX500Principal().getEncoded()));
		Instant now = Instant.now();
		tbsCertList.addChild(new ASN1GeneralizedTime(now));
		tbsCertList.addChild(new ASN1GeneralizedTime(now.plusMillis(nextUpdateDelay)));
		ASN1Sequence revokedCertificates = new ASN1Sequence();
		revokes.forEach((serial, time) -> revokedCertificates.addChild(
				new ASN1Sequence(new ASN1Integer(serial), new ASN1GeneralizedTime(Instant.ofEpochMilli(time)))));
		tbsCertList.addChild(revokedCertificates);
		ASN1Sequence extensions = new ASN1Sequence();
		extensions.addChild(new ASN1Sequence(OID_CRLNumber,
				new ASN1OctetString(new ASN1Integer(BigInteger.valueOf(System.currentTimeMillis())).toDER())));
		extensions.addChild(new ASN1Sequence(OID_AuthorityKeyIdentifier,
				new ASN1OctetString(new ASN1Sequence(new ASN1OctetString(CtxTag0, ((ASN1OctetString) DecodeBER.decode(
						((ASN1OctetString) DecodeBER.decode(cacert.getExtensionValue(OID_SubjectKeyIdentifier.get())))
								.get())).get())).toDER())));
		byte[] issuerAltName = cacert.getExtensionValue(OID_SubjectAltName.get());
		if (issuerAltName != null)
			extensions.addChild(new ASN1Sequence(OID_IssuerAltName, new ASN1RawData(issuerAltName)));
		tbsCertList.addChild(new ASN1ConstructedObject(CtxTag0, extensions));
		Signature signer = Signature.getInstance(signature.get());
		signer.initSign(privateKey);
		byte[] derTBS = tbsCertList.toDER();
		signer.update(derTBS);
		return (X509CRL) CertificateFactory.getInstance("X.509")
				.generateCRL(new ByteArrayInputStream(
						new ASN1Sequence(new ASN1RawData(derTBS), algorithmIdentifier, new ASN1BitString(signer.sign()))
								.toDER()));
	}
}
