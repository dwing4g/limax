package limax.switcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.function.Consumer;

import limax.codec.Base64Encode;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.pkix.KeyInfo;
import limax.pkix.SSLContextAllocator;
import limax.pkix.TrustManager;
import limax.util.Helper;
import limax.util.SecurityUtils;
import limax.util.ZipUtils;

public class LmkConfigData {
	private final String passphrase;
	private final String pkcs7;
	private final String pkcs8;
	private final byte[] zipTrustsPath;
	private final String revocationCheckerOptions;
	private final boolean validateDate;
	private final long defaultLifetime;
	private final int renewConcurrency;
	private final TrustManager trustManager;
	private final SSLContextAllocator sslContextAllocator;
	private final KeyInfo keyInfo;

	public LmkConfigData(SSLContextAllocator sslContextAllocator, Path trustsPath, String revocationCheckerOptions,
			boolean validateDate, long defaultLifetime, int renewConcurrency) throws Exception {
		KeyInfo keyInfo = sslContextAllocator.getKeyInfo();
		this.passphrase = new String(Base64Encode.transform(Helper.makeRandValues(16)));
		this.pkcs7 = SecurityUtils.assemblePKCS7(keyInfo.getCertificateChain());
		this.pkcs8 = SecurityUtils.assemblePKCS8(keyInfo.getPrivateKey(), passphrase.toCharArray());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ZipUtils.zip(trustsPath, baos);
		this.zipTrustsPath = baos.toByteArray();
		this.revocationCheckerOptions = revocationCheckerOptions;
		this.validateDate = validateDate;
		this.defaultLifetime = defaultLifetime;
		this.renewConcurrency = renewConcurrency;
		this.trustManager = loadTrustManager();
		this.trustManager.getTrustAnchors().forEach(anchor -> sslContextAllocator.getTrustManager().addTrust(anchor));
		this.sslContextAllocator = sslContextAllocator;
		this.keyInfo = null;
	}

	public LmkConfigData(Octets binary) throws Exception {
		OctetsStream os = OctetsStream.wrap(binary);
		this.passphrase = os.unmarshal_String();
		this.pkcs7 = os.unmarshal_String();
		this.pkcs8 = os.unmarshal_String();
		this.zipTrustsPath = os.unmarshal_bytes();
		this.revocationCheckerOptions = os.unmarshal_String();
		this.validateDate = os.unmarshal_boolean();
		this.defaultLifetime = os.unmarshal_long();
		this.renewConcurrency = os.unmarshal_int();
		this.trustManager = loadTrustManager();
		this.sslContextAllocator = null;
		this.keyInfo = KeyInfo
				.save(URI.create("lmk:requestor@" + System.getProperty("java.io.tmpdir", ".").replace('\\', '/')),
						SecurityUtils.PublicKeyAlgorithm.loadPrivateKey(pkcs8, passphrase.toCharArray()),
						CertificateFactory.getInstance("X.509")
								.generateCertificates(new ByteArrayInputStream(pkcs7.getBytes()))
								.toArray(new Certificate[0]),
						prompt -> passphrase.toCharArray());
	}

	public Octets encode() {
		return new OctetsStream().marshal(passphrase).marshal(pkcs7).marshal(pkcs8).marshal(zipTrustsPath)
				.marshal(revocationCheckerOptions).marshal(validateDate).marshal(defaultLifetime)
				.marshal(renewConcurrency);
	}

	private TrustManager loadTrustManager() throws IOException {
		TrustManager trustManager = new TrustManager();
		trustManager.setRevocationCheckerOptions(revocationCheckerOptions);
		Path trustsPath = Files.createTempDirectory("trustsPath");
		ZipUtils.unzip(new ByteArrayInputStream(zipTrustsPath), trustsPath);
		trustManager.addTrust(trustsPath);
		Files.walkFileTree(trustsPath, new FileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.deleteIfExists(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.deleteIfExists(dir);
				return FileVisitResult.CONTINUE;
			}
		});
		Files.deleteIfExists(trustsPath);
		return trustManager;
	}

	public LmkMasquerade createLmkMasquerade(Consumer<LmkInfo> lmkDataUploadConsumer) throws Exception {
		return new LmkMasquerade(
				keyInfo == null ? () -> sslContextAllocator.alloc()
						: () -> keyInfo.createSSLContext(trustManager, true, null),
				trustManager, validateDate, defaultLifetime, renewConcurrency, lmkDataUploadConsumer);
	}
}
