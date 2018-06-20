package limax.pkix;

import java.util.BitSet;

public enum KeyUsage {
	digitalSignature {
		@Override
		public void update(BitSet bits) {
			bits.set(7);
		}
	},
	nonRepudiation {
		@Override
		public void update(BitSet bits) {
			bits.set(6);
		}
	},
	keyEncipherment {
		@Override
		public void update(BitSet bits) {
			bits.set(5);
		}
	},
	dataEncipherment {
		@Override
		public void update(BitSet bits) {
			bits.set(4);
		}
	},
	keyAgreement {
		@Override
		public void update(BitSet bits) {
			bits.set(3);
		}
	},
	keyCertSign {
		@Override
		public void update(BitSet bits) {
			bits.set(2);
		}
	},
	cRLSign {
		@Override
		public void update(BitSet bits) {
			bits.set(1);
		}
	},
	encipherOnly {
		@Override
		public void update(BitSet bits) {
			bits.set(0);
		}
	},
	decipherOnly {
		@Override
		public void update(BitSet bits) {
			bits.set(15);
		}
	};

	abstract void update(BitSet bits);
}
