package limax.endpoint.variant;

import java.util.Collection;
import java.util.Map;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;

final class MarshalMethods {

	final static MarshalMethod booleanMethod = new MarshalMethod() {
		@Override
		public OctetsStream marshal(OctetsStream os, Variant v) {
			os.marshal(v.getBooleanValue());
			return os;
		}

		@Override
		public Variant unmarshal(OctetsStream os) throws MarshalException {
			return Variant.create(os.unmarshal_boolean());
		}

		@Override
		public Declaration getDeclaration() {
			return DeclarationImpl.Boolean;
		}
	};

	final static MarshalMethod byteMethod = new MarshalMethod() {
		@Override
		public OctetsStream marshal(OctetsStream os, Variant v) {
			os.marshal(v.getByteValue());
			return os;
		}

		@Override
		public Variant unmarshal(OctetsStream os) throws MarshalException {
			return Variant.create(os.unmarshal_byte());
		}

		@Override
		public Declaration getDeclaration() {
			return DeclarationImpl.Byte;
		}
	};

	final static MarshalMethod shortMethod = new MarshalMethod() {
		@Override
		public OctetsStream marshal(OctetsStream os, Variant v) {
			os.marshal(v.getShortValue());
			return os;
		}

		@Override
		public Variant unmarshal(OctetsStream os) throws MarshalException {
			return Variant.create(os.unmarshal_short());
		}

		@Override
		public Declaration getDeclaration() {
			return DeclarationImpl.Short;
		}
	};

	final static MarshalMethod intMethod = new MarshalMethod() {
		@Override
		public OctetsStream marshal(OctetsStream os, Variant v) {
			os.marshal(v.getIntValue());
			return os;
		}

		@Override
		public Variant unmarshal(OctetsStream os) throws MarshalException {
			return Variant.create(os.unmarshal_int());
		}

		@Override
		public Declaration getDeclaration() {
			return DeclarationImpl.Int;
		}
	};

	final static MarshalMethod longMethod = new MarshalMethod() {
		@Override
		public OctetsStream marshal(OctetsStream os, Variant v) {
			os.marshal(v.getLongValue());
			return os;
		}

		@Override
		public Variant unmarshal(OctetsStream os) throws MarshalException {
			return Variant.create(os.unmarshal_long());
		}

		@Override
		public Declaration getDeclaration() {
			return DeclarationImpl.Long;
		}
	};

	final static MarshalMethod floatMethod = new MarshalMethod() {
		@Override
		public OctetsStream marshal(OctetsStream os, Variant v) {
			os.marshal(v.getFloatValue());
			return os;
		}

		@Override
		public Variant unmarshal(OctetsStream os) throws MarshalException {
			return Variant.create(os.unmarshal_float());
		}

		@Override
		public Declaration getDeclaration() {
			return DeclarationImpl.Float;
		}
	};

	final static MarshalMethod doubleMethod = new MarshalMethod() {
		@Override
		public OctetsStream marshal(OctetsStream os, Variant v) {
			os.marshal(v.getDoubleValue());
			return os;
		}

		@Override
		public Variant unmarshal(OctetsStream os) throws MarshalException {
			return Variant.create(os.unmarshal_double());
		}

		@Override
		public Declaration getDeclaration() {
			return DeclarationImpl.Double;
		}
	};

	final static MarshalMethod stringMethod = new MarshalMethod() {
		@Override
		public OctetsStream marshal(OctetsStream os, Variant v) {
			os.marshal(v.getStringValue());
			return os;
		}

		@Override
		public Variant unmarshal(OctetsStream os) throws MarshalException {
			return Variant.create(os.unmarshal_String());
		}

		@Override
		public Declaration getDeclaration() {
			return DeclarationImpl.String;
		}
	};

	final static MarshalMethod octetsMethod = new MarshalMethod() {
		@Override
		public OctetsStream marshal(OctetsStream os, Variant v) {
			os.marshal(v.getOctetsValue());
			return os;
		}

		@Override
		public Variant unmarshal(OctetsStream os) throws MarshalException {
			return Variant.create(os.unmarshal_Octets());
		}

		@Override
		public Declaration getDeclaration() {
			return DeclarationImpl.Binary;
		}
	};

	private abstract static class CollectionMarshalMethod implements MarshalMethod {
		private final MarshalMethod valuemm;

		public CollectionMarshalMethod(Declaration val) {
			this.valuemm = val.createMarshalMethod();
		}

		@Override
		public OctetsStream marshal(OctetsStream os, Variant v) {
			final Collection<Variant> vs = v.getCollectionValue();
			os.marshal_size(vs.size());
			for (final Variant sv : vs)
				valuemm.marshal(os, sv);
			return os;
		}

		@Override
		public Variant unmarshal(OctetsStream os) throws MarshalException {
			final Variant v = createVariant();
			final int count = os.unmarshal_size();
			for (int i = 0; i < count; i++)
				v.collectionInsert(valuemm.unmarshal(os));
			return v;
		}

		protected abstract Variant createVariant();
	}

	private abstract static class MapMarshalMethod implements MarshalMethod {
		private final MarshalMethod keymm;
		private final MarshalMethod valuemm;

		public MapMarshalMethod(Declaration key, Declaration val) {
			this.keymm = key.createMarshalMethod();
			this.valuemm = val.createMarshalMethod();
		}

		@Override
		public OctetsStream marshal(OctetsStream os, Variant v) {
			final Map<Variant, Variant> vs = v.getMapValue();
			os.marshal_size(vs.size());
			for (final Map.Entry<Variant, Variant> e : vs.entrySet()) {
				keymm.marshal(os, e.getKey());
				valuemm.marshal(os, e.getValue());
			}
			return os;
		}

		@Override
		public Variant unmarshal(OctetsStream os) throws MarshalException {
			final Variant v = createVariant();
			final int count = os.unmarshal_size();
			for (int i = 0; i < count; i++)
				v.mapInsert(keymm.unmarshal(os), valuemm.unmarshal(os));
			return v;
		}

		protected abstract Variant createVariant();
	}

	static MarshalMethod createListMethod(final Declaration list, Declaration item) {
		return new CollectionMarshalMethod(item) {

			@Override
			public Declaration getDeclaration() {
				return list;
			}

			@Override
			protected Variant createVariant() {
				return Variant.createList();
			}
		};
	}

	static MarshalMethod createSetMethod(final Declaration set, Declaration item) {
		return new CollectionMarshalMethod(item) {
			@Override
			public Declaration getDeclaration() {
				return set;
			}

			@Override
			protected Variant createVariant() {
				return Variant.createSet();
			}
		};
	}

	static MarshalMethod createVectorMethod(final Declaration vector, Declaration item) {
		return new CollectionMarshalMethod(item) {

			@Override
			public Declaration getDeclaration() {
				return vector;
			}

			@Override
			protected Variant createVariant() {
				return Variant.createVector();
			}
		};
	}

	static MarshalMethod createMapMethod(final Declaration map, Declaration key, Declaration val) {
		return new MapMarshalMethod(key, val) {

			@Override
			public Declaration getDeclaration() {
				return map;
			}

			@Override
			protected Variant createVariant() {
				return Variant.createMap();
			}
		};
	}

}
