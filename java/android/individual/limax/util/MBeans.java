package limax.util;

public final class MBeans {
	private MBeans() {
	}

	private final static Runnable doNothing = new Runnable() {
		@Override
		public void run() {
		}
	};

	private static Resource root = Resource.createRoot();

	public static Resource register(Resource parent, Object object, String name) {
		return Resource.create(parent, doNothing);
	}

	public static Resource root() {
		return root;
	}

}
