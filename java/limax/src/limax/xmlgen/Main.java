package limax.xmlgen;

import java.io.File;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import limax.util.StringUtils;
import limax.util.Trace;
import limax.util.XMLUtils;

public final class Main {

	public static boolean isMakingNet = false;
	public static boolean isMakingZdb = false;
	public static boolean isMakingView = false;
	public static boolean isMakingConverter = false;

	public static boolean isJava = false;
	public static boolean isCpp = false;
	public static boolean isObjectiveC = false;
	public static boolean isCSharp = false;
	public static boolean scriptSupport = false;
	public static boolean variantSupport = false;
	public static boolean jsTemplate = false;
	public static boolean luaTemplate = false;

	public static boolean variableCommentPrevious = false;

	public static String outputEncoding = "UTF-8";
	public static String inputEncoding = "UTF-8";

	public static boolean noServiceXML = false;

	public static File inputPath = new File(".");
	public static File outputPath = new File(".");

	public static boolean zdbNoverify = false;

	public static String singleService = null;

	public static Project currentProject = null;

	private final static Map<Integer, String> types = new HashMap<>();

	public static void checkReserveType(String name, int type) {
		String old = types.put(type, name);
		if (old != null)
			throw new RuntimeException(name + ", duplicate type with " + old);
	}

	private static void usage() {
		PrintStream ps = System.out;
		ps.println("Usage: java -jar limax.jar xmlgen [options] ...");
		ps.println("    -h --help        print this");

		ps.println("    -java            generate for java, default");
		ps.println("    -textEncoding    string type encoding. default UTF-8. for java");
		ps.println("    -noServiceXML    not generate service-*.xml. for java");
		ps.println("    -service         only one service name in xml");
		ps.println("    -script          generate script supported server");
		ps.println("    -variant         generate variant supported server");
		ps.println("    -jsTemplate      generate template.js in current directory");
		ps.println("    -luaTemplate     generate template.lua in current directory");

		ps.println("    -c++             generate for C++");
		ps.println("    -oc              generate for Objective-C++, use C++ codes");
		ps.println("    -ostream         C++ Protocol or Bean Obj >> ostream");
		ps.println("    -pch             include precompiled header when generate cpp file");
		ps.println("    -cxxTrace 	     generate trace(ostream) in c++");

		ps.println("    -c#              generate for CSharp");

		ps.println("    -trace           trace level(DEBUG, INFO, WARN, ERROR, FETAL). default WARN");
		ps.println("    -outputEncoding  encoding. default UTF-8");
		ps.println("    -inputEncoding   encoding. default UTF-8");
		ps.println("    -outputPath      output path. default .\\");
		ps.println("    -inputPath       input path. default .\\");

		ps.println("    -zdbNoverify     do not generate xbean verify code.");
		ps.println("    -zdbExplicitLockCheck [table1:table2]  generate lockcheck except [...], ");

		System.exit(1);
	}

	private static void assertFalse(boolean c, String msg) {
		if (c)
			throw new IllegalArgumentException(msg);
	}

	private static void prepare() throws Exception {
		assertFalse(isJava && isCpp, "options conflict : -java, -c++");
		assertFalse(isJava && isCSharp, "options conflict : -java, -c#");
		assertFalse(isCpp && isCSharp, "options conflict : -c++, -c#");
		assertFalse(scriptSupport && (isCpp || isCSharp), "-script only support with -java");
		assertFalse(variantSupport && (isCpp || isCSharp), "-variant only support with -java");
		assertFalse(jsTemplate && !scriptSupport, "-jsTemplate must be set with -script");
		assertFalse(luaTemplate && !scriptSupport, "-luaTemplate must be set with -script");
	}

	public static void main(String args[]) throws Exception {
		System.setProperty("line.separator", "\n");
		PrintStream ps = System.out;
		String xmlfile = null;

		Trace.openNew(null, true, -1, -1, -1);
		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("-trace"))
				Trace.set(Trace.valueOf(args[++i].toUpperCase()));
			else if (args[i].equals("-outputEncoding"))
				outputEncoding = args[++i];
			else if (args[i].equals("-inputEncoding"))
				inputEncoding = args[++i];
			else if (args[i].equals("-noServiceXML"))
				noServiceXML = true;
			else if (args[i].equals("-service"))
				singleService = args[++i];
			else if (args[i].equals("-outputPath"))
				outputPath = new File(args[++i]);
			else if (args[i].equals("-inputPath"))
				inputPath = new File(args[++i]);
			else if (args[i].equals("-script"))
				scriptSupport = true;
			else if (args[i].equals("-variant"))
				variantSupport = true;
			else if (args[i].equals("-jsTemplate"))
				jsTemplate = true;
			else if (args[i].equals("-luaTemplate"))
				luaTemplate = true;
			else if (args[i].equals("-java"))
				isJava = true;
			else if (args[i].equals("-c#") || args[i].equals("-csharp"))
				isCSharp = true;
			else if (args[i].equals("-c++") || args[i].equals("-cpp"))
				isCpp = true;
			else if (args[i].equals("-oc")) {
				isObjectiveC = true;
				isCpp = true;
			} else if (args[i].equals("-h") || args[i].equals("--help"))
				usage();
			else if (args[i].equals("-zdbNoverify"))
				zdbNoverify = true;
			else if (args[i].equals("-variableCommentPrevious"))
				variableCommentPrevious = true;
			else
				xmlfile = args[i];
		}

		if (xmlfile == null)
			usage();

		prepare();

		File file = new File(inputPath, xmlfile);

		System.out.println("import --> " + StringUtils.quote(file.getCanonicalPath()));

		currentProject = new Project(new Naming.Root(), XMLUtils.getRootElement(file));

		if (Trace.isDebugEnabled()) {
			System.out.println("Namespace dump :");
			currentProject.dump(ps);
		}

		compile(currentProject.getRoot());
		System.out.println("xmlgen --> " + xmlfile);

		{
			boolean isclient = currentProject.isClient();
			assertFalse(scriptSupport && isclient, "-script only support server");
			assertFalse(variantSupport && isclient, "-variant only support server");
		}
		currentProject.make();

		CachedFileOutputStream.doRemoveFiles();
	}

	public static void compile(Naming.Root root) {
		Collection<Naming> list = root.compile();
		if (list.size() > 0) {
			System.err.println("Unresolved symobls:");
			for (Naming n : list)
				System.err.println(n);
			System.exit(0);
		}
	}
}
