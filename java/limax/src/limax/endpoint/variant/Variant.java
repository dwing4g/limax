package limax.endpoint.variant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

import limax.codec.JSONBuilder;
import limax.codec.JSONMarshal;
import limax.codec.Octets;
import limax.util.Helper;

public final class Variant implements JSONMarshal {

	public final static Variant Null = new Variant(Data.nullData);
	public final static Variant True = new Variant(BooleanData.trueData);
	public final static Variant False = new Variant(BooleanData.falseData);

	private final Data data;

	private Variant(Data data) {
		this.data = data;
	}

	private Variant(Collection<Variant> v, Declaration decl) {
		data = new CollectionData(v, decl);
	}

	private Variant(Map<Variant, Variant> v, Declaration decl) {
		data = new MapData(v, decl);
	}

	public static Variant create(byte v) {
		return new Variant(new NumberData<Byte>(v, VariantType.Byte));
	}

	public static Variant create(short v) {
		return new Variant(new NumberData<Short>(v, VariantType.Short));
	}

	public static Variant create(int v) {
		return new Variant(new NumberData<Integer>(v, VariantType.Int));
	}

	public static Variant create(long v) {
		return new Variant(new NumberData<Long>(v, VariantType.Long));
	}

	public static Variant create(float v) {
		return new Variant(new NumberData<Float>(v, VariantType.Float));
	}

	public static Variant create(double v) {
		return new Variant(new NumberData<Double>(v, VariantType.Double));
	}

	public static <V extends Number> Variant create(V v) {
		if (v instanceof Byte)
			return new Variant(new NumberData<Byte>((Byte) v, VariantType.Byte));
		else if (v instanceof Short)
			return new Variant(new NumberData<Short>((Short) v, VariantType.Short));
		else if (v instanceof Integer)
			return new Variant(new NumberData<Integer>((Integer) v, VariantType.Int));
		else if (v instanceof Long)
			return new Variant(new NumberData<Long>((Long) v, VariantType.Long));
		else if (v instanceof Float)
			return new Variant(new NumberData<Float>((Float) v, VariantType.Float));
		else if (v instanceof Double)
			return new Variant(new NumberData<Double>((Double) v, VariantType.Double));
		else
			throw new IllegalArgumentException();
	}

	public static Variant create(boolean v) {
		return v ? True : False;
	}

	public static Variant create(String v) {
		return new Variant(new StringData(v));
	}

	public static Variant create(Octets v) {
		return new Variant(new OctetsData(v));
	}

	public static Variant createList() {
		return new Variant(new LinkedList<Variant>(), DeclarationImpl.createList(DeclarationImpl.Object));
	}

	public static Variant createVector() {
		return new Variant(new ArrayList<Variant>(), DeclarationImpl.createVector(DeclarationImpl.Object));
	}

	public static Variant createSet() {
		return new Variant(new HashSet<Variant>(), DeclarationImpl.createSet(DeclarationImpl.Object));
	}

	public static Variant createMap() {
		return new Variant(new HashMap<Variant, Variant>(),
				DeclarationImpl.createMap(DeclarationImpl.Object, DeclarationImpl.Object));
	}

	public static Variant createStruct() {
		return new Variant(new StructData());
	}

	public boolean getBooleanValue() {
		return data.getBooleanValue();
	}

	public byte getByteValue() {
		return data.getByteValue();
	}

	public short getShortValue() {
		return data.getShortValue();
	}

	public int getIntValue() {
		return data.getIntValue();
	}

	public long getLongValue() {
		return data.getLongValue();
	}

	public float getFloatValue() {
		return data.getFloatValue();
	}

	public double getDoubleValue() {
		return data.getDoubleValue();
	}

	public String getStringValue() {
		return data.getStringValue();
	}

	public Octets getOctetsValue() {
		return data.getOctetsValue();
	}

	public Collection<Variant> getCollectionValue() {
		return data.getListValue();
	}

	public Map<Variant, Variant> getMapValue() {
		return data.getMapValue();
	}

	public boolean getBoolean(String name) {
		return data.getBoolean(name);
	}

	public byte getByte(String name) {
		return data.getByte(name);
	}

	public short getShort(String name) {
		return data.getShort(name);
	}

	public int getInt(String name) {
		return data.getInt(name);
	}

	public long getLong(String name) {
		return data.getLong(name);
	}

	public float getFloat(String name) {
		return data.getFloat(name);
	}

	public double getDouble(String name) {
		return data.GetDouble(name);
	}

	public String getString(String name) {
		return data.GetString(name);
	}

	public Octets getOctets(String name) {
		return data.getOctets(name);
	}

