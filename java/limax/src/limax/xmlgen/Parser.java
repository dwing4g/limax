package limax.xmlgen;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class Parser implements LineParser {
	private List<String> lines = new ArrayList<String>();
	private int importBeginLine = -1;
	private int defineBeginLine = -1;

	enum State {
		ImportBegin, ImportEnd, DefineBegin, DefineEnd, Others
	};

	private State state = State.Others;

	public static String IMPORT_BEGIN = "// {{{ XMLGEN_IMPORT_BEGIN";
	public static String IMPORT_END = "// XMLGEN_IMPORT_END }}}";
	public static String DEFINE_BEGIN = "// {{{ XMLGEN_DEFINE_BEGIN";
	public static String DEFINE_END = "// XMLGEN_DEFINE_END }}}";

	public void verify(String info) {
		if (importBeginLine < 0 || defineBeginLine < 0 || importBeginLine > defineBeginLine
				|| importBeginLine > lines.size() || defineBeginLine > lines.size())
			throw new RuntimeException(info + " is not a xmlgen file");
	}

	public void printBeforeImport(PrintStream ps) {
		for (int i = 0; i < importBeginLine; ++i)
			ps.println(lines.get(i));
	}

	public void printImportToDefine(PrintStream ps) {
		for (int i = importBeginLine; i < defineBeginLine; ++i)
			ps.println(lines.get(i));
	}

	public void printAfterDefine(PrintStream ps) {
		for (int i = defineBeginLine; i < lines.size(); ++i)
			ps.println(lines.get(i));
	}

	@Override
	public void parseLine(String line) {
		// State old = state;
		switch (state) {
		case ImportBegin:
			skipUntil(line, State.ImportEnd);
			break;

		case DefineBegin:
			skipUntil(line, State.DefineEnd);
			break;

		case Others:
			State c = lineState(line);
			switch (c) {
			case ImportBegin:
				if (importBeginLine >= 0)
					throw new RuntimeException("too many import section");
				importBeginLine = lines.size();
				state = State.ImportBegin;
				break;
			case DefineBegin:
				if (defineBeginLine >= 0)
					throw new RuntimeException("too many define section");
				defineBeginLine = lines.size();
				state = State.DefineBegin;
				break;
			case Others:
				lines.add(line);
				break;
			default:
				throw new IllegalStateException(state + "->" + c);
			}
		default:
			break;
		}
		// System.out.println(" --- " + old + "->" + state + Main.quote(line));
	}

	private State lineState(String line) {
		if (line.indexOf(IMPORT_BEGIN) >= 0)
			return State.ImportBegin;
		if (line.indexOf(IMPORT_END) >= 0)
			return State.ImportEnd;
		if (line.indexOf(DEFINE_BEGIN) >= 0)
			return State.DefineBegin;
		if (line.indexOf(DEFINE_END) >= 0)
			return State.DefineEnd;
		return State.Others;
	}

	private void skipUntil(String line, State target) {
		State c = lineState(line);
		if (c == target)
			state = State.Others;
		else if (State.Others == c)
			; // System.out.println("------- skip " + state + line);
		else
			throw new IllegalStateException(state + "->" + c);
	}
}
