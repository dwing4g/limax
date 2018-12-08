package limax.executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class Options {

	public static class Option {
		private String name;
		private String valueName;
		private String description;
		private String valueDefault;
		private String value = null;
		private boolean present = false;

		public boolean isPresent() {
			return present;
		}

		public void setPresent() {
			present = true;
		}

		public Option(String[] option) {
			if (option.length != 4)
				throw new java.lang.IllegalArgumentException();
			setParams(option[0], option[1], option[2], option[3]);
		}

		private void setParams(String name, String valueName, String description, String valueDefault) {
			if (name != null && !Option.isOption(name))
				throw new IllegalArgumentException("Option name must startsWith '-'. name=" + name);
			this.name = name;
			this.valueName = valueName;
			this.description = description;
			this.valueDefault = valueDefault;
		}

		public Option(String name, String valueName, String description, String valueDefault) {
			setParams(name, valueName, description, valueDefault);
		}

		public boolean hasValue() {
			return valueName != null;
		}

		public String getName() {
			return name;
		}

		public static boolean isOption(String arg) {
			return arg.startsWith("-");
		}

		public String getValueName() {
			return valueName;
		}

		public String getDescription() {
			return description;
		}

		public String getValueDefault() {
			return valueDefault;
		}

		@Override
		public String toString() {
			return name;
		}

		public String getValuePresent() {
			return value;
		}

		public String getValue() {
			if (false == hasValue())
				throw new java.lang.IllegalStateException(this + " : no value");
			if (null != value)
				return value;
			if (valueDefault != null && valueDefault.equals("!"))
				throw new IllegalArgumentException(this + " : value required!");
			return valueDefault;
		}

		public void setValue(String value) {
			if (false == hasValue())
				throw new IllegalStateException(this + " : no value");
			this.value = value;
		}
	}

	private Map<String, Option> options = new HashMap<String, Option>();
	private List<String> tokens = new LinkedList<String>();
	private List<Option> ordered = new ArrayList<Option>();
	private int maxOptionNameLength = 0;
	private int maxValueNameLength = 0;

	public Options() {
	}

	public Options(String[][] options) {
		add(options);
	}

	public void add(String[][] options) {
		for (String[] option : options)
			add(option);
	}

	public void add(String[] option) {
		add(new Option(option));
	}

	public void add(Option option) {
		if (option.getName() != null && null != options.put(option.getName(), option))
			throw new RuntimeException(option + " : duplicate name");
		ordered.add(option);
		if (option.getName() != null && option.getName().length() > maxOptionNameLength)
			maxOptionNameLength = option.getName().length();
		if (option.getValueName() != null && option.getValueName().length() > maxValueNameLength)
			maxValueNameLength = option.getValueName().length();
	}

	public void parse(String[] args) {
		parse(args, 0);
	}

	public void parse(String[] args, int offset) {
		Option last = null;
		for (int i = offset; i < args.length; ++i) {
			String arg = args[i];
			if (Option.isOption(arg)) {
				last = options.get(arg);
				if (null == last)
					throw new IllegalArgumentException(arg + " : unknown option");
				last.setPresent();
			} else if (null != last) {
				last.setValue(arg);
				last = null;
			} else {
				tokens.add(arg);
			}
		}
		for (Option option : options.values())
			if (option.hasValue())
				option.getValue();
	}

	private static char[] spaceN(int n, String s) {
		n = n - (null == s ? 0 : s.length());
		if (n < 1)
			n = 1;
		char spaces[] = new char[n];
		Arrays.fill(spaces, ' ');
		return spaces;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		String lineSeprarator = System.getProperty("line.separator", "\n");
		int nl = maxOptionNameLength + 2;
		int vl = maxValueNameLength + 2;
		for (Option opt : ordered) {
			sb.append("    ");
			if (null != opt.getName())
				sb.append(opt.getName());
			sb.append(spaceN(nl, opt.getName()));
			if (null != opt.getValueName())
				sb.append(opt.getValueName());
			sb.append(spaceN(vl, opt.getValueName())).append(": ").append(opt.getDescription());
			if (opt.hasValue() && opt.getValueDefault() != null) {
				if (opt.getValueDefault().equals("!"))
					sb.append(" :REQUIRED!");
				else
					sb.append(" :DEFAULT=").append(opt.getValueDefault());
			}
			sb.append(lineSeprarator);
		}
		return sb.toString();
	}

	public List<String> getTokens() {
		return tokens;
	}

	public boolean hasToken() {
		return false == tokens.isEmpty();
	}

	public String getValue(String optname) {
		Option option = getOption(optname);
		if (option.hasValue())
			return option.getValue();
		return option.isPresent() ? "" : null;
	}

	public Option getOption(String optname) {
		Option option = options.get(optname);
		if (null == option)
			throw new RuntimeException(optname + " : unknown option");
		return option;
	}

}