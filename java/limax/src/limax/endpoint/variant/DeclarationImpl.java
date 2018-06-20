package limax.endpoint.variant;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;

final class DeclarationImpl {

	private static final MarshalMethod nullMarshalMethod = new MarshalMethod() {

		@Override
		public OctetsStream marshal(OctetsStream os, Variant v) {
			return os;
		}

		@Override
		public Variant unmarshal(OctetsStream os) throws MarshalException {
			return Variant.Null;
		}

		@Override
		public Declaration getDeclaration() {
			return Null;
		}

	};

	final static Declaration Null = new Declaration() {
		@Override
		public VariantType getType() {
			return VariantType.Null;
		}

		@Override
		public MarshalMethod createMarshalMethod() {
			return nullMarshalMethod;
		}

	};

	final static Declaration Object = new Declaration() {
		@Override
		public VariantType getType() {
			return VariantType.Object;
		}

		@Override
		public MarshalMethod createMarshalMethod() {
			return nullMarshalMethod;
		}

	};

	final static Declaration Boolean = new Declaration() {

		@Override
		public VariantType getType() {
			return VariantType.Boolean;
		}

		@Override
		public MarshalMethod createMarshalMethod() {
			return MarshalMethods.booleanMethod;
		}

	};

	final static Declaration Byte = new Declaration() {

		@Override
		public VariantType getType() {
			return VariantType.Byte;
		}

		@Override
		public MarshalMethod createMarshalMethod() {
			return MarshalMethods.byteMethod;
		}

	};

	final static Declaration Short = new Declaration() {

		@Override
		public VariantType getType() {
			return VariantType.Short;
		}

		@Override
		public MarshalMethod createMarshalMethod() {
			return MarshalMethods.shortMethod;
		}

	};

	final static Declaration Int = new Declaration() {

		@Override
		public VariantType getType() {
			return VariantType.Int;
		}

		@Override
		public MarshalMethod createMarshalMethod() {
			return MarshalMethods.intMethod;
		}

	};

	final static Declaration Long = new Declaration() {

		@Override
		public VariantType getType() {
			return VariantType.Long;
		}

		@Override
		public MarshalMethod createMarshalMethod() {
			return MarshalMethods.longMethod;
		}

	};

	final static Declaration Float = new Declaration() {

		@Override
		public VariantType getType() {
			return VariantType.Float;
		}

		@Override
		public MarshalMethod createMarshalMethod() {
			return MarshalMethods.floatMethod;
		}

	};

	final static Declaration Double = new Declaration() {

		@Override
		public VariantType getType() {
			return VariantType.Double;
		}

		@Override
		public MarshalMethod createMarshalMethod() {
			return MarshalMethods.doubleMethod;
		}

	};

	final static Declaration String = new Declaration() {

		@Override
		public VariantType getType() {
			return VariantType.String;
		}

		@Override
		public MarshalMethod createMarshalMethod() {
			return MarshalMethods.stringMethod;
		}

	};

	final static Declaration Binary = new Declaration() {

		@Override
		public VariantType getType() {
			return VariantType.Binary;
		}

		@Override
		public MarshalMethod createMarshalMethod() {
			return MarshalMethods.octetsMethod;
		}

	};

	final static Declaration createList(final Declaration value) {
		return new CollectionDeclaration() {

			@Override
			public VariantType getType() {
				return VariantType.List;
			}

			@Override
			public MarshalMethod createMarshalMethod() {
				return MarshalMethods.createListMethod(this, value);
			}

			@Override
			public Declaration getValue() {
				return value;
			}
		};
	}

	final static Declaration createVector(final Declaration value) {
		return new CollectionDeclaration() {

			@Override
			public VariantType getType() {
				return VariantType.Vector;
			}

			@Override
			public MarshalMethod createMarshalMethod() {
				return MarshalMethods.createVectorMethod(this, value);
			}

			@Override
			public Declaration getValue() {
				return value;
			}

		};
	}

	final static Declaration createSet(final Declaration value) {
		return new CollectionDeclaration() {
			@Override
			public VariantType getType() {
				return VariantType.Set;
			}

			@Override
			public MarshalMethod createMarshalMethod() {
				return MarshalMethods.createSetMethod(this, value);
			}

			@Override
			public Declaration getValue() {
				return value;
			}

		};
	}

	final static Declaration createMap(final Declaration key, final Declaration value) {
		return new MapDeclaration() {

			@Override
			public VariantType getType() {
				return VariantType.Map;
			}

			@Override
			public MarshalMethod createMarshalMethod() {
				return MarshalMethods.createMapMethod(this, key, value);
			}

			@Override
			public Declaration getValue() {
				return value;
			}

			@Override
			public Declaration getKey() {
				return key;
			}

		};
	}

}
