package limax.executable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {
	private Main() {
	}

	private static void usage(Map<String, Class<?>> map) {
		System.out.println("limax toolset");
		System.out.println("commands:");
		map.keySet().forEach(key -> System.out.println("	" + key));
		Runtime.getRuntime().exit(1);
	}

	public static void main(String[] args) throws Exception {
		Map<String, Class<?>> map = new LinkedHashMap<>();
		map.put("switcher", limax.switcher.Main.class);
		map.put("xmlgen", limax.xmlgen.Main.class);
		map.put("jmxtool", JmxTool.class);
		map.put("edbtool", limax.edb.DBTool.class);
		map.put("zdbtool", limax.zdb.tool.DBTool.class);
		map.put("seckey", MakeFileContentAsString.class);
		map.put("file2str", MakeFileContentAsString.class);
		map.put("node", limax.node.js.Main.class);
		map.put("pkix", limax.pkix.tool.Main.class);
		map.put("keyserver", limax.key.KeyAllocator.class);
		map.put("qrcode", QrCodeTool.class);
		if (args.length < 1)
			usage(map);
		String cmd = args[0].toLowerCase();
		Class<?> cls = map.get(cmd);
		if (null == cls)
			usage(map);
		Method method = cls.getDeclaredMethod("main", String[].class);
		method.setAccessible(true);
		method.invoke(null, (Object) Arrays.copyOfRange(args, 1, args.length));
	}
}
