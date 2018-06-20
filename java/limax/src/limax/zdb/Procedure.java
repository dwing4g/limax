package limax.zdb;

import java.util.concurrent.Future;

@FunctionalInterface
public interface Procedure {

	interface Result {
		boolean isSuccess();

		Throwable getException();
	}

	interface Done<P extends Procedure> {
		void doDone(P p, Result r);
	}

	boolean process() throws Exception;

	static <P extends Procedure> void execute(P p) {
		execute(p, null);
	}

	static <P extends Procedure> void execute(P p, Done<P> d) {
		ProcedureImpl.execute(p, d);
	}

	static <P extends Procedure> Result call(P p) {
		return ProcedureImpl.call(p);
	}

	static <P extends Procedure> Future<Result> submit(P p) {
		return ProcedureImpl.submit(p);
	}

	default void execute() {
		execute(this);
	}

	default void execute(Done<Procedure> d) {
		execute(this, d);
	}

	default Result call() {
		return call(this);
	}

	default Future<Result> submit() {
		return submit(this);
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