	public Variant getVariant(String name) {
		return data.getVariant(name);
	}

	public boolean isNull() {
		return Data.nullData == data;
	}

	public final Collection<Variant> getCollection(String name) {
		return data.getCollection(name);
	}

	public final Map<Variant, Variant> getMap(String name) {
		return data.getMap(name);
	}

	public <V extends Number> void collectionInsert(V v) {
		collectionInsert(create(v));
	}

	public void collectionInsert(boolean v) {
		collectionInsert(create(v));
	}

	public void collectionInsert(String v) {
		collectionInsert(create(v));
	}

	public void collectionInsert(Octets v) {
		collectionInsert(create(v));
	}

	public void collectionInsert(Variant v) {
		data.listInsert(v);
	}

	public <K extends Number> Variant mapInsert(K k, boolean v) {
		return mapInsert(create(k), create(v));
	}

	public <K extends Number> Variant mapInsert(K k, Variant v) {
		return mapInsert(create(k), v);
	}

	public <K extends Number, V extends Number> Variant mapInsert(K k, V v) {
		return mapInsert(create(k), create(v));
	}

	public <K extends Number> Variant mapInsert(K k, String v) {
		return mapInsert(create(k), create(v));
	}

	public <K extends Number> Variant mapInsert(K k, Octets v) {
		return mapInsert(create(k), create(v));
	}

	public Variant mapInsert(String k, Variant v) {
		return mapInsert(create(k), v);
	}

	public Variant mapInsert(String k, boolean v) {
		return mapInsert(create(k), create(v));
	}

	public <V extends Number> Variant mapInsert(String k, V v) {
		return mapInsert(create(k), create(v));
	}

	public Variant mapInsert(String k, String v) {
		return mapInsert(create(k), create(v));
	}

	public Variant mapInsert(String k, Octets v) {
		return mapInsert(create(k), create(v));
	}

	public Variant mapInsert(Variant k, Variant v) {
		return data.mapInsert(k, v);
	}

	public void structSetValue(String name, Variant v) {
		data.structSetValue(name, v);
	}

	public <V extends Number> void setValue(String name, V v) {
		structSetValue(name, create(v));
	}

	public void setValue(String name, boolean v) {
		structSetValue(name, create(v));
	}

	public void setValue(String name, String v) {
		structSetValue(name, create(v));
	}

	public void setValue(String name, Octets v) {
		structSetValue(name, create(v));
	}

	public void setValue(String name, Variant v) {
		structSetValue(name, v);
	}

	public Variant copy() {
		return new Variant(data.copy());
	}

