package limax.zdb;

import limax.util.Promise;

@FunctionalInterface
public interface Function<R> extends AbstractProcedure {
	R process() throws Exception;

	static <R, F extends Function<R>> Promise<R> promise(F f) {
		return f.promise();
	}

	default Promise<R> promise() {
		return ProcedureImpl.promise(this);
	}
}
