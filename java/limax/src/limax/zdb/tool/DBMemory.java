package limax.zdb.tool;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import limax.util.StringUtils;
import limax.util.Trace;
import limax.xmlgen.Bean;
import limax.xmlgen.CapacityConf;
import limax.xmlgen.Cbean;
import limax.xmlgen.Table;
import limax.xmlgen.TypeAny;
import limax.xmlgen.TypeBinary;
import limax.xmlgen.TypeBoolean;
import limax.xmlgen.TypeByte;
import limax.xmlgen.TypeDouble;
import limax.xmlgen.TypeFloat;
import limax.xmlgen.TypeInt;
import limax.xmlgen.TypeList;
import limax.xmlgen.TypeLong;
import limax.xmlgen.TypeMap;
import limax.xmlgen.TypeSet;
import limax.xmlgen.TypeShort;
import limax.xmlgen.TypeString;
import limax.xmlgen.TypeVector;
import limax.xmlgen.Variable;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;
import limax.xmlgen.Zdb;

class DBMemory {

	static long sizeof(limax.xmlgen.Type type, CapacityConf cc) {
		Sizeof s = new Sizeof(cc);
		type.accept(s);
		return s.size;
	}

	static class Sizeof implements Visitor {
		private long size;
		private CapacityConf cc;

		Sizeof(CapacityConf cc) {
			this.cc = cc;
		}

		@Override
		public void visit(TypeBoolean type) {
			size = 1;
		}

		@Override
		public void visit(TypeByte type) {
			size = 1;
		}

		@Override
		public void visit(TypeShort type) {
			size = 2;
		}

		@Override
		public void visit(TypeInt type) {
			size = 4;
		}

		@Override
		public void visit(TypeLong type) {
			size = 8;
		}

		@Override
		public void visit(TypeFloat type) {
			size = 4;
		}

		@Override
		public void visit(TypeDouble type) {
			size = 8;
		}

		@Override
		public void visit(TypeBinary type) {
			size = cc.getCapacity();
		}

		@Override
		public void visit(TypeString type) {
			size = cc.getCapacity();
		}

		@Override
		public void visit(TypeList type) {
			size = cc.getCapacity() * sizeof(type.getValueType(), new CapacityConf(cc.getValue(), null, null));
		}

		@Override
		public void visit(TypeSet type) {
			size = cc.getCapacity() * sizeof(type.getValueType(), new CapacityConf(cc.getValue(), null, null));
		}

		@Override
		public void visit(TypeVector type) {
			size = cc.getCapacity() * sizeof(type.getValueType(), cc.getValueConf());
		}

		@Override
		public void visit(TypeMap type) {
			size = cc.getCapacity()
					* (sizeof(type.getKeyType(), cc.getKeyConf()) + sizeof(type.getValueType(), cc.getValueConf()));
		}

		@Override
		public void visit(Bean type) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void visit(Cbean type) {
			for (Variable v : type.getVariables()) {
				size += sizeof(v.getType(), new CapacityConf(v.getCapacity()));
			}
		}

		@Override
		public void visit(Xbean type) {
			for (Variable v : type.getVariables()) {
				size += sizeof(v.getType(), new CapacityConf(v.getCapacity()));
			}
		}

		@Override
		public void visit(TypeAny type) {
			size = cc.getCapacity();
		}

	}

	static class Item {
		long key = 0;
		long value = 0;
		long table = 0;
		long cache = 0;
		long capacity = 0;
	}

	static class Percent {
		double key = 0;
		double value = 0;
		double table = 0;
		double cache = 0;
		double capacity = 0;
	}

	static class TableSize {
		private final limax.xmlgen.Table table;

		Item columns = new Item();
		Percent percent = new Percent();

		TableSize(limax.xmlgen.Table table, Item total) {
			this.table = table;

			// size
			CapacityConf cc = new CapacityConf(table.getCapacity());
			columns.key = sizeof(table.getKeyType(), cc.getKeyConf());
			columns.value = sizeof(table.getValueType(), cc.getValueConf());
			columns.table = columns.key + columns.value;
			columns.capacity = table.getCacheCapValue();
			columns.cache = columns.capacity * columns.table;

			// total
			total.key += columns.key;
			total.value += columns.value;
			total.table += columns.table;
			total.capacity += columns.capacity;
			total.cache += columns.cache;
		}

