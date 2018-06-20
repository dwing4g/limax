package limax.xmlgen;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import limax.util.ElementHelper;

public class Variable extends Naming implements Dependency {
	private final static Pattern pattern = Pattern.compile("[_a-zA-Z][_a-zA-Z0-9]*");
	private final static Set<String> keywords = new HashSet<>(Arrays.asList("Object", "abstract", "add", "alias", "and",
			"and_eq", "as", "ascending", "asm", "assert", "async", "auto", "await", "base", "bitand", "bitor", "bool",
			"boolean", "break", "byte", "case", "catch", "char", "checked", "class", "compl", "const", "const_cast",
			"continue", "decimal", "default", "delegate", "delete", "descending", "do", "double", "dynamic",
			"dynamic_cast", "else", "enum", "event", "explicit", "export", "extends", "extern", "false", "final",
			"finally", "fixed", "float", "for", "foreach", "friend", "get", "global", "goto", "group", "if",
			"implements", "implicit", "import", "in", "inline", "instanceof", "int", "interface", "internal", "into",
			"is", "join", "let", "lock", "long", "mutable", "namespace", "native", "new", "not", "not_eq", "null",
			"object", "operator", "or", "or_eq", "orderby", "out", "override", "package", "params", "partial",
			"private", "protected", "public", "readonly", "ref", "register", "reinterpret_cast", "remove", "return",
			"sbyte", "sealed", "select", "set", "short", "signed", "sizeof", "stackalloc", "static", "static_cast",
			"strictfp", "string", "struct", "super", "switch", "synchronized", "template", "this", "throw", "throws",
			"transient", "true", "try", "typedef", "typeid", "typename", "typeof", "uint", "ulong", "unchecked",
			"union", "unsafe", "unsigned", "ushort", "using", "value", "var", "virtual", "void", "volatile", "wchar_t",
			"where", "while", "xor", "xor_eq", "yield",
			// c++ class method names
			"marshal", "unmarshal", "trace", "encode", "getType", "process", "destroy", "equals", "Equals"));

	static void verifyName(Naming nm) {
		String name = nm.getName();
		if (!pattern.matcher(name).matches())
			throw new RuntimeException("invalid name \"" + name + "\"");
		if (keywords.contains(name))
			throw new RuntimeException("variable reserved keyword \"" + name + "\"");
		if (Main.scriptSupport && name.equals("onchange"))
			throw new RuntimeException("script reserved keyword \"" + name + "\"");
	}

	private String type;
	private String key = "";
	private String value = "";
	private String comment = "";
	private Type varType;
	private boolean json = true;

	// view
	private boolean clip;
	private boolean snapshot;

	// zdb
	private String foreign = "";
	private String capacity = "";

	// dynamic
	private boolean dynamic = false;
	private int serial = -1;
	private String script = "";

	public Variable(Protocol protocol, Element self) throws Exception {
		super(protocol, self);
		initialize(self);
	}

	public Variable(Bean bean, Element self) throws Exception {
		super(bean, self);
		initialize(self);
	}

	public Variable(Xbean bean, Element self) throws Exception {
		super(bean, self);
		initialize(self);
	}

	public Variable(Cbean bean, Element self) throws Exception {
		super(bean, self);
		initialize(self);
	}

	public Variable(View view, Element self) throws Exception {
		super(view, self);
		initialize(self);
	}

	public Variable(Control control, Element self) throws Exception {
		super(control, self);
		initialize(self);
	}

	public Variable(Variable variable, Element self) throws Exception {
		super(variable, self);
		initialize(self);
	}

	Variable(Naming parent, Element self) throws Exception {
		super(parent, self);
		initialize(self);
	}

	private void initialize(Element self) {
		ElementHelper eh = new ElementHelper(self);
		if (getParent() instanceof Variable)
			script = eh.getString("script").trim();
		else
			verifyName(this);
		type = eh.getString("type");
		key = eh.getString("key");
		value = eh.getString("value");
		clip = eh.getBoolean("clip", false);
		snapshot = eh.getBoolean("snapshot", false);
		json = eh.getBoolean("json", true);
		foreign = eh.getString("foreign");
		capacity = eh.getString("capacity");
		comment = extractComment(self);
		dynamic = eh.getBoolean("dynamic", false);
		serial = eh.getInt("serial", -1);
		eh.warnUnused("name");
	}

	static String extractComment(Element self) {
		String comment = "";
		Node c = Main.variableCommentPrevious ? self.getPreviousSibling() : self.getNextSibling();
		if (c != null && Node.TEXT_NODE == c.getNodeType()) {
			comment = c.getTextContent().trim().replaceAll("[\r\n]", "");
		}
		if (!comment.isEmpty())
			comment = " // " + comment;
		return comment;
	}

	public Variable(Xbean xbean, Variable oldvar) {
		super(xbean, oldvar.getName());
		initializeFromOther(oldvar);
	}

