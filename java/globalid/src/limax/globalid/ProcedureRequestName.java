package limax.globalid;

import limax.globalid.providerglobalid.RequestName;
import limax.providerglobalid.GroupName;
import limax.providerglobalid.NameRequest;
import limax.providerglobalid.NameResponse;
import limax.util.Trace;
import limax.zdb.Procedure;
import limax.zdb.XDeadlock;

public final class ProcedureRequestName implements Procedure {
	private final static Object lock = new Object();
	private final RequestName rpc;

	public ProcedureRequestName(RequestName rpc) {
		this.rpc = rpc;
	}

	private void create(GroupName gn, NameResponse response) {
		Short gid = table.Namegroups.update(gn.grp);
		if (gid == null) {
			synchronized (lock) {
				if (Main.maxgroupid == Short.MAX_VALUE) {
					Trace.fatal("maxgroupid overflow.");
					response.status = NameResponse.REJECT;
					return;
				}
				gid = ++Main.maxgroupid;
			}
			table.Maxgroupid.delete(0);
			table.Maxgroupid.insert(0, gid);
			table.Namegroups.delete(gn.grp);
			table.Namegroups.insert(gn.grp, gid);
			Main.groupids.put(gn.grp, gid);
		}
		if (test(gn, response.serial, gid)) {
			response.status = NameResponse.DUPLICATE;
		} else {
			Main.setCreate(response.serial);
			response.status = NameResponse.OK;
		}
	}

	private void delete(GroupName gn, NameResponse response) {
		if (test(gn, response.serial, Main.groupids.get(gn.grp))) {
			Main.setDelete(response.serial);
			response.status = NameResponse.OK;
		} else {
			response.status = NameResponse.NOTEXISTS;
		}
	}

	private void test(GroupName gn, NameResponse response) {
		response.status = test(gn, response.serial, Main.groupids.get(gn.grp)) ? NameResponse.OK
				: NameResponse.NOTEXISTS;
	}

	private boolean test(GroupName gn, long serial, Short gid) {
		return !Main.isDelete(serial) && (Main.isCreate(serial)
				|| gid != null && table.Names.select(new cbean.NameKey(gid, gn.name)) != null);
	}

	@Override
	public boolean process() {
		NameRequest request = rpc.getArgument();
		NameResponse response = rpc.getResult();
		int type;
		if (request.type > 0) {
			try {
				response.serial = Main.lock(request.gn, request.serial, rpc.getTransport());
				type = request.type;
			} catch (Main.IgnoreOperationException e) {
				return false;
			} catch (XDeadlock e) {
				response.status = NameResponse.DEADLOCK;
				type = -1;
			}
		} else {
			type = -request.type;
			request.gn = Main.getGroupName(response.serial = request.serial);
		}
		switch (type) {
		case NameRequest.CREATE:
			create(request.gn, response);
			break;
		case NameRequest.DELETE:
			delete(request.gn, response);
			break;
		case NameRequest.TEST:
			test(request.gn, response);
		}
		try {
			rpc.response();
		} catch (Exception e) {
			rpc.getManager().close(rpc.getTransport());
			return false;
		}
		return true;
	}
}
