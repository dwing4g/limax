package limax.endpoint.variant;

public enum VariantType {

	Null {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (0 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.Null;
		}
	},
	Boolean {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (0 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.Boolean;
		}
	},
	Byte {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (0 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.Byte;
		}
	},
	Short {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (0 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.Short;
		}
	},
	Int {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (0 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.Int;
		}
	},
	Long {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (0 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.Long;
		}
	},
	Float {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (0 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.Float;
		}
	},
	Double {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (0 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.Double;
		}
	},
	String {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (0 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.String;
		}
	},
	Binary {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (0 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.Binary;
		}
	},
	List {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (1 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.createList(args[0]);
		}
	},
	Vector {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (1 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.createVector(args[0]);
		}
	},
	Set {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (1 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.createSet(args[0]);
		}
	},
	Map {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			if (2 != args.length)
				throw new IllegalArgumentException();
			return DeclarationImpl.createMap(args[0], args[1]);
		}
	},
	Struct {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			throw new UnsupportedOperationException();
		}
	},
	Object {
		@Override
		public Declaration createDeclaration(Declaration... args) {
			throw new UnsupportedOperationException();
		}
	};

	public abstract Declaration createDeclaration(Declaration... args);
}
