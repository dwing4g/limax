package limax.zdb.tool;

import java.util.Map;

import limax.xmlgen.Bean;
import limax.xmlgen.Cbean;
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
import limax.xmlgen.Xbean;

class Schemas {
	static SchemaKeyValue of(limax.xmlgen.Table table) {
		return new SchemaKeyValue(table.getKeyType(), table.getValueType());
	}

	static Schema of(Type type) {
		TypeVisitor v = new TypeVisitor();
		type.accept(v);
		return v.schema;
	}

	static final Schema schemaBoolean = new SchemaInt(IntType.BOOLEAN);
	static final Schema schemaByte = new SchemaInt(IntType.BYTE);
	static final Schema schemaShort = new SchemaInt(IntType.SHORT);
	static final Schema shcemaInt = new SchemaInt(IntType.INT);
	static final Schema schemaLong = new SchemaInt(IntType.LONG);

	static final Schema schemaDouble = new SchemaFloat(true);
	static final Schema schemaFloat = new SchemaFloat(false);

	static final Schema schemaBinary = new Schema() {
		@Override
		public DataBinary create() {
			return new DataBinary();
		}

		@Override
		public ConvertType diff(Schema t, boolean asKey) {
			return this == t ? ConvertType.SAME : ConvertType.MANUAL;
		}

		@Override
		public boolean isDynamic() {
			return false;
		}

		@Override
		public String toString() {
			return "byte[]";
		}
	};

	static final Schema schemaString = new Schema() {
		@Override
		public DataString create() {
			return new DataString();
		}

		@Override
		public ConvertType diff(Schema t, boolean asKey) {
			return this == t ? ConvertType.SAME : ConvertType.MANUAL;
		}

		@Override
		public boolean isDynamic() {
			return false;
		}

		@Override
		public String toString() {
			return "String";
		}
	};

	static String typeName(Schema schema) {
		if (schema instanceof SchemaCollection) {
			SchemaCollection s = ((SchemaCollection) schema);
			return s.typeName() + "<" + typeName(s.elementSchema()) + ">";
		} else if (schema instanceof SchemaBean) {
			return ((SchemaBean) schema).typeName();
		} else if (schema instanceof SchemaKeyValue) {
			SchemaKeyValue kv = (SchemaKeyValue) schema;
			return typeName(kv.keySchema()) + ", " + typeName(kv.valueSchema());
		} else {
			return schema.toString();
		}
	}

	static String str(Map<String, Schema> map, String prefix) {
		StringBuilder sb = new StringBuilder();
		map.forEach((name, s) -> {
			sb.append(prefix).append(name).append(":\t").append(Schemas.typeName(s));
			sb.append(System.lineSeparator());
			if (s instanceof SchemaCollection)
				s = ((SchemaCollection) s).elementSchema();
			if (s instanceof SchemaBean)
				sb.append(str(((SchemaBean) s).entries(), "\t" + prefix));
			else if (s instanceof SchemaKeyValue)
				sb.append(str(((SchemaKeyValue) s).entries(), "\t" + prefix));
		});
		return sb.toString();
	}

	private static class TypeVisitor implements limax.xmlgen.Visitor {
		private Schema schema;

		@Override
		public void visit(TypeBoolean type) {
			schema = schemaBoolean;
		}

		@Override
		public void visit(TypeByte type) {
			schema = schemaByte;
		}

		@Override
		public void visit(TypeShort type) {
			schema = schemaShort;
		}

		@Override
		public void visit(TypeInt type) {
			schema = shcemaInt;
		}

		@Override
		public void visit(TypeLong type) {
			schema = schemaLong;
		}

		@Override
		public void visit(TypeFloat type) {
			schema = schemaFloat;
		}

		@Override
		public void visit(TypeDouble type) {
			schema = schemaDouble;
		}

		@Override
		public void visit(TypeBinary type) {
			schema = schemaBinary;
		}

		@Override
		public void visit(TypeString type) {
			schema = schemaString;
		}

		@Override
		public void visit(TypeList type) {
			schema = new SchemaCollection("list", type.getValueType(), false);
		}

		@Override
		public void visit(TypeSet type) {
			schema = new SchemaCollection("set", type.getValueType(), true);
		}

		@Override
		public void visit(TypeVector type) {
			schema = new SchemaCollection("vector", type.getValueType(), false);
		}

		@Override
		public void visit(TypeMap type) {
			schema = new SchemaCollection("map", type.getKeyType(), type.getValueType());
		}

		@Override
		public void visit(Bean type) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void visit(Cbean type) {
			schema = new SchemaBean(type);
		}

		@Override
		public void visit(Xbean type) {
			schema = new SchemaBean(type);
		}

		@Override
		public void visit(TypeAny type) {
			throw new UnsupportedOperationException();
		}
	}
}
