package limax.zdb.tool;

public enum IntType {
	BOOLEAN {
		@Override
		public int size() {
			return 1;
		}
	},
	BYTE {
		@Override
		public int size() {
			return 1;
		}
	},
	SHORT {
		@Override
		public int size() {
			return 2;
		}
	},
	INT {
		@Override
		public int size() {
			return 4;
		}
	},
	LONG {
		@Override
		public int size() {
			return 8;
		}
	};

	public abstract int size();
}