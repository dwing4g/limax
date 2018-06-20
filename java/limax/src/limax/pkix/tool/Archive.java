package limax.pkix.tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;

class Archive {
	private final Path path;

	Archive(String path) {
		try {
			this.path = Paths.get(path);
		} catch (Exception e) {
			throw new RuntimeException("Invalid Archive directory " + path, e);
		}
		if (!Files.isDirectory(this.path))
			throw new RuntimeException("Archive directory " + this.path + " not exists.");
	}

	void store(X509Certificate cert) throws Exception {
		Files.write(path.resolve(cert.getSerialNumber().toString(16) + ".cer"), cert.getEncoded());
	}
}
