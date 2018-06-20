package limax.xmlgen.csharp;

import java.io.File;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Main;
import limax.xmlgen.Manager;
import limax.xmlgen.Protocol;
import limax.xmlgen.Rpc;
import limax.xmlgen.Service;

public class Netgen {

	private final Service service;

	public Netgen(Service service) {
		this.service = service;
	}

	public void make() {
		final File genDir = new File(Main.outputPath, Xmlgen.path_protocols);
		final File srcDir = new File(Main.outputPath, Xmlgen.path_xmlsrc);
		for (final Protocol p : service.getProtocols())
			new ProtocolFormatter(service, p).make(genDir, srcDir);

		for (final Rpc p : service.getRpcs())
			throw new RuntimeException("unsupport rpc " + p.getFullName());
		for (final Manager manager : service.getManagers())
			new StatesFormatter(service, manager).makeStates();
	}

	private static void delete(File file) {
		if (file.isDirectory())
			for (File f : file.listFiles())
				delete(f);
		file.delete();
	}

	public static void make(Collection<Bean> beans) {
		File genDir = new File(Main.outputPath, Xmlgen.path_beans);
		delete(genDir);
		for (Bean bean : beans)
			new BeanFormatter(bean).make(genDir);
	}

}