		void percent(Item total) {
			percent.key = (double) columns.key / total.key;
			percent.value = (double) columns.value / total.value;
			percent.table = (double) (columns.table) / total.table;
			percent.capacity = (double) columns.capacity / total.capacity;
			percent.cache = (double) (columns.cache) / total.cache;
		}

		public static void formatHeaders(List<String> cols) {
			cols.add("name");
			cols.add("table");
			cols.add("table%");
			cols.add("cache");
			cols.add("cache%");
			cols.add("capacity");
			cols.add("capacity%");
		}

		public void formatColumns(List<String> cols) {
			cols.add(table.getName());
			cols.add(String.valueOf(columns.table));
			cols.add(String.format("%.4f", percent.table));
			cols.add(String.valueOf(columns.cache));
			cols.add(String.format("%.4f", percent.cache));
			cols.add(String.valueOf(columns.capacity));
			cols.add(String.format("%.4f", percent.capacity));
		}
	}

	static class Comp implements Comparator<TableSize> {
		private final String name;
		private final boolean percent;
		private Class<?> pclass = Percent.class;
		private Class<?> iclass = Item.class;

		public Comp(String name) {
			this.percent = name.endsWith("%");
			this.name = this.percent ? name.substring(0, name.length() - 1) : name;
		}

		@Override
		public int compare(TableSize o1, TableSize o2) {
			try {
				if (name.isEmpty())
					return o1.table.getName().compareTo(o2.table.getName());

				if (percent) {
					double v1 = (Double) pclass.getDeclaredField(name).get(o1.percent);
					double v2 = (Double) pclass.getDeclaredField(name).get(o2.percent);
					return Long.signum((long) (v2 * 10000 - v1 * 10000));
				}
				long v1 = (Long) iclass.getDeclaredField(name).get(o1.columns);
				long v2 = (Long) iclass.getDeclaredField(name).get(o2.columns);
				return Long.signum(v2 - v1);
			} catch (Exception x) {
				throw new RuntimeException(x);
			}
		}

		@Override
		public boolean equals(Object obj) {
			return this == obj;
		}
	}

	private static char[] spaceN(int n, String s) {
		n = n - (null == s ? 0 : s.length());
		if (n < 1)
			n = 1;
		char spaces[] = new char[n];
		java.util.Arrays.fill(spaces, ' ');
		return spaces;
	}

	public static void dump(limax.xmlgen.Zdb meta, String orderby, PrintStream out) throws Exception {

		CapVisitor.verify(meta, out);
		// compute size and sum total
		TableSize[] tables = new TableSize[meta.getTables().size()];
		Item total = new Item();
		{
			int i = 0;
			for (limax.xmlgen.Table table : meta.getTables())
				tables[i++] = new TableSize(table, total);
			for (TableSize table : tables)
				table.percent(total);

			Arrays.sort(tables, new Comp(orderby));
		}

		// format header
		List<String> headers = new ArrayList<>();
		TableSize.formatHeaders(headers);
		List<Integer> length = headers.stream().map(String::length).collect(Collectors.toList());

		// format and record max-column-length
		List<List<String>> rows = new ArrayList<>();
		rows.add(headers);
		for (TableSize table : tables) {
			List<String> cols = new ArrayList<>();
			table.formatColumns(cols);
			for (int i = 0; i < cols.size(); ++i) {
				if (cols.get(i).length() > length.get(i))
					length.set(i, cols.get(i).length());
			}
			rows.add(cols);
		}

		// print tables
		for (List<String> cols : rows) {
			for (int i = 0; i < cols.size(); ++i) {
				String col = cols.get(i);
				out.print(col);
				out.print(spaceN(length.get(i) + 4, col));
			}
			out.println();
		}

		// print total
		out.println("-------------------------------------------------------------------");
		out.println("TOTAL");
		out.println(String.format("	cache: %dM table<key, value>: %dK<%d, %dK>", total.cache / 1024 / 1024,
				total.table / 1024, total.key, total.value / 1024));
		out.println("	TABLE-COUNT: " + rows.size());
		out.println("-------------------------------------------------------------------");
	}

	static class CapConf extends CapacityConf {
		private final String message;
		private final PrintStream out;

