package limax.zdb;

import java.util.concurrent.Future;

@FunctionalInterface
public interface Procedure extends AbstractProcedure {
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

}
