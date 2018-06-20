package limax.switcher;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;

import limax.codec.Base64Decode;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.codec.SHA256;
import limax.endpoint.LmkBundle;
import limax.net.Engine;
import limax.pkix.LmkRequest;
import limax.pkix.TrustManager;
import limax.util.SecurityUtils;
import limax.util.Trace;

public class LmkMasquerade {
	@FunctionalInterface
	public interface SSLContextSupplier {
		SSLContext get() throws Exception;
	}

	private final SSLContextSupplier sslContextSupplier;
	private final TrustManager trustManager;
	private final boolean validateDate;
	private final long defaultLifetime;
	private final int renewConcurrency;
	private final Consumer<LmkInfo> lmkInfoConsumer;
	private final AtomicInteger serial = new AtomicInteger();

	LmkMasquerade(SSLContextSupplier sslContextSupplier, TrustManager trustManager, boolean validateDate,
			long defaultLifetime, int renewConcurrency, Consumer<LmkInfo> lmkInfoConsumer) throws Exception {
		this.sslContextSupplier = sslContextSupplier;
		this.trustManager = trustManager;
		this.validateDate = validateDate;
		this.defaultLifetime = defaultLifetime;
		this.renewConcurrency = renewConcurrency;
		this.lmkInfoConsumer = lmkInfoConsumer;
	}

	public boolean masquerade(String username, String token, Octets nonce, BiConsumer<String, Long> lmkConsumer) {
		try {
			X509Certificate[] certs = CertificateFactory.getInstance("X.509")
					.generateCertificates(new ByteArrayInputStream(Base64Decode.transform(username.getBytes())))
					.toArray(new X509Certificate[0]);
			RSAPublicKey rsaPublicKey = (RSAPublicKey) certs[0].getPublicKey();
			if (!token.startsWith("LMK0"))
				return false;
			OctetsStream os = OctetsStream.wrap(Octets.wrap(Base64Decode.transform(token.substring(4).getBytes())));
			if (!new BigInteger(os.unmarshal_bytes())
					.modPow(rsaPublicKey.getPublicExponent(), rsaPublicKey.getModulus())
					.equals(new BigInteger(1, SHA256.digest(nonce.getBytes()))))
				return false;
			X509CertSelector selector = new X509CertSelector();
			selector.setCertificate(certs[0]);
			PKIXBuilderParameters pkixBuilderParameters = trustManager.createPKIXBuilderParameters(selector, true);
			if (!validateDate)
				pkixBuilderParameters.setDate(certs[0].getNotBefore());
			pkixBuilderParameters.addCertStore(
					CertStore.getInstance("Collection", new CollectionCertStoreParameters(Arrays.asList(certs))));
			CertPathBuilder.getInstance("PKIX").build(pkixBuilderParameters);
			LmkRequest lmkRequest = LmkRequest.buildLmkRequest(certs[0]);
			long notBefore = certs[0].getNotBefore().getTime();
			long notAfter1 = certs[0].getNotAfter().getTime();
			long notAfter0 = (notAfter1 - notBefore) / 2 + notBefore;
			long notAfter;
			long now = System.currentTimeMillis();
			if (validateDate) {
				if (now > notAfter0) {
					scheduleRenew(lmkRequest.createContext(SecurityUtils.extractOcspURI(certs[0]).getHost()),
							os.unmarshal_bytes());
					notAfter = notAfter1;
				} else {
					notAfter = notAfter0;
				}
			} else {
				notAfter = now + defaultLifetime;
			}
			lmkConsumer.accept(lmkRequest.getUid(), notAfter);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	void scheduleRenew(LmkRequest.Context ctx, byte[] passphrase) {
		Engine.getApplicationExecutor().execute(serial.incrementAndGet() % renewConcurrency, () -> {
			try {
				if (passphrase != null)
					LmkStore.save(ctx, passphrase);
				LmkBundle lmkBundle = ctx.fetch(sslContextSupplier.get());
				lmkInfoConsumer.accept(new LmkInfo(ctx.getUid(), LmkStore.save(ctx, lmkBundle)));
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("LmkMasquerade.scheduleRenew, retry", e);
				scheduleRenew(ctx, null);
			}
		});
	}

	void recover(LmkInfo lmkInfo) {
		lmkInfoConsumer.accept(lmkInfo);
	}
}
