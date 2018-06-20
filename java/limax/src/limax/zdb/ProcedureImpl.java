package limax.zdb;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import limax.util.Trace;

final class ProcedureImpl<P extends Procedure> implements Procedure.Result {

	boolean call() {
		int savepoint = Transaction.savepoint();
		try {
			if (process.process()) {
				setSuccess(true);
				setException(null);
				return true;
			}
		} catch (Exception e) {
			setException(e);
			Trace.log(Zdb.pmeta().getTrace(), "Procedure execute", e);
		}
		Transaction.rollback(savepoint);
		setSuccess(false);
		return false;
	}

	private static boolean perform(ProcedureImpl<?> p) {
		try {
			Transaction.current().perform(p);
		} catch (XDeadlock e) {
			return false;
		} catch (Throwable e) {
		}
		return true;
	}

	static <P extends Procedure> Procedure.Result call(P p) {
		ProcedureImpl<P> impl = new ProcedureImpl<>(p);
		if (Transaction.current() == null) {
			try {
				Transaction.create();
				for (int retry = 0;;) {
					if (perform(impl))
						break;
					retry++;
					if (retry > impl.retryTimes())
						break;
					if (retry == impl.retryTimes() && impl.retrySerial())
						impl.isolation = Transaction.Isolation.LEVEL3;
					try {
						Thread.sleep(impl.delay());
					} catch (InterruptedException e) {
					}
				}
			} finally {
				Transaction.destroy();
			}
		} else {
			impl.call();
		}
		return impl;
	}

	static <P extends Procedure> Future<Procedure.Result> submit(P p) {
		if (Transaction.current() != null)
			throw new IllegalStateException("can not submit in transaction.");
		return new ProcedureFuture<P>(new ProcedureImpl<P>(p));
	}

	static <P extends Procedure> void execute(P p, Procedure.Done<P> done) {
		new ProcedureFuture<P>(new ProcedureImpl<P>(p), done);
	}

	private final P process;
	private volatile Transaction.Isolation isolation;
	private volatile boolean success = false;
	private volatile Throwable exception;

	private ProcedureImpl(P p) {
		this.process = p;
		this.isolation = Transaction.getIsolationLevel();
	}

	Transaction.Isolation getIsolationLevel() {
		return isolation;
	}

	@Override
	public boolean isSuccess() {
		return success;
	}

	void setSuccess(boolean success) {
		this.success = success;
	}

	@Override
	public Throwable getException() {
		return exception;
	}

	void setException(Throwable exception) {
		this.exception = exception;
	}

	String getProcedureName() {
		return process.getName();
	}

	private int maxExecutionTime() {
		return process.maxExecutionTime() == -1 ? Zdb.pmeta().getMaxExecutionTime() : process.maxExecutionTime();
	}

	private int retryDelay() {
		return process.retryDelay() == -1 ? Zdb.pmeta().getRetryDelay() : process.retryDelay();
	}

	private int retryTimes() {
		return process.retryTimes() == -1 ? Zdb.pmeta().getRetryTimes() : process.retryTimes();
	}

	private boolean retrySerial() {
		return process.retrySerial() ? true : Zdb.pmeta().getRetrySerial();
	}

	private int delay() {
		int t0 = retryDelay();
		int t1 = maxExecutionTime();
		return t0 <= 0 ? 0 : Zdb.random().nextInt(t1 <= 0 ? t0 : Math.min(t0, t1));
	}

	private static class ProcedureFuture<P extends Procedure> implements RunnableFuture<Procedure.Result> {
		private volatile Future<ProcedureImpl<P>> future;
		private final ProcedureImpl<P> p;
		private final Procedure.Done<P> done;
		private volatile int retry = 0;

		public ProcedureFuture(ProcedureImpl<P> p) {
			this(p, null);
		}

		private void launch() {
			this.future = Zdb.procedureExecutor(p.maxExecutionTime()).submit(this, p);
		}

		public ProcedureFuture(ProcedureImpl<P> p, Procedure.Done<P> done) {
			this.p = p;
			this.done = done;
			launch();
		}

		private void done() {
			if (done != null)
				try {
					done.doDone(p.process, p);
				} catch (Throwable e) {
					if (Trace.isErrorEnabled())
						Trace.error("doDone", e);
				}
		}

		private static class XDeadLockError extends XError {
			static final long serialVersionUID = 6156439040197208243L;
			private final Future<?> future;

			XDeadLockError(Future<?> future) {
				this.future = future;
			}

			void pending() {
				try {
					future.get();
				} catch (InterruptedException | ExecutionException e) {
				}
			}
		}

		@Override
		public void run() {
			try {
				try {
					Transaction.create().perform(p);
				} finally {
					Transaction.destroy();
				}
				done();
			} catch (XDeadlock e) {
				retry++;
				if (retry > p.retryTimes()) {
					done();
					throw e;
				}
				if (retry == p.retryTimes() && p.retrySerial())
					p.isolation = Transaction.Isolation.LEVEL3;
				throw new XDeadLockError(Zdb.scheduler().schedule(() -> launch(), p.delay(), TimeUnit.MILLISECONDS));
			} catch (XError e) {
				done();
				throw e;
			} catch (Throwable e) {
				done();
				throw new XError(e);
			}
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return future.cancel(mayInterruptIfRunning);
		}

		@Override
		public Procedure.Result get() throws InterruptedException, ExecutionException {
			for (;;) {
				try {
					future.get();
					return p;
				} catch (ExecutionException e) {
					if (!(e.getCause() instanceof XDeadLockError))
						throw new ExecutionException(p.getException());
					((XDeadLockError) e.getCause()).pending();
				}
			}
		}

		@Override
		public Procedure.Result get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException {
			for (;;) {
				try {
					future.get(timeout, unit);
					return p;
				} catch (ExecutionException e) {
					if (!(e.getCause() instanceof XDeadLockError))
						throw new ExecutionException(p.getException());
					((XDeadLockError) e.getCause()).pending();
				}
			}
		}

		@Override
		public boolean isCancelled() {
			return future.isCancelled();
		}

		@Override
		public boolean isDone() {
			return future.isDone();
		}
	}
}
