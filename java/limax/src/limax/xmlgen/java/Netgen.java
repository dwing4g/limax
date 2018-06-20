package limax.xmlgen.java;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collection;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import limax.util.ElementHelper;
import limax.util.StringUtils;
import limax.util.XMLUtils;
import limax.xmlgen.Bean;
import limax.xmlgen.CachedFileOutputStream;
import limax.xmlgen.Cbean;
import limax.xmlgen.Main;
import limax.xmlgen.Manager;
import limax.xmlgen.Procedure;
import limax.xmlgen.Project;
import limax.xmlgen.Protocol;
import limax.xmlgen.Rpc;
import limax.xmlgen.Service;

public class Netgen {
	private Service service;

	public Netgen(Service service) {
		this.service = service;
	}

	public static void makeBeans(Collection<Cbean> cbeans, Collection<Bean> beans) {
		File genDir = new File(Main.outputPath, "gen");
		cbeans.forEach(bean -> CbeanFormatter.make(bean, genDir));
		beans.forEach(bean -> new BeanFormatter(bean).make(genDir));
	}

	public void make() throws Exception {
		File srcDir = new File(Main.outputPath, "src");
		File genDir = new File(Main.outputPath, "gen");

		for (final Protocol p : service.getProtocols())
			new ProtocolFormatter(service.getFullName(), p).make(srcDir);
		for (final Rpc r : service.getRpcs())
			new RpcFormatter(service.getFullName(), r).make(srcDir);

		for (final Manager manager : service.getManagers())
			new StatesFormatter(service, manager).makeStates(genDir);

		CachedFileOutputStream.removeOtherFiles(genDir);

		if (Main.noServiceXML)
			return;

		final File serviceconf = new File(Main.outputPath, "service-" + service.getName() + ".xml");
		if (!serviceconf.exists()) {
			try (PrintStream ps = new PrintStream(new FileOutputStream(serviceconf), false, "utf8")) {
				ps.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
				ps.println("<ServiceConf name=" + StringUtils.quote(service.getName()) + ">");
				ps.println(
						"<!-- ThreadPoolSize nioCpus=\"1\" netProcessors=\"4\" protocolSchedulers=\"4\" applicationExecutors=\"16\"-->");
				ps.println("</ServiceConf>");
			}
		}
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		final DocumentBuilder builder = factory.newDocumentBuilder();
		final Document doc = builder.parse(serviceconf);
		final Element root = doc.getDocumentElement();
		for (final Manager manager : service.getManagers())
			new ManagerFormatter(manager, service).make(root);
		if (service.hasServerOrProviderManager())
			setTrace(root);
		if (service.isUseGlobalId())
			setGlobalId(root);
		if (service.isUseZdb())
			setZdb(root);

		try (OutputStream os = new CachedFileOutputStream(serviceconf)) {
			XMLUtils.prettySave(doc, os);
		}
	}

	private static Element uniqueElement(Element parent, String tag) {
		final NodeList nl = parent.getElementsByTagName(tag);
		if (nl.getLength() > 1)
			throw new RuntimeException("Multi " + tag + " element found!");
		if (nl.getLength() > 0)
			return (Element) nl.item(0);
		Element e = parent.getOwnerDocument().createElement(tag);
		parent.appendChild(e);
		return e;
	}

	private static void setTrace(Element root) {
		Element self = uniqueElement(root, "Trace");
		ElementHelper setter = new ElementHelper(self);
		setter.setIfEmpty("outDir", "./trace");
		setter.setIfEmpty("console", true);
		setter.setIfEmpty("rotateHourOfDay", "6");
		setter.setIfEmpty("rotateMinute", "0");
		setter.setIfEmpty("level", "WARN");
	}

	private static void setGlobalId(Element root) {
		Element self = uniqueElement(root, "GlobalId");
		ElementHelper setter = new ElementHelper(self);
		setter.setIfEmpty("autoReconnect", "true");
		setter.setIfEmpty("remoteIp", "127.0.0.1");
		setter.setIfEmpty("remotePort", "10210");
	}

	private void setZdb(Element root) {
		limax.xmlgen.Zdb zdb = ((Project) service.getParent()).getZdb();

		Element self = uniqueElement(root, "Zdb");

		ElementHelper setter = new ElementHelper(self);
		setter.setIfEmpty("dbhome", zdb.getDbHome().toString());
		setter.setIfEmpty("jdbcPoolSize", zdb.getJdbcPoolSize());

		setter.setIfEmpty("defaultTableCache", zdb.getDefaultTableCache());

		setter.setIfEmpty("zdbVerify", zdb.isZdbVerify());
		setter.setIfEmpty("snapshotFatalTime", zdb.getSnapshotFatalTime());
		setter.setIfEmpty("marshalPeriod", zdb.getMarshalPeriod());
		setter.setIfEmpty("marshalN", zdb.getMarshalN());

		setter.setIfEmpty("edbCacheSize", zdb.getEdbCacheSize());
		setter.setIfEmpty("edbLoggerPages", zdb.getEdbLoggerPages());
		setter.setIfEmpty("corePoolSize", zdb.getCorePoolSize());
		setter.setIfEmpty("procPoolSize", zdb.getProcPoolSize());
		setter.setIfEmpty("schedPoolSize", zdb.getSchedPoolSize());
		setter.setIfEmpty("checkpointPeriod", zdb.getCheckpointPeriod());
		setter.setIfEmpty("deadlockDetectPeriod", zdb.getDeadlockDetectPeriod());
		setter.setIfEmpty("autoKeyInitValue", zdb.getAutoKeyInitValue());
		setter.setIfEmpty("autoKeyStep", zdb.getAutoKeyStep());

		Procedure pconf = zdb.getProcedure();
		setter = new ElementHelper(uniqueElement(self, "Procedure"));
		setter.setIfEmpty("maxExecutionTime", pconf.getMaxExecutionTime());
		setter.setIfEmpty("retryTimes", pconf.getRetryTimes());
		setter.setIfEmpty("retryDelay", pconf.getRetryDelay());
		setter.setIfEmpty("trace", pconf.getTrace().toString());
	}

}
