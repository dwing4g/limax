package limax.globalid;

import limax.globalid.providerglobalid.RequestId;
import limax.providerglobalid.Group;
import limax.zdb.Procedure;

public final class ProcedureRequestId implements Procedure {
	private final RequestId rpc;

	public ProcedureRequestId(RequestId rpc) {
		this.rpc = rpc;
	}

	@Override
	public boolean process() {
		Group g = rpc.getArgument();
		Long gid = table.Idgroups.update(g.grp);
		if (gid == null) {
			gid = Long.valueOf(0);
		} else {
			gid++;
			table.Idgroups.delete(g.grp);
		}
		table.Idgroups.insert(g.grp, gid);
		rpc.getResult().val = gid;
		try {
			rpc.response();
		} catch (Exception e) {
			rpc.getManager().close(rpc.getTransport());
			return false;
		}
		return true;
	}
}
