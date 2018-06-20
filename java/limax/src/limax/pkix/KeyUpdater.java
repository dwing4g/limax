package limax.pkix;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;

import limax.util.Helper;
import limax.util.SecurityUtils;
import limax.util.Trace;

class KeyUpdater {
	private final Path path;

	KeyUpdater(URI location) throws IOException {
		Path path = Paths.get(System.getProperty("java.io.tmpdir", "."));
		this.path = path.resolve(Helper.toFileNameString(location.toString().toLowerCase()));
	}

	interface Update<T, U> {
		void apply(T t, U u) throws Exception;
	}

	private ByteBuffer load() throws Exception {
		try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
			int size = (int) fc.size();
			ByteBuffer bb = ByteBuffer.allocate(size);
			fc.read(bb);
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			bb.position(0);
			bb.limit(size - 32);
			md.update(bb);
			bb.limit(size);
			byte[] hash = new byte[32];
			bb.get(hash);
			if (!Arrays.equals(hash, md.digest()))
				throw new SignatureException();
			bb.position(size - 40);
			return bb;
		}
	}

	void recover(Update<byte[], byte[]> update) throws Exception {
		ByteBuffer bb = load();
		byte[] b0 = new byte[bb.getInt()];
		byte[] b1 = new byte[bb.getInt()];
		bb.position(0);
		bb.get(b0);
		bb.get(b1);
		if (Trace.isWarnEnabled())
			Trace.warn("KeyRecovering, from " + path);
		update.apply(b0, b1);
		if (Trace.isWarnEnabled())
			Trace.warn("KeyRecovered, drop " + path);
		Files.delete(path);
	}

	void recover(char[] passphrase, Update<PrivateKey, Certificate[]> update) throws Exception {
		ByteBuffer bb = load();
		int l0 = bb.getInt();
		int l1 = bb.getInt();
		if (Trace.isWarnEnabled())
			Trace.warn("KeyRecovering, from " + path);
		update.apply(SecurityUtils.PublicKeyAlgorithm.loadPrivateKey(new String(bb.array(), 0, l0), passphrase),
				CertificateFactory.getInstance("X.509")
						.generateCertificates(new ByteArrayInputStream(bb.array(), l0, l1))
						.toArray(new Certificate[0]));
		if (Trace.isWarnEnabled())
			Trace.warn("KeyRecovered, drop " + path);
		Files.delete(path);
	}

	void save(PrivateKey privateKey, Certificate[] chain, char[] passphrase, Update<byte[], byte[]> update)
			throws Exception {
		byte[] b0 = SecurityUtils.assemblePKCS8(privateKey, passphrase).getBytes();
		byte[] b1 = SecurityUtils.assemblePKCS7(chain).getBytes();
		ByteBuffer bb = ByteBuffer.allocate(8);
		bb.putInt(b0.length).putInt(b1.length).rewind();
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(b0);
		md.update(b1);
		md.update(bb);
		bb.rewind();
		try (FileChannel fc = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE)) {
			fc.write(ByteBuffer.wrap(b0));
			fc.write(ByteBuffer.wrap(b1));
			fc.write(bb);
			fc.write(ByteBuffer.wrap(md.digest()));
			fc.force(true);
		}
		if (Trace.isInfoEnabled())
			Trace.info("KeyUpdating, backup created on " + path);
		update.apply(b0, b1);
		if (Trace.isInfoEnabled())
			Trace.info("KeyUpdated, drop " + path);
		Files.delete(path);
	}
}