		@Override
		public void throwIf(boolean condition, String more) {
			if (condition)
				throw new IllegalArgumentException("invalid capacity! " + message + " info=" + more);
		}

		public void warnIf(boolean condition, String more) {
			if (condition)
				Trace.warn(message + " info=" + more);
		}

		public void notNeed() {
			this.warnIf(super.getCapacity() != null || super.getKey() != null || super.getValue() != null,
					"capacity is not required.");
		}

		public void capacityNeed() {
			this.warnIf(super.getCapacity() == null, "capacity is required.");
		}

		public void keyNotNeed() {
			this.warnIf(super.getKey() != null, "capacity.key is not required.");
		}

		public void valueNotNeed() {
			this.warnIf(super.getValue() != null, "capacity.value is not required.");
		}

		public void capacityOnly() {
			this.capacityNeed();
			this.keyNotNeed();
			this.valueNotNeed();
		}

		public CapConf extractKey() {
			return new CapConf(super.getKey(), null, null, this.message + "<key>", out);
		}

		public CapConf extractValue() {
			return new CapConf(super.getValue(), null, null, this.message + "<value>", out);
		}

		private CapConf(Integer capacity, Integer key, Integer value, String message, PrintStream out) {
			super(capacity, key, value);
			this.message = message;
			this.out = out;
		}

		public CapConf(String conf, String context, PrintStream out) {
			super(conf);
			this.message = "CAPACITY conf=" + StringUtils.quote(conf) + " name=" + context;
			this.out = out;
		}
	}

	static class CapVisitor implements Visitor {
		static void verify(Zdb zdb, PrintStream out) {
			for (Variable var : zdb.getDescendantVariables()) {
				String s = "bean." + var.getParent().getName() + "." + var.getName();
				var.getType().accept(new CapVisitor(new CapConf(var.getCapacity(), s, out)));
			}

			for (Table t : zdb.getTables()) {
				CapConf cap = new CapConf(t.getCapacity(), "table." + t.getName(), out);
				t.getKeyType().accept(new CapVisitor(cap.extractKey()));
				t.getValueType().accept(new CapVisitor(cap.extractValue()));
				cap.warnIf(null != cap.getCapacity(), "capacity is not required.");
			}
		}

		CapConf conf;

		CapVisitor(CapConf cap) {
			conf = cap;
		}

		@Override
		public void visit(Cbean type) {
			conf.notNeed();
		}

		@Override
		public void visit(Xbean type) {
			conf.notNeed();
		}

		@Override
		public void visit(TypeString type) {
			conf.capacityOnly();
		}

		@Override
		public void visit(TypeBinary type) {
			conf.capacityOnly();
		}

		@Override
		public void visit(TypeBoolean type) {
			conf.notNeed();
		}

		@Override
		public void visit(TypeByte type) {
			conf.notNeed();
		}

		@Override
		public void visit(TypeShort type) {
			conf.notNeed();
		}

		@Override
		public void visit(TypeInt type) {
			conf.notNeed();
		}

		@Override
		public void visit(TypeLong type) {
			conf.notNeed();
		}

		@Override
		public void visit(TypeFloat type) {
			conf.notNeed();
		}

		@Override
		public void visit(TypeDouble type) {
			conf.notNeed();
		}

		@Override
		public void visit(TypeList type) {
			conf.capacityNeed();
			conf.keyNotNeed();
			type.getValueType().accept(new CapVisitor(conf.extractValue()));
		}

		@Override
		public void visit(TypeVector type) {
			conf.capacityNeed();
			conf.keyNotNeed();
			type.getValueType().accept(new CapVisitor(conf.extractValue()));
		}

		@Override
		public void visit(TypeSet type) {
			conf.capacityNeed();
			conf.keyNotNeed();
			type.getValueType().accept(new CapVisitor(conf.extractValue()));
		}

		@Override
		public void visit(TypeMap type) {
			conf.capacityNeed();
			type.getKeyType().accept(new CapVisitor(conf.extractKey()));
			type.getValueType().accept(new CapVisitor(conf.extractValue()));
		}

		@Override
		public void visit(TypeAny type) {
			conf.capacityOnly();
		}

		@Override
		public void visit(Bean type) {
			throw new UnsupportedOperationException();
		}
	}
}
