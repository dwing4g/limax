package limax.zdb;

public interface AbstractProcedure {
	interface Result {
		boolean isSuccess();

		Throwable getException();
	}

	interface Done<P extends AbstractProcedure> {
		void doDone(P p, Result r);
	}

	default int maxExecutionTime() {
		return -1;
	}

	default int retryDelay() {
		return -1;
	}

	default int retryTimes() {
		return -1;
	}

	default boolean retrySerial() {
		return false;
	}

	default String getName() {
		return getClass().getName();
	}
}
