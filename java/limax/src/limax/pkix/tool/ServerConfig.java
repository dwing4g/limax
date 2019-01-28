package limax.pkix.tool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;

import org.w3c.dom.Element;

import limax.pkix.CAService;
import limax.pkix.KeyInfo;
import limax.util.ElementHelper;
import limax.util.Trace;
import limax.util.XMLUtils;

class ServerConfig {
	private final Element root;

	ServerConfig(Path path) throws Exception {
		byte[] data = Files.readAllBytes(path);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		XMLUtils.prettySave(XMLUtils.getRootElement(new ByteArrayInputStream(data)).getOwnerDocument(), os);
		if (!Arrays.equals(os.toByteArray(), data))
			Files.write(path, os.toByteArray());
		this.root = XMLUtils.getRootElement(new ByteArrayInputStream(data));
		ElementHelper eh = new ElementHelper((Element) root.getElementsByTagName("Trace").item(0));
		Trace.Config config = new Trace.Config();
		config.setOutDir(eh.getString("outDir", "./trace"));
		config.setConsole(eh.getBoolean("console", true));
		config.setRotateHourOfDay(eh.getInt("rotateHourOfDay", 6));
		config.setRotateMinute(eh.getInt("rotateMinute", 0));
		config.setRotatePeriod(eh.getLong("rotatePeriod", 86400000l));
		config.setLevel(eh.getString("level", "warn").toUpperCase());
		Trace.openNew(config);
	}

	Element getRoot() {
		return root;
	}

	Element getElement(String name) {
		return (Element) root.getElementsByTagName(name).item(0);
	}

	char[] getAuthCode() {
		char[] authCode = new ElementHelper(root).getString("authCode").toCharArray();
		if (authCode.length == 0) {
			authCode = System.console().readPassword("authCode:");
			if (!Arrays.equals(authCode, System.console().readPassword("Confirm authCode:")))
				throw new RuntimeException("Differ authCode Input.");
		} else if (Trace.isWarnEnabled())
			Trace.warn("CAServer authCode SHOULD NOT contains in config file, except for test.");
		return authCode;
	}

	Archive getArchive() {
		String archive = new ElementHelper(root).getString("archive");
		if (archive.isEmpty())
			throw new RuntimeException("missing archive directory");
		return new Archive(archive);
	}

	String getDomain() {
		String domain = new ElementHelper(root).getString("domain");
		if (domain.isEmpty())
			throw new RuntimeException("missing domain");
		return domain;
	}

	CAService getCAService() throws Exception {
		CAService ca = null;
		for (Element e : XMLUtils.getChildElements(root)) {
			if (e.getTagName().equals("CAService")) {
				ElementHelper eh = new ElementHelper(e);
				URI location = URI.create(eh.getString("location"));
				if (Trace.isInfoEnabled())
					Trace.info("CAService " + location + " loading");
				String passphrase = eh.getString("passphrase");
				if (!passphrase.isEmpty() && Trace.isWarnEnabled())
					Trace.warn("CAService " + location
							+ " passphrase SHOULD NOT contains in config file, except for test.");
				CAService tmp = CAService.create(location, passphrase == null
						? prompt -> System.console().readPassword(prompt) : prompt -> passphrase.toCharArray());
				ca = ca == null ? tmp : ca.combine(tmp);
			}
		}
		return ca;
	}

	OcspServer createOcspServer(CAService ca, String domain, ScheduledExecutorService scheduler) throws Exception {
		ElementHelper eh = new ElementHelper(getElement("OcspServer"));
		int port = eh.getInt("port");
		int nextUpdateDelay = eh.getInt("nextUpdateDelay");
		int signatureBits = eh.getInt("signatureBits");
		int responseCacheCapacity = eh.getInt("responseCacheCapacity");
		Path ocspStore = Paths.get(eh.getString("ocspStore"));
		OcspSignerConfig ocspSignerConfig = new OcspSignerConfig(ca, nextUpdateDelay,
				eh.getString("certificateAlgorithm"), eh.getInt("certificateLifetime"), signatureBits, scheduler);
		return new OcspServer(ocspSignerConfig, port, domain, ocspStore, responseCacheCapacity);
	}

	OcspServer createOcspServer(KeyInfo keyInfo, String domain, ScheduledExecutorService scheduler) throws Exception {
		ElementHelper eh = new ElementHelper(root);
		int port = eh.getInt("port");
		int nextUpdateDelay = eh.getInt("nextUpdateDelay");
		int signatureBits = eh.getInt("signatureBits");
		int responseCacheCapacity = eh.getInt("responseCacheCapacity");
		Path ocspStore = Paths.get(eh.getString("ocspStore"));
		Path cRLFile = Paths.get(eh.getString("cRLFile"));
		Certificate[] chain = keyInfo.getCertificateChain();
		X509Certificate cacert = (X509Certificate) chain[chain.length - 1];
		OcspSignerConfig ocspSignerConfig = new OcspSignerConfig(keyInfo, cacert, cRLFile, nextUpdateDelay,
				signatureBits, scheduler);
		return new OcspServer(ocspSignerConfig, port, domain, ocspStore, responseCacheCapacity);
	}
}
