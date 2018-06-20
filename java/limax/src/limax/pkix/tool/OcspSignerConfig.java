package limax.pkix.tool;

import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.security.auth.x500.X500Principal;

import limax.codec.SHA1;
import limax.codec.asn1.ASN1BitString;
import limax.codec.asn1.ASN1ConstructedObject;
import limax.codec.asn1.ASN1OctetString;
import limax.codec.asn1.ASN1RawData;
import limax.codec.asn1.ASN1Sequence;
import limax.codec.asn1.ASN1Tag;
import limax.codec.asn1.DecodeBER;
import limax.codec.asn1.TagClass;
import limax.pkix.CAService;
import limax.pkix.ExtKeyUsage;
import limax.pkix.KeyInfo;
import limax.pkix.KeyUsage;
import limax.pkix.X509CRLParameter;
import limax.pkix.X509EndEntityCertificateParameter;
import limax.pkix.X509Extension;
import limax.util.Trace;

class OcspSignerConfig {
	private static final long SIGNER_RENEW_AHEAD_MILLISECOND = 10000L;
	private static final ASN1Tag CtxTag0 = new ASN1Tag(TagClass.ContextSpecific, 0);
	private static final ASN1Tag CtxTag2 = new ASN1Tag(TagClass.ContextSpecific, 2);
	private static final X509Extension OCSP_NOCHECK = new X509Extension("1.3.6.1.5.5.7.48.1.5", false,
			new byte[] { 4, 2, 5, 0 });

	static class Current {
		private final PrivateKey privateKey;
		private final byte[] responderID;
		private final byte[] certs;

		Current(PrivateKey privateKey, byte[] responderID, byte[] certs) {
			this.privateKey = privateKey;
			this.responderID = responderID;
			this.certs = certs;
		}

		PrivateKey getPrivateKey() {
			return privateKey;
		}

		byte[] getResponderID() {
			return responderID;
		}

		byte[] getCerts() {
			return certs;
		}
	}

	private final X509Certificate[] cacerts;
	private final BiFunction<X509Certificate, Map<BigInteger, Long>, byte[]> cRLSigner;
	private final int nextUpdateDelay;
	private final int signatureBits;
	private final ScheduledExecutorService scheduler;
	private volatile Current current;

	Current getCurrent() {
		return current;
	}

	X509Certificate[] getCACertificates() {
		return cacerts;
	}

	private long init(PrivateKey privateKey, Certificate[] chain) throws Exception {
		ASN1Sequence seq = new ASN1Sequence();
		for (int i = 0; i < chain.length - 1; i++)
			seq.addChild(new ASN1RawData(chain[i].getEncoded()));
		X509Certificate cert = (X509Certificate) chain[0];
		byte[] responderID = new ASN1ConstructedObject(CtxTag2,
				new ASN1OctetString(SHA1.digest(
						((ASN1BitString) ((ASN1Sequence) DecodeBER.decode(cert.getPublicKey().getEncoded())).get(1))
								.get().toByteArray()))).toDER();
		byte[] certs = new ASN1ConstructedObject(CtxTag0, seq).toDER();
		this.current = new Current(privateKey, responderID, certs);
		return cert.getNotAfter().getTime() - System.currentTimeMillis() - TimeUnit.DAYS.toMillis(nextUpdateDelay)
				- SIGNER_RENEW_AHEAD_MILLISECOND;
	}

	OcspSignerConfig(KeyInfo keyInfo, X509Certificate cacert, Path cRLFile, int nextUpdateDelay, int signatureBits,
			ScheduledExecutorService scheduler) throws Exception {
		this.cacerts = new X509Certificate[] { cacert };
		this.nextUpdateDelay = nextUpdateDelay;
		this.signatureBits = signatureBits;
		this.scheduler = scheduler;
		this.cRLSigner = (cert, revokes) -> {
			try {
				return Files.readAllBytes(cRLFile);
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("OcspSignerConfig loadCRL", e);
				return null;
			}
		};
		scheduler.schedule(() -> {
			Trace.fatal("OcspSignerConfig Certificate Expired, Aborting");
			System.exit(-1);
		}, init(keyInfo.getPrivateKey(), keyInfo.getCertificateChain()), TimeUnit.MILLISECONDS);
	}

	OcspSignerConfig(CAService ca, int nextUpdateDelay, String algo, int lifetime, int signatureBits,
			ScheduledExecutorService scheduler) throws Exception {
		this.cacerts = ca.getCACertificates();
		this.nextUpdateDelay = nextUpdateDelay;
		this.signatureBits = signatureBits;
		this.scheduler = scheduler;
		this.cRLSigner = (cacert, revokes) -> {
			try {
				return ca.sign(new X509CRLParameter() {
					@Override
					public X509Certificate getCACertificate() {
						return cacert;
					}

					@Override
					public Map<BigInteger, Long> getRevokes() {
						return revokes;
					}

					@Override
					public long getNextUpdateDelay() {
						return TimeUnit.DAYS.toMillis(nextUpdateDelay);
					}
				}).getEncoded();
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("OcspSignerConfig signCRL", e);
				return null;
			}
		};
		renewOcspSigner(ca, algo, lifetime);
	}

	int getNextUpdateDelay() {
		return nextUpdateDelay;
	}

	int getSignatureBits() {
		return signatureBits;
	}

	ScheduledExecutorService getScheduler() {
		return scheduler;
	}

	BiFunction<X509Certificate, Map<BigInteger, Long>, byte[]> getCRLSigner() {
		return cRLSigner;
	}

	private void renewOcspSigner(CAService ca, String algo, int lifetime) throws Exception {
		KeyPair keyPair = Main.keyPairGenerator(ca, algo);
		scheduler.schedule(() -> {
			try {
				renewOcspSigner(ca, algo, lifetime);
			} catch (Exception e) {
				Trace.fatal("OcspSignerConfig renew fail", e);
				System.exit(-1);
			}
		}, init(keyPair.getPrivate(), ca.sign(new X509EndEntityCertificateParameter() {
			@Override
			public X500Principal getSubject() {
				return new X500Principal("CN=OCSP Responder");
			}

			@Override
			public URI getOcspURI() {
				return null;
			}

			@Override
			public Function<X509Certificate, URI> getCRLDPMapping() {
				return null;
			}

			@Override
			public PublicKey getPublicKey() {
				return keyPair.getPublic();
			}

			@Override
			public EnumSet<KeyUsage> getKeyUsages() {
				return EnumSet.of(KeyUsage.digitalSignature);
			}

			@Override
			public EnumSet<ExtKeyUsage> getExtKeyUsages() {
				return EnumSet.of(ExtKeyUsage.OCSPSigning);
			}

			@Override
			public Date getNotAfter() {
				return new Date(getNotBefore().getTime() + TimeUnit.DAYS.toMillis(lifetime + nextUpdateDelay));
			}

			@Override
			public Collection<X509Extension> getAdditionalExtensions() {
				return Arrays.asList(OCSP_NOCHECK);
			}
		})), TimeUnit.MILLISECONDS);
	}
}