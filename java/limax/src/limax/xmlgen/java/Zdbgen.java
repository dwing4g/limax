package limax.xmlgen.java;

import java.io.File;
import java.io.PrintStream;

import limax.xmlgen.CachedFileOutputStream;
import limax.xmlgen.FileOperation;
import limax.xmlgen.Main;
import limax.xmlgen.Zdb;

public class Zdbgen {

	private static File xbeanDir;
	private static File tableDir;

	static PrintStream openCBeanFile(File genDir, String classname) {
		return FileOperation.fopen(new File(genDir, "cbean"), classname + ".java");
	}

	static PrintStream openXBeanFile(String classname) {
		return FileOperation.fopen(xbeanDir, classname + ".java");
	}

	static PrintStream openTableFile(String classname) {
		return FileOperation.fopen(tableDir, classname + ".java");
	}

	static String verifyString(String objName, String methodName, boolean readonly) {
		return objName + ".verifyStandaloneOrLockHeld(\"" + methodName + "\", " + readonly + ")";
	}

	static void verify(PrintStream ps, String prefix, String objName, String methodName, boolean readonly) {
		if (!Main.zdbNoverify)
			ps.println(prefix + verifyString(objName, methodName, readonly) + ";");
	}

	public static void make(Zdb zdb, File genDir) {
		tableDir = new File(genDir, "table");
		xbeanDir = new File(genDir, "xbean");
		CachedFileOutputStream.removeOtherFiles(genDir);
		Foreign.verify(zdb);
		ForeignCircle.verify(zdb);
		zdb.getXbeans().forEach(XbeanFormatter::make);
		zdb.getTables().forEach(TableShortcut::make);
		TableTables.make(zdb.getTables());
		TableMeta.make(zdb);
	}
}
