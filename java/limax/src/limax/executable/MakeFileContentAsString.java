package limax.executable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

class MakeFileContentAsString {
	private MakeFileContentAsString() {
	}

	public static void main(String args[]) throws Exception {
		if (args.length < 5) {
			System.out.println("usage : inputfile outputfile concat stringprefix stringsubfix");
			return;
		}
		String concat = args[2].trim();
		try (final BufferedReader br = new BufferedReader(new FileReader(args[0]));
				final BufferedWriter bw = new BufferedWriter(new FileWriter(args[1]))) {
			bw.write(args[3]);
			bw.newLine();
			String line = br.readLine();
			while (null != line) {
				line = line.replace("\\", "\\\\");
				line = line.replace("\"", "\\\"");
				bw.write("\"");
				bw.write(line);
				bw.write("\\n\"" + concat);
				bw.newLine();
				line = br.readLine();
			}
			bw.write("\"\\n\"");
			bw.write(args[4]);
		}
	}
}
