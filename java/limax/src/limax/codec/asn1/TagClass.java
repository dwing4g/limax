package limax.codec.asn1;

public enum TagClass {
	Universal {
		public byte mask(byte identifier) {
			return identifier;
		}
	},
	Application {
		public byte mask(byte identifier) {
			return (byte) (0x40 | identifier);
		}
	},
	ContextSpecific {
		public byte mask(byte identifier) {
			return (byte) (0x80 | identifier);
		}
	},
	Private {
		public byte mask(byte identifier) {
			return (byte) (0xc0 | identifier);
		}
	};

	public abstract byte mask(byte identifier);

	public static TagClass unmask(byte identifier) {
		switch (identifier & 0xc0) {
		case 0x40:
			return Application;
		case 0x80:
			return ContextSpecific;
		case 0xc0:
			return Private;
		}
		return Universal;
	}
}
