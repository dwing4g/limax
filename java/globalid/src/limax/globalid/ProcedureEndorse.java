package limax.globalid;

import limax.globalid.providerglobalid.EndorseNames;
import limax.providerglobalid.NameResponse;
import limax.providerglobalid.NamesEndorse;
import limax.zdb.Procedure;

public final class ProcedureEndorse implements Procedure {
	private final EndorseNames rpc;

	public ProcedureEndorse(EndorseNames rpc) {
		this.rpc = rpc;
	}

	@Override
	public boolean process() {
		NamesEndorse arg = rpc.getArgument();
		if (arg.tid != 0) {
			if (arg.type == NamesEndorse.COMMIT) {
				Main.endorse(arg.tid).forEach(e -> {
					if (e.getValue())
						table.Names.insert(e.getKey());
					else
						table.Names.delete(e.getKey());
				});
			}
			Main.unlock(rpc.getTransport(), arg.tid);
		}
		rpc.getResult().status = NameResponse.OK;
		try {
			rpc.response();
		} catch (Exception e) {
			rpc.getManager().close(rpc.getTransport());
			return false;
		}
		return true;
	}
}