	public Variable(Cbean cbean, Variable oldvar) {
		super(cbean, oldvar.getName());
		initializeFromOther(oldvar);
	}

	private void initializeFromOther(Variable self) {
		type = self.getTypeString();
		key = self.getKey();
		value = self.getValue();
		foreign = self.getForeign();
		capacity = self.getCapacity();
	}

	public static final class Builder {
		Variable var;

		public Builder(Xbean xbean, String name, String type) {
			var = new Variable(xbean, name);
			var.type = type;
		}

		public Builder(Cbean cbean, String name, String type) {
			var = new Variable(cbean, name);
			var.type = type;
		}

		public Builder(Variable variable, String name, String type) {
			var = new Variable(variable, name);
			var.type = type;
		}

		public Variable build() {
			return var;
		}

		public Builder key(String key) {
			var.key = key;
			return this;
		}

		public Builder value(String value) {
			var.value = value;
			return this;
		}

		public Builder foreign(String foreign) {
			var.foreign = foreign;
			return this;
		}

		public Builder capacity(String capacity) {
			var.capacity = capacity;
			return this;
		}

		public Builder dynamic() {
			var.dynamic = true;
			return this;
		}

		public Builder script(String script) {
			var.script = script;
			return this;
		}

		public Builder serial(int serial) {
			var.serial = serial;
			return this;
		}
	}

	private Variable(Naming parent, String name) {
		super(parent, name);
	}

	public Type getType() {
		return varType;
	}

	public String getTypeString() {
		return type;
	}

	public String getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}

	public boolean isClip() {
		return clip;
	}

	public boolean isSnapshot() {
		return snapshot;
	}

	public String getComment() {
		return comment;
	}

	public boolean isJSONEnabled() {
		return json;
	}

	public String getForeign() {
		return foreign;
	}

	public String getCapacity() {
		return capacity;
	}

	public int getSerial() {
		return serial;
	}

	public String getScript() {
		return script;
	}

	@Override
	public boolean resolve() {
		if (dynamic) {
			Xbean xbean = (Xbean) getParent();
			if (!((Zdb) xbean.getParent()).isDynamic())
				throw new RuntimeException(
						"Use Xbean's dynamic variable, Element Zdb must set attribute dynamic=\"true\" first.");
			Variable[] vars = getChildren().stream().map(n -> (Variable) n)
					.sorted(Comparator.comparingInt(Variable::getSerial)).toArray(Variable[]::new);
			if (vars.length == 0)
				throw new RuntimeException(xbean.getFullName() + "." + getName() + " has not defined");
			if (vars[0].serial < 0)
				throw new RuntimeException(
						xbean.getFullName() + "." + getName() + "'s serial must be nonnegative but " + vars[0].serial);
			serial = vars[vars.length - 1].serial;
			varType = xbean.setMasterDynamicVariable(serial);
			return true;
		}
		if ((varType = Type.resolve(this, type, key, value)) == null)
			return false;
		if (getParent() instanceof Variable) {
			Variable var = (Variable) getParent();
			Xbean xbean = (Xbean) var.getParent();
			if (!xbean.appendDynamicVariable(var.getName(), serial, varType))
				throw new RuntimeException(
						xbean.getFullName() + "." + var.getName() + "'s serial " + serial + " is duplicated!");
		}
		return true;
	}

	@Override
	public void depends(Set<Type> types) {
		varType.depends(types);
	}

	public boolean isDynamic() {
		return dynamic;
	}

	void expandDynamicVariable(Map<Integer, Xbean.DynamicVariable> map) {
		Pattern pattern = Pattern.compile("\\$(\\w+)");
		getChildren().stream().map(n -> (Variable) n).forEach(var -> {
			Xbean.DynamicVariable current = map.get(var.serial);
			Matcher matcher = pattern.matcher(var.script);
			while (matcher.find()) {
				String key = matcher.group(1);
				Object depend = null;
				try {
					int serial = Integer.parseInt(key);
					if (map.containsKey(serial))
						depend = serial;
				} catch (Exception e) {
					if (((Xbean) getParent()).getStaticVariables().stream().map(Naming::getName)
							.filter(n -> n.equals(key)).findAny().isPresent())
						depend = key;
				}
				if (depend == null)
					throw new RuntimeException(((Xbean) getParent()).getFullName() + "." + getName()
							+ "'s script contains wrong symbol $" + key);
				if (depend instanceof Integer && var.serial <= (Integer) depend)
					throw new RuntimeException(((Xbean) getParent()).getFullName() + "." + getName()
							+ " has dependence error. serial = " + var.serial + " depend serial " + depend);
				StringBuffer sb = new StringBuffer();
				matcher.appendReplacement(sb, "");
				current.script(sb.toString()).depend(depend);
			}
			StringBuffer sb = new StringBuffer();
			matcher.appendTail(sb);
			current.script(sb.toString());
		});
	}
}
