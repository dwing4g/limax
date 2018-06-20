package limax.provider;

import java.util.concurrent.Future;

import limax.codec.CodecException;
import limax.net.Protocol;
import limax.net.SizePolicyException;
import limax.net.Transport;
import limax.util.Trace;
import limax.zdb.Procedure;
import limax.zdb.Transaction;

public final class ProcedureHelper {

	private ProcedureHelper() {
	}

	private static class SendDirectly implements Runnable {
		private final Transport transport;
		private final Protocol p;

		public SendDirectly(Transport transport, Protocol p) {
			this.transport = transport;
			this.p = p;
		}

		@Override
		public void run() {
			try {
				p.send(transport);
			} catch (InstantiationException | SizePolicyException | CodecException e) {
				if (Trace.isErrorEnabled())
					Trace.error(this, e);
			}
		}
	}

	public static void sendWhileFinish(Transport transport, Protocol p) {
		Runnable r = new SendDirectly(transport, p);
		Transaction.addSavepointTask(r, r);
	}

	public static void executeWhileFinish(Runnable r) {
		Transaction.addSavepointTask(r, r);
	}

	public static void executeWhileFinish(Procedure p) {
		executeWhileFinish(() -> Procedure.execute(p));
	}

	public static void sendWhileCommit(Transport transport, Protocol p) {
		Transaction.addSavepointTask(new SendDirectly(transport, p), null);
	}

	public static void executeWhileCommit(Runnable r) {
		Transaction.addSavepointTask(r, null);
	}

	public static void executeWhileCommit(Procedure p) {
		executeWhileCommit(() -> Procedure.execute(p));
	}

	public static void sendWhileRollback(Transport transport, Protocol p) {
		Transaction.addSavepointTask(null, new SendDirectly(transport, p));
	}

	public static void executeWhileRollback(Runnable r) {
		Transaction.addSavepointTask(null, r);
	}

	public static void executeWhileRollback(Procedure p) {
		executeWhileRollback(() -> Procedure.execute(p));
	}

	public static Procedure nameProcedure(String name, Procedure p) {
		return new Procedure() {

			@Override
			public boolean process() throws Exception {
				return p.process();
			}

			@Override
			public String getName() {
				return name;
			}
		};
	}

	public static void execute(String name, Procedure p) {
		Procedure.execute(nameProcedure(name, p));
	}

	public static <P extends Procedure> void execute(String name, P p, Procedure.Done<P> d) {
		Procedure.execute(nameProcedure(name, p), (_p_, _r_) -> d.doDone(p, _r_));
	}

	public static Procedure.Result call(String name, Procedure p) {
		return Procedure.call(nameProcedure(name, p));
	}

	public static Future<Procedure.Result> submit(String name, Procedure p) {
		return Procedure.submit(nameProcedure(name, p));
	}

	public static void executeWhileFinish(String name, Procedure p) {
		executeWhileFinish(nameProcedure(name, p));
	}

	public static void executeWhileCommit(String name, Procedure p) {
		executeWhileCommit(nameProcedure(name, p));
	}

	public static void executeWhileRollback(String name, Procedure p) {
		executeWhileRollback(nameProcedure(name, p));
	}

}
