package limax.pkix;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import limax.util.SecurityUtils.PublicKeyAlgorithm;
import limax.util.Trace;

class CertificateAuthority {
	private static final Set<String> algorithms = Collections.unmodifiableSet(
			new HashSet<>(Arrays.asList("RSA/1024/256", "RSA/1024/384", "RSA/1024/512", "RSA/2048/256", "RSA/2048/384",
					"RSA/2048/512", "RSA/4096/256", "RSA/4096/384", "RSA/4096/512", "EC/256/256", "EC/256/384",
					"EC/256/512", "EC/384/256", "EC/384/384", "EC/384/512", "EC/521/256", "EC/521/384", "EC/521/512")));
	private final static EnumSet<KeyUsage> caKeyUsages = EnumSet.of(KeyUsage.cRLSign, KeyUsage.keyCertSign);
	private final static EnumSet<KeyUsage> endEntityKeyUsages = EnumSet.complementOf(caKeyUsages);
	private final PrivateKey privateKey;
	private final Certificate[] cacerts;
	private final X509Certificate cacert;
	private final int pathLen;
	private final Date notBefore;
	private final Date notAfter;
	private final int signsize;

	@Override
	public int hashCode() {
		return cacert.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof CertificateAuthority ? cacert.equals(((CertificateAuthority) obj).cacert) : false;
	}

	static Collection<String> getAlgorithms() {
		return algorithms;
	}

