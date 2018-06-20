package limax.xmlgen;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

public final class FileOperation {
	private static File toFile(File base, String name) {
		File file = new File(base, name);
		file.getParentFile().mkdirs();
		if (!file.getParentFile().exists())
			throw new RuntimeException("can not create dirs: " + file.getParent());
		return file;
	}

	public static boolean fparse(File path, String name, LineParser parser) {
		File file = toFile(path, name);
		if (!file.exists())
			return false;
		try (BufferedReader lnr = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), Main.inputEncoding))) {
			for (String line; (line = lnr.readLine()) != null;)
				parser.parseLine(line);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return true;
	}

	public static PrintStream fopen(File path, String name) {
		try {
			return new PrintStream(new CachedFileOutputStream(toFile(path, name)), false, Main.outputEncoding);
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static PrintStream fopen(String name) {
		return fopen(Main.outputPath, name);
	}
}
