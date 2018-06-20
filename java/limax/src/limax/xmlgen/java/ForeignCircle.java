package limax.xmlgen.java;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import limax.xmlgen.Cbean;
import limax.xmlgen.Table;
import limax.xmlgen.Type;
import limax.xmlgen.Variable;
import limax.xmlgen.Xbean;
import limax.xmlgen.Zdb;

class ForeignCircle {

	static void verify(Zdb zdb) {
		for (Table t : zdb.getTables())
			new ForeignCircle(zdb, t).detect(t);
	}

	private Zdb zdb;
	private Table origin;
	// order by add
	private Map<String, Set<String>> detected = new LinkedHashMap<String, Set<String>>();

	ForeignCircle(Zdb zdb, Table origin) {
		this.zdb = zdb;
		this.origin = origin;
	}

	Set<String> foreigns(Table table) {
		Set<String> foreigns = getTableForeigns(table);
		detected.put(table.getName(), foreigns);
		if (foreigns.contains(origin.getName())) {
			for (Map.Entry<String, Set<String>> e : detected.entrySet()) {
				if (false == e.getValue().isEmpty())
					System.err.println(origin.getName() + " " + e);
			}
			throw new IllegalStateException("foreign circle found.");
		}
		return foreigns;
	}

	Set<String> getTableForeigns(Table table) {
		Set<String> foreigns = new HashSet<String>();
		Foreign.Conf f = new Foreign.Conf(table.getForeign(), "");
		if (f.getKey() != null)
			foreigns.add(f.getKey());
		if (f.getValue() != null)
			foreigns.add(f.getValue());

		Set<Type> types = new HashSet<Type>();
		table.getKeyType().depends(types);
		table.getValueType().depends(types);
		for (Type type : types) {
			if (type instanceof Cbean) {
				collectBeanForeigns(((Cbean) type).getVariables(), foreigns);
			} else if (type instanceof Xbean) {
				collectBeanForeigns(((Xbean) type).getVariables(), foreigns);
			}
		}
		return foreigns;
	}

	void collectBeanForeigns(List<Variable> variables, Set<String> foreigns) {
		for (Variable var : variables) {
			Foreign.Conf f = new Foreign.Conf(var.getForeign(), "");
			if (null != f.getKey())
				foreigns.add(f.getKey());
			if (null != f.getValue())
				foreigns.add(f.getValue());
		}
	}

	boolean contains(Table table) {
		return detected.containsKey(table.getName());
	}

	void detect(Table table) {
		if (null == table)
			return; // maybe null. see TypeBinary

		if (contains(table))
			return;

		Set<String> foreigns = foreigns(table);
		if (foreigns.contains(table.getName()))
			throw new IllegalStateException(
					"foreign to self is not supported!" + "NO null value in ZDB. table=" + table.getName());

		for (String foreign : foreigns)
			detect(zdb.getTable(foreign));
	}

}
