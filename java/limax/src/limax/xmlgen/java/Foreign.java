package limax.xmlgen.java;

import limax.util.StringUtils;
import limax.util.Trace;
import limax.xmlgen.Bean;
import limax.xmlgen.Cbean;
import limax.xmlgen.ForeignConf;
import limax.xmlgen.Table;
import limax.xmlgen.Type;
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

class Foreign implements Visitor {

	static void verify(Zdb zdb) {
		for (Variable var : zdb.getDescendantVariables()) {
			String f = var.getForeign();
			if (!f.isEmpty()) {
				String s = "bean." + var.getParent().getName() + "." + var.getName();
				var.getType().accept(new Foreign(zdb, new Conf(f, s)));
			}
		}

		for (Table t : zdb.getTables()) {
			String f = t.getForeign();
			if (!f.isEmpty()) {
				Conf uf = new Conf(f, "table=" + t.getName());
				if (uf.getKey() != null)
					t.getKeyType().accept(new Foreign(zdb, new Conf(uf, uf.getKey())));
				if (uf.getValue() != null)
					t.getValueType().accept(new Foreign(zdb, new Conf(uf, uf.getValue())));
			}
		}
	}

	/**
	 * foreign format = [[key:tableName];][[value:]tableName]
	 */
	static class Conf extends ForeignConf {
		private final String message;

		@Override
		public void throwIf(boolean condition, String more) {
			if (condition)
				throw new IllegalArgumentException("invalid foreign! " + message + " " + more);
		}

		public void warn(String more) {
			Trace.warn(message + " " + more);
		}

		public Conf(Conf foreign, String value) {
			super(null, value);
			this.message = foreign.message;
		}

		public Conf(String conf, String context) {
			super(conf);
			this.message = "FOREIGN conf=" + StringUtils.quote(conf) + " name=" + context;
		}
	}

	Zdb zdb;
	Conf conf;

	Foreign(Zdb zdb, Conf foreign) {
		this.zdb = zdb;
		this.conf = foreign;
	}

	@Override
	public void visit(Cbean type) {
		_verify(type, conf.getValue(), "cbean");
	}

	@Override
	public void visit(Xbean type) {
		_verify(type, conf.getValue(), "xbean");
	}

	private void _verify(Type type, String tablename, String tag) {
		Table table = zdb.getTable(tablename);
		conf.throwIf(null == table, "[" + tag + "] table not exist.");
		conf.throwIf(table.isMemory(), "[" + tag + "] foreign table is memory");
		conf.throwIf(table.getKeyType() != type, "[" + tag + "] type not match");
	}

	private void verify(Type type) {
		conf.throwIf(null != conf.getKey(), type.getName() + " need value only.");
		if (null != conf.getValue())
			_verify(type, conf.getValue(), type.getName());

	}

	private void verify(Type type, Type value) {
		conf.throwIf(null != conf.getKey(), "[" + type.getName() + "]" + " need value only.");
		if (null != conf.getValue())
			_verify(value, conf.getValue(), type.getName() + ".value");
	}

	private void verify(Type type, Type key, Type value) {
		if (null != conf.getKey())
			_verify(key, conf.getKey(), type.getName() + ".key");
		if (null != conf.getValue())
			_verify(value, conf.getValue(), type.getName() + ".value");
	}

	@Override
	public void visit(TypeString type) {
		verify(type);
	}

	@Override
	public void visit(TypeBinary type) {
		conf.warn("[binary] this foreign is unverifiable.");
	}

	@Override
	public void visit(TypeBoolean type) {
		verify(type);
	}

	@Override
	public void visit(TypeByte type) {
		verify(type);
	}

	@Override
	public void visit(TypeShort type) {
		verify(type);
	}

	@Override
	public void visit(TypeInt type) {
		verify(type);
	}

	@Override
	public void visit(TypeLong type) {
		verify(type);
	}

	@Override
	public void visit(TypeFloat type) {
		verify(type);

	}

	@Override
	public void visit(TypeDouble type) {
		verify(type);
	}

	@Override
	public void visit(TypeList type) {
		verify(type, type.getValueType());

	}

	@Override
	public void visit(TypeVector type) {
		verify(type, type.getValueType());
	}

	@Override
	public void visit(TypeSet type) {
		verify(type, type.getValueType());
	}

	@Override
	public void visit(TypeMap type) {
		verify(type, type.getKeyType(), type.getValueType());
	}

	@Override
	public void visit(TypeAny type) {
		conf.throwIf(true, "[any] unsupported.");
	}

	@Override
	public void visit(Bean type) {
		throw new UnsupportedOperationException();
	}

}
