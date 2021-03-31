package limax.pkix;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertPathValidator;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import limax.util.Trace;

public class TrustManager implements Cloneable {
	private final Set<TrustAnchor> trustAnchors = new HashSet<>();
	private Set<PKIXRevocationChecker.Option> options;

	public void addTrust(TrustAnchor anchor) {
		trustAnchors.add(anchor);
		if (Trace.isDebugEnabled()) {
			X509Certificate cert = anchor.getTrustedCert();
			Trace.debug(String.format("addTrust\nSubject: %s\nIssuer: %s", cert.getSubjectX500Principal(),
					cert.getIssuerX500Principal()));
		}
	}

	public void addTrust(Certificate cert) {
		addTrust(new TrustAnchor((X509Certificate) cert, null));
	}

	public void addTrust(InputStream x509ORpkcs7Stream) throws CertificateException {
		CertificateFactory.getInstance("X.509").generateCertificates(x509ORpkcs7Stream).forEach(cert -> addTrust(cert));
	}

	public void addTrust(KeyStore keyStore) throws KeyStoreException {
		for (Enumeration<String> e = keyStore.aliases(); e.hasMoreElements();) {
			String alias = e.nextElement();
			if (keyStore.isCertificateEntry(alias))
				addTrust(keyStore.getCertificate(alias));
		}
	}

	/**
	 * 
	 * @param trustsPath
	 * 
	 *            If trustsPath is file extract the file, if trustsPath is
	 *            directory walk it one level for files. Treat these files as
	 *            x509file, pkcs7file, keystorefile to addTrust, ignore all
	 *            exceptions.
	 */
	public void addTrust(Path trustsPath) {
		List<Path> files;
		if (Files.isDirectory(trustsPath)) {
			try (Stream<Path> stream = Files.list(trustsPath)) {
				files = stream.filter(Files::isRegularFile).collect(Collectors.toList());
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("loadTrusts", e);
				return;
			}
		} else if (Files.isRegularFile(trustsPath)) {
			files = Arrays.asList(trustsPath);
		} else
			return;
		for (Path path : files) {
			boolean done = false;
			while (true) {
				try (InputStream in = Files.newInputStream(path)) {
					addTrust(in);
					done = true;
					break;
				} catch (Exception e) {
				}
				try (InputStream in = Files.newInputStream(path)) {
					KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
					keyStore.load(in, null);
					addTrust(in);
					done = true;
					break;
				} catch (Exception e) {
				}
				break;
			}
			if (done) {
				if (Trace.isInfoEnabled())
					Trace.info("loadTrusts from <" + path + "> success");
			} else {
				if (Trace.isWarnEnabled())
					Trace.warn("loadTrusts from <" + path + "> fail");
			}
		}
	}

	public void setRevocationCheckerOptions(Set<PKIXRevocationChecker.Option> options) {
		this.options = options;
	}

	/**
	 * @param options
	 *            <br>
	 *            1. Case insensitive and keywords separate by comma. <br>
	 *            2. Keyword set [DISABLE, NO_FALLBACK, ONLY_END_ENTITY,
	 *            PREFER_CRLS, SOFT_FAIL], ignore invalid keyword. <br>
	 *            3. High priority keyword 'DISABLE' <br>
	 *            4. Other keyword reference to PKIXRevocationChecker.Option
	 */
	public void setRevocationCheckerOptions(String options) {
		Set<PKIXRevocationChecker.Option> set = new HashSet<>();
		for (String checker : options.toUpperCase().split(",")) {
			if (checker.equals("DISABLE")) {
				set = null;
				break;
			}
			try {
				set.add(PKIXRevocationChecker.Option.valueOf(checker.trim()));
			} catch (Exception e) {
			}
		}
		setRevocationCheckerOptions(set);
	}

	public Set<TrustAnchor> getTrustAnchors() {
		return trustAnchors;
	}

	public PKIXBuilderParameters createPKIXBuilderParameters(X509CertSelector selector,
			boolean installRevocationChecker) throws GeneralSecurityException {
		PKIXBuilderParameters pkixBuilderParameters = new PKIXBuilderParameters(trustAnchors,
				selector != null ? selector : new X509CertSelector());
		if (installRevocationChecker && options != null) {
			PKIXRevocationChecker pKIXRevocationChecker = (PKIXRevocationChecker) CertPathValidator.getInstance("PKIX")
					.getRevocationChecker();
			pKIXRevocationChecker.setOptions(options);
			pkixBuilderParameters.addCertPathChecker(pKIXRevocationChecker);
		} else {
			pkixBuilderParameters.setRevocationEnabled(false);
		}
		return pkixBuilderParameters;
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}
}
