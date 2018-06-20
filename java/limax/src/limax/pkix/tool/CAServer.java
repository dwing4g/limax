package limax.pkix.tool;

import java.security.cert.X509CertSelector;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.w3c.dom.Element;

import limax.pkix.CAService;
import limax.util.ConcurrentEnvironment;
import limax.util.ElementHelper;
import limax.util.Helper;

class CAServer {
	private static final ScheduledThreadPoolExecutor scheduler = ConcurrentEnvironment.getInstance()
			.newScheduledThreadPool("CAServer Scheduler", 5, true);
	private final OcspServer ocspServer;
	private final CertServer certServer;
	private final CertUpdateServer certUpdateServer;

	CAServer(ServerConfig serverConfig) throws Exception {
		char[] authCode = serverConfig.getAuthCode();
		Archive archive = serverConfig.getArchive();
		String domain = serverConfig.getDomain();
		CAService ca = serverConfig.getCAService();
		ElementHelper eh = new ElementHelper(serverConfig.getElement("OcspServer"));
		this.ocspServer = serverConfig.createOcspServer(ca, domain, scheduler);
		Element e = serverConfig.getElement("CertServer");
		eh = new ElementHelper(e);
		this.certServer = new CertServer(ca, eh.getInt("port"), ocspServer, e,
				AuthCode.create(Helper.makeRandValues(32), authCode), archive);
		eh = new ElementHelper(serverConfig.getElement("CertUpdateServer"));
		X509CertSelector selector = new X509CertSelector();
		selector.setIssuer(ca.getCACertificate().getSubjectX500Principal());
		AutoHttpsServer server = new AutoHttpsServer(ca, domain, eh.getInt("port"),
				eh.getString("certificateAlgorithm"), eh.getInt("certificateLifetime"), null, selector, ocspServer,
				scheduler);
		this.certUpdateServer = new CertUpdateServer(ca, eh.getInt("renewLifespanPercent"), ocspServer, archive,
				server);
	}

	void start() throws Exception {
		ocspServer.start();
		certServer.start();
		certUpdateServer.start();
	}
}
