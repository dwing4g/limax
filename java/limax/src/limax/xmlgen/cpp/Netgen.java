package limax.xmlgen.cpp;

import java.io.File;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Main;
import limax.xmlgen.Protocol;
import limax.xmlgen.Rpc;
import limax.xmlgen.Service;

public class Netgen {
	private final Service service;

	static final String ProtocolBaseClassName = "limax::Protocol";

	public Netgen(Service service) {
		this.service = service;
	}

	public void make() {
		final File incDir = new File(Main.outputPath, Xmlgen.path_protocols);
		final File srcDir = new File(Main.outputPath, Xmlgen.path_xmlgen_src);
		for (final Protocol p : service.getProtocols())
			new ProtocolFormatter(service, p).make(incDir, srcDir);

		for (final Rpc p : service.getRpcs())
			throw new RuntimeException("unsupport rpc " + p.getFullName());
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
		for (Bean bean : beans) {
			new BeanFormatter(bean).make(genDir);
			Xmlgen.allbeanhpps.add(BeanFormatter.getOutputFileName(bean.getFullName()) + ".h");
		}
	}

}