	@Override
	public int hashCode() {
		return data.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Variant) {
			final Variant v = (Variant) obj;
			return v.data.equals(data);
		}
		return false;
	}

	@Override
	public String toString() {
		return data.toString();
	}

	public Declaration makeDeclaration() {
		return data.makeDeclaration();
	}

	public VariantType getVariantType() {
		return data.getVariantType();
	}

	private static class Data implements JSONMarshal {
		public final static Data nullData = new Data();

		public Declaration makeDeclaration() {
			return DeclarationImpl.Null;
		}

		public VariantType getVariantType() {
			return VariantType.Null;
		}

		public boolean getBooleanValue() {
			return false;
		}

		public byte getByteValue() {
			return 0;
		}

		public short getShortValue() {
			return 0;
		}

		public int getIntValue() {
			return 0;
		}

		public long getLongValue() {
			return 0L;
		}

		public float getFloatValue() {
			return 0.0f;
		}

		public double getDoubleValue() {
			return 0.0;
		}

		public String getStringValue() {
			return "";
		}

		private final static Octets nullOctets = new Octets();

		public Octets getOctetsValue() {
			return nullOctets;
		}

		public Collection<Variant> getListValue() {
			return Collections.emptyList();
		}

		public Map<Variant, Variant> getMapValue() {
			return Collections.emptyMap();
		}

		public final boolean getBoolean(String name) {
			return getVariant(name).getBooleanValue();
		}

		public final byte getByte(String name) {
			return getVariant(name).getByteValue();
		}

		public final short getShort(String name) {
			return getVariant(name).getShortValue();
		}

		public final int getInt(String name) {
			return getVariant(name).getIntValue();
		}

		public final long getLong(String name) {
			return getVariant(name).getLongValue();
		}

		public final float getFloat(String name) {
			return getVariant(name).getFloatValue();
		}

		public final double GetDouble(String name) {
			return getVariant(name).getDoubleValue();
		}

		public final String GetString(String name) {
			return getVariant(name).getStringValue();
		}

		public final Octets getOctets(String name) {
			return getVariant(name).getOctetsValue();
		}

		public Variant getVariant(String name) {
			return Null;
		}

		public final Collection<Variant> getCollection(String name) {
			return getVariant(name).getCollectionValue();
		}

		public final Map<Variant, Variant> getMap(String name) {
			return getVariant(name).getMapValue();
		}

		public void listInsert(Variant v) {
		}

		public Variant mapInsert(Variant k, Variant v) {
			return Null;
		}

		public void structSetValue(String name, Variant v) {

		}

		public Data copy() {
			return this;
		}

		@Override
		public String toString() {
			return "null";
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Data ? getVariantType() == ((Data) obj).getVariantType() : false;
		}

		@Override
		public JSONBuilder marshal(JSONBuilder jb) {
			return jb.append((Object) null);
		}
	}

	private static class BooleanData extends Data {
		public final static BooleanData trueData = new BooleanData(true);
		public final static BooleanData falseData = new BooleanData(false);

		private final boolean value;

		private BooleanData(boolean v) {
			value = v;
		}

		@Override
		public boolean getBooleanValue() {
			return value;
		}

		@Override
		public Declaration makeDeclaration() {
			return DeclarationImpl.Boolean;
		}

		@Override
		public VariantType getVariantType() {
			return VariantType.Boolean;
		}

		@Override
		public byte getByteValue() {
			return (byte) getIntValue();
		}

		@Override
		public short getShortValue() {
			return (short) getIntValue();
		}

		@Override
		public int getIntValue() {
			return value ? 1 : 0;
		}

		@Override
		public long getLongValue() {
			return getIntValue();
		}

		@Override
		public int hashCode() {
			return Boolean.valueOf(value).hashCode();
		}

		@Override
		public String getStringValue() {
			return Boolean.toString(value);
		}

		@Override
		public String toString() {
			return Boolean.toString(value);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof BooleanData) {
				final BooleanData data = (BooleanData) obj;
				return data.value == value;
			}
			return false;
		}

		@Override
		public JSONBuilder marshal(JSONBuilder jb) {
			return jb.append(value);
		}
	}

	private static class NumberData<E extends Number> extends Data {
		private final E value;
		private final VariantType type;

		public NumberData(E v, VariantType t) {
			value = v;
			type = t;
		}

		@Override
		public VariantType getVariantType() {
			return type;
		}

		@Override
		public Declaration makeDeclaration() {
			return type.createDeclaration();
		}

		@Override
		public byte getByteValue() {
			return value.byteValue();
		}

		@Override
		public short getShortValue() {
			return value.shortValue();
		}

		@Override
		public int getIntValue() {
			return value.intValue();
		}

		@Override
		public long getLongValue() {
			return value.longValue();
		}

		@Override
		public float getFloatValue() {
			return value.floatValue();
		}

		@Override
		public double getDoubleValue() {
			return value.doubleValue();
		}

		@Override
		public String toString() {
			return value.toString();
		}

		@Override
		public int hashCode() {
			return value.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (super.equals(obj)) {
				@SuppressWarnings("unchecked")
				final NumberData<E> data = (NumberData<E>) obj;
				return data.value.equals(value);
			}
			return false;
		}

		@Override
		public JSONBuilder marshal(JSONBuilder jb) {
			return jb.append(value);
		}
	}

	private static class StringData extends Data {
		private final String value;

		public StringData(String v) {
			value = v;
		}

		@Override
		public VariantType getVariantType() {
			return VariantType.String;
		}

		@Override
		public Declaration makeDeclaration() {
			return DeclarationImpl.String;
		}

		@Override
		public String getStringValue() {
			return value;
		}

		@Override
		public String toString() {
			return value;
		}

		@Override
		public int hashCode() {
			return value.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof StringData) {
				final StringData data = (StringData) obj;
				return data.value.equals(value);
			}
			return false;
		}

		@Override
		public JSONBuilder marshal(JSONBuilder jb) {
			return jb.append(value);
		}
	}

	private static class OctetsData extends Data {
		private final Octets value;

		public OctetsData(Octets v) {
			value = v;
		}

		@Override
		public VariantType getVariantType() {
			return VariantType.Binary;
		}

		@Override
		public Declaration makeDeclaration() {
			return DeclarationImpl.Binary;
		}

		@Override
		public Octets getOctetsValue() {
			return value;
		}

		@Override
		public String toString() {
			return value.toString();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof OctetsData) {
				final OctetsData data = (OctetsData) obj;
				return data.value.equals(value);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return value.hashCode();
		}

		@Override
		public JSONBuilder marshal(JSONBuilder jb) {
			return jb.append(Helper.toHexString(value.getBytes()));
		}
	}

	private static class StructData extends Data {
		private final Map<String, Variant> values = new HashMap<String, Variant>();

		public StructData() {
		}

		@Override
		public VariantType getVariantType() {
			return VariantType.Struct;
		}

		@Override
		public Declaration makeDeclaration() {
			StructDeclarationCreator decl = new StructDeclarationCreator();
			for (Map.Entry<String, Variant> e : values.entrySet())
				decl.addFieldDefinition(e.getKey(), e.getValue().makeDeclaration());
			return decl.create();
		}

		@Override
		public Variant getVariant(String name) {
			final Variant v = values.get(name);
			return v == null ? super.getVariant(name) : v;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append('[');
			for (final Map.Entry<String, Variant> e : values.entrySet())
				sb.append('(').append(e.getKey()).append(',').append(e.getValue().toString()).append(')');
			sb.append(']');
			return sb.toString();
		}

		@Override
		public int hashCode() {
			return values.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof StructData) {
				StructData sd = (StructData) obj;
				return sd.values.equals(values);
			}
			return false;
		}

		@Override
		public void structSetValue(String name, Variant v) {
			values.put(name, v);
		}

		@Override
		public Data copy() {
			StructData data = new StructData();
			for (Map.Entry<String, Variant> e : values.entrySet())
				data.values.put(e.getKey(), e.getValue().copy());
			return data;
		}

		@Override
		public JSONBuilder marshal(JSONBuilder jb) {
			return jb.append(values);
		}
	}

	private static class CollectionData extends Data {
		private final Collection<Variant> values;
		private final Declaration decl;

		public CollectionData(Collection<Variant> v, Declaration decl) {
			this.values = v;
			this.decl = decl;
		}

		@Override
		public VariantType getVariantType() {
			return decl.getType();
		}

		@Override
		public Declaration makeDeclaration() {
			return decl;
		}

		@Override
		public Collection<Variant> getListValue() {
			return values;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append('[');
			for (final Variant v : values)
				sb.append(v.toString()).append(',');
			sb.append(']');
			return sb.toString();
		}

		@Override
		public int hashCode() {
			return values.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof CollectionData) {
				CollectionData ld = (CollectionData) obj;
				return ld.values.equals(values);
			}
			return false;
		}

		@Override
		public void listInsert(Variant v) {
			values.add(v);
		}

		@Override
		public Data copy() {
			final Collection<Variant> c;
			switch (decl.getType()) {
			case List:
				c = new LinkedList<Variant>();
				break;
			case Vector:
				c = new ArrayList<Variant>();
				break;
			case Set:
				c = new HashSet<Variant>();
				break;
			default:
				throw new IllegalArgumentException("bad declaration type " + decl.getType());
			}
			final CollectionData data = new CollectionData(c, decl);
			for (final Variant v : values)
				data.values.add(v.copy());
			return data;
		}

		@Override
		public JSONBuilder marshal(JSONBuilder jb) {
			return jb.append(values);
		}
	}

	private static class MapData extends Data {
		final Map<Variant, Variant> values;
		private final Declaration decl;

		public MapData(Map<Variant, Variant> v, Declaration d) {
			values = v;
			decl = d;
		}

		@Override
		public VariantType getVariantType() {
			return decl.getType();
		}

		@Override
		public Declaration makeDeclaration() {
			return decl;
		}

		@Override
		public Map<Variant, Variant> getMapValue() {
			return values;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append('[');
			for (final Map.Entry<Variant, Variant> e : values.entrySet())
				sb.append('(').append(e.getKey().toString()).append(',').append(e.getValue().toString()).append(')');
			sb.append(']');
			return sb.toString();
		}

		@Override
		public int hashCode() {
			return values.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof MapData) {
				MapData ld = (MapData) obj;
				return ld.values.equals(values);
			}
			return false;
		}

		@Override
		public Variant mapInsert(Variant k, Variant v) {
			Variant o = values.put(k, v);
			return null == o ? Null : o;
		}

		@Override
		public Data copy() {
			MapData data = new MapData(new HashMap<Variant, Variant>(), decl);
			for (Map.Entry<Variant, Variant> e : values.entrySet())
				data.values.put(e.getKey(), e.getValue().copy());
			return data;
		}

		@Override
		public JSONBuilder marshal(JSONBuilder jb) {
			return jb.append(values);
		}
	}

	@Override
	public JSONBuilder marshal(JSONBuilder jb) {
		return data.marshal(jb);
	}
}
