package limax.endpoint;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import limax.endpoint.providerendpoint.SyncViewToClients;
import limax.providerendpoint.ViewMemberData;
import limax.providerendpoint.ViewVariableData;

final class ViewContextImpl {

	interface createViewInstance {

		int getProviderId();

		View createView(short classindex);
	}

	private static long getTemporaryViewKey(short classindex, int instanceindex) {
		return ((classindex & 0xFFFFL) << 32) | (instanceindex & 0xFFFFFFFFL);
	}

	final EndpointManagerImpl netmanager;
	private final createViewInstance createview;
	private final Map<Short, View> viewmap = new HashMap<Short, View>();
	private final Map<Long, TemporaryView> tempviewmap = new HashMap<Long, TemporaryView>();

	ViewContextImpl(createViewInstance createview, EndpointManagerImpl netmanager) {
		this.netmanager = netmanager;
		this.createview = createview;
	}

	int getProviderId() {
		return createview.getProviderId();
	}

	void clear() {
		synchronized (this) {
			for (TemporaryView v : tempviewmap.values())
				v.doClose();
			for (View v : viewmap.values())
				v.doClose();
			viewmap.clear();
			tempviewmap.clear();
		}
	}

	synchronized TemporaryView findTemporaryView(short classindex, int instanceindex) {
		return tempviewmap.get(getTemporaryViewKey(classindex, instanceindex));
	}

	void onSyncViewToClients(SyncViewToClients p) throws Exception {
		switch (p.synctype) {
		case SyncViewToClients.DT_VIEW_DATA: {
			final View view = getViewInstance(p.classindex);
			synchronized (view) {
				for (ViewVariableData var : p.vardatas)
					view.onData(netmanager.getSessionId(), var.index, var.field, var.data, var.dataremoved);
			}
			break;
		}
		case SyncViewToClients.DT_TEMPORARY_INIT_DATA: {
			final TemporaryView view = getTemporaryView(p.classindex, p.instanceindex);
			synchronized (view) {
				Collection<Long> sessionids = new HashSet<Long>();
				for (ViewMemberData item : p.members)
					sessionids.add(item.sessionid);
				view.onOpen(sessionids);
				for (ViewVariableData var : p.vardatas)
					view.onData(netmanager.getSessionId(), var.index, var.field, var.data, var.dataremoved);
				for (ViewMemberData var : p.members)
					if (var.vardata.index >= 0)
						view.onData(var.sessionid, var.vardata.index, var.vardata.field, var.vardata.data,
								var.vardata.dataremoved);
			}
			break;
		}
		case SyncViewToClients.DT_TEMPORARY_DATA: {
			final TemporaryView view = findTemporaryView(p.classindex, p.instanceindex);
			if (view != null)
				synchronized (view) {
					for (ViewVariableData var : p.vardatas)
						view.onData(netmanager.getSessionId(), var.index, var.field, var.data, var.dataremoved);
					for (ViewMemberData var : p.members)
						view.onData(var.sessionid, var.vardata.index, var.vardata.field, var.vardata.data,
								var.vardata.dataremoved);
				}
			break;
		}
		case SyncViewToClients.DT_TEMPORARY_ATTACH: {
			final TemporaryView view = findTemporaryView(p.classindex, p.instanceindex);
			if (view != null && p.members.size() >= 1) {
				synchronized (view) {
					view.onAttach(p.members.get(0).sessionid);
					for (ViewMemberData var : p.members)
						if (var.vardata.index >= 0)
							view.onData(var.sessionid, var.vardata.index, var.vardata.field, var.vardata.data,
									var.vardata.dataremoved);
				}
			}
			break;
		}
		case SyncViewToClients.DT_TEMPORARY_DETACH: {
			final TemporaryView view = findTemporaryView(p.classindex, p.instanceindex);
			if (view != null && p.members.size() == 1) {
				ViewMemberData e = p.members.get(0);
				synchronized (view) {
					view.detach(e.sessionid, e.vardata.index);
				}
			}
			break;
		}
		case SyncViewToClients.DT_TEMPORARY_CLOSE: {
			final TemporaryView view = closeTemporaryView(p.classindex, p.instanceindex);
			if (view != null)
				synchronized (view) {
					view.doClose();
				}
			break;
		}
		}
	}

	private synchronized View getViewInstance(short classindex) {
		View view = viewmap.get(classindex);
		if (null != view)
			return view;
		view = createview.createView(classindex);
		if (null == view)
			throw new RuntimeException(
					"unknown view class pvid = " + createview.getProviderId() + " classindex = " + classindex);
		return view;
	}

	private synchronized TemporaryView getTemporaryView(short classindex, int instanceindex) {
		final long key = getTemporaryViewKey(classindex, instanceindex);
		TemporaryView view = tempviewmap.get(key);
		if (null == view) {
			view = (TemporaryView) createview.createView(classindex);
			if (null == view)
				throw new RuntimeException("unknown temporary view class pvid = " + createview.getProviderId()
						+ " classindex = " + classindex + " instanceindex = " + instanceindex);
			view.instanceindex = instanceindex;
			tempviewmap.put(key, view);
		}
		return view;
	}

	private synchronized TemporaryView closeTemporaryView(short classindex, int instanceindex) {
		return tempviewmap.remove(getTemporaryViewKey(classindex, instanceindex));
	}

	synchronized View getSesseionOrGlobalView(short classindex) {
		View view = viewmap.get(classindex);
		if (null != view)
			return view;
		view = createview.createView(classindex);
		if (null != view)
			viewmap.put(view.getClassIndex(), view);
		return view;
	}

	EndpointManager getEndpointManager() {
		return netmanager;
	}

}