	CertificateAuthority(URI location, X509RootCertificateParameter param, Function<String, char[]> cb)
			throws Exception {
		String signAlgorithm = location.getFragment();
		if (signAlgorithm != null) {
			signAlgorithm = signAlgorithm.toUpperCase();
			if (!algorithms.contains(signAlgorithm))
				throw new NoSuchAlgorithmException(signAlgorithm);
		}
		KeyInfo keyInfo;
		try {
			keyInfo = KeyInfo.load(location, cb);
		} catch (UnrecoverableKeyException e) {
			throw e;
		} catch (Exception e) {
			String[] part = signAlgorithm.split("/");
			PublicKeyAlgorithm algorithm = PublicKeyAlgorithm.valueOf(part[0]);
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algorithm.name());
			keyPairGenerator.initialize(Integer.parseInt(part[1]));
			KeyPair keyPair = keyPairGenerator.generateKeyPair();
			CertificateGenerator cergen = new CertificateGenerator(keyPair, Integer.parseInt(part[2]));
			cergen.setSubject(param.getSubject());
			cergen.setValidity(Instant.ofEpochMilli(param.getNotBefore().getTime()),
					Instant.ofEpochMilli(param.getNotAfter().getTime()));
			cergen.setKeyUsage(caKeyUsages);
			cergen.setCertificatePolicyCPS(param.getCertificatePolicyCPS());
			cergen.setAdditionalExtensions(param.getAdditionalExtensions());
			keyInfo = KeyInfo.save(location, keyPair.getPrivate(), cergen.sign(new Certificate[0]), cb);
		}
		privateKey = keyInfo.getPrivateKey();
		cacerts = keyInfo.getCertificateChain();
		signsize = signAlgorithm == null ? 0
				: Integer.parseInt(signAlgorithm.substring(signAlgorithm.lastIndexOf('/') + 1));
		cacert = (X509Certificate) cacerts[0];
		pathLen = cacert.getBasicConstraints();
		if (pathLen < 0)
			throw new CertificateException("Invalid ca pathLen " + pathLen);
		notBefore = cacert.getNotBefore();
		notAfter = cacert.getNotAfter();
		long now = System.currentTimeMillis();
		if (notAfter.getTime() <= now) {
			if (Trace.isErrorEnabled())
				Trace.error("cacert expired");
		} else {
			if ((now - notBefore.getTime()) / (notAfter.getTime() - now) >= 4)
				if (Trace.isWarnEnabled())
					Trace.warn("cacert remain less than 20% lifespan");
		}
	}

	private CertificateGenerator createGenerator(X509CertificateBaseParameter param) throws Exception {
		CertificateGenerator cergen = new CertificateGenerator(privateKey, cacert,
				((X509CertificateParameter) param).getPublicKey(), signsize);
		cergen.setSubject(param.getSubject());
		Date notBefore = param.getNotBefore();
		Date notAfter = param.getNotAfter();
		if (notBefore.before(this.notBefore) || notBefore.after(this.notAfter))
			throw new IllegalArgumentException("request notBefore out of ca's validity range");
		if (notAfter.before(this.notBefore) || notAfter.after(this.notAfter))
			throw new IllegalArgumentException("request notBefore out of ca's validity range");
		cergen.setValidity(Instant.ofEpochMilli(notBefore.getTime()), Instant.ofEpochMilli(notAfter.getTime()));
		return cergen;
	}

	private X509Certificate[] sign(X509CertificateParameter param, CertificateGenerator cergen) throws Exception {
		cergen.setOcspURI(param.getOcspURI());
		Function<X509Certificate, URI> cRLDPMapping = param.getCRLDPMapping();
		if (cRLDPMapping != null)
			cergen.setCRLDP(cRLDPMapping.apply(cacert));
		cergen.setCertificatePolicyCPS(param.getCertificatePolicyCPS());
		cergen.setAdditionalExtensions(param.getAdditionalExtensions());
		for (GeneralName name : param.getSubjectAltNames())
			cergen.addSubjectAltName(name);
		return cergen.sign(cacerts);
	}

	private X509Certificate[] sign(X509CACertificateParameter param) throws Exception {
		int pathLen = param.getBasicConstraints();
		if (pathLen < 0)
			throw new IllegalArgumentException("request pathLen < 0");
		if (pathLen >= this.pathLen)
			throw new IllegalArgumentException("request pathLen >= ca.pathLen");
		CertificateGenerator cergen = createGenerator(param);
		cergen.setBasicConstraints(pathLen);
		cergen.setKeyUsage(caKeyUsages);
		return sign(param, cergen);
	}

	private X509Certificate[] sign(X509EndEntityCertificateParameter param) throws Exception {
		CertificateGenerator cergen = createGenerator(param);
		EnumSet<KeyUsage> keyUsages = param.getKeyUsages();
		EnumSet<ExtKeyUsage> extKeyUsages = param.getExtKeyUsages();
		if (!endEntityKeyUsages.containsAll(keyUsages))
			throw new IllegalArgumentException("Invalid keyUsage of End Entity Certificate " + keyUsages);
		cergen.setKeyUsage(keyUsages);
		cergen.setExtKeyUsage(extKeyUsages);
		return sign(param, cergen);
	}

	X509Certificate[] sign(X509CertificateParameter param) throws Exception {
		return param instanceof X509CACertificateParameter ? sign((X509CACertificateParameter) param)
				: sign((X509EndEntityCertificateParameter) param);
	}

	public X509Certificate[] sign(X509CertificateRenewParameter param) throws Exception {
		if (param.getCertificate().getBasicConstraints() != -1)
			throw new CertificateException("ONLY End Entity Certificate Support renew");
		CertificateGenerator cergen = new CertificateGenerator(privateKey, cacert, param.getPublicKey(), signsize);
		Function<X509Certificate, URI> cRLDPMapping = param.getCRLDPMapping();
		if (cRLDPMapping != null)
			cergen.setCRLDP(cRLDPMapping.apply(cacert));
		return cergen.sign(cacerts, param.getCertificate());
	}

	X509CRL sign(X509CRLParameter param) throws Exception {
		return new CRLGenerator(privateKey, cacert, param.getRevokes(), param.getNextUpdateDelay(), signsize).sign();
	}

	X509Certificate getCACertificate() {
		return cacert;
	}
}