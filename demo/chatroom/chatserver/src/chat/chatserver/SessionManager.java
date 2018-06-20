package chat.chatserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import chat.chatserver.chatviews.CommonInfo;
import chat.chatviews.RoomChatHallInfo;
import chat.chatviews.ViewChatRoomInfo;
import chat.chatviews.monitor.RoomInfo;
import limax.net.Config;
import limax.net.Manager;
import limax.net.ServerManager;
import limax.net.Transport;
import limax.provider.GlobalId;
import limax.provider.ProcedureHelper;
import limax.provider.ProviderListener;
import limax.util.Pair;
import limax.util.Trace;
import limax.zdb.Procedure;

public class SessionManager implements ProviderListener {

	static private final SessionManager instance = new SessionManager();

	private SessionManager() {
	}

	public static SessionManager getInstance() {
		return instance;
	}

	private ServerManager manager = null;

	public ServerManager getManager() {
		return manager;
	}

	private static void insertDefaultChatrooms() {
		final Procedure.Result r = Procedure.call(ProcedureHelper.nameProcedure("insertDefaultChatrooms", () -> {
			GlobalId.create(Main.groupHallNames, "test");
			GlobalId.create(Main.groupRoomNames, "test");
			final Pair<Long, xbean.ChatHallInfo> hallinfo = table.Chathalls.insert();
			hallinfo.getValue().setName("test");
			table.Hallnamecache.insert(hallinfo.getValue().getName(), hallinfo.getKey());

			final Pair<Long, xbean.ChatRoomInfo> roominfo = table.Chatrooms.insert();
			roominfo.getValue().setHallid(hallinfo.getKey());
			roominfo.getValue().setName("test");

			hallinfo.getValue().getRooms().add(roominfo.getKey());
			return true;
		}));
		if (!r.isSuccess()) {
			Trace.error("insert default chatroom failed!", r.getException());
			System.exit(-1);
		} else {
			if (Trace.isInfoEnabled())
				Trace.info("insert default chatroom done!");
			setViewHallsFromCache();
		}
	}

	static void setViewHallsFromCache() {
		final ArrayList<RoomChatHallInfo> halls = new ArrayList<>();
		table.Hallnamecache.get().getCache().walk((name, hid) -> {
			final Procedure.Result r = Procedure.call(ProcedureHelper.nameProcedure("setViewHallsFromCache", () -> {
				xbean.ChatHallInfo chi = table.Chathalls.select(hid);
				halls.add(new RoomChatHallInfo(chi.getName(), hid, chi.getRooms().stream().map(rid -> {
					RoomInfo.mapKey(rid, hid, rid);
					return new ViewChatRoomInfo(table.Chatrooms.select(rid).getName(), rid);
				}).collect(() -> new ArrayList<ViewChatRoomInfo>(), List::add, List::addAll)));
				return true;
			}));
			if (!r.isSuccess()) {
				Trace.fatal("get all chatroom failed!", r.getException());
				System.exit(-1);
			}
		});
		CommonInfo.getInstance().setHalls(halls);
		lastSetHallsTimeStamp = System.currentTimeMillis();
	}

	public static volatile long lastSetHallsTimeStamp = 0;

	private void openListen() {
		try {
			manager.openListen();
		} catch (IOException e) {
			if (Trace.isErrorEnabled())
				Trace.error("SessionManager.openListen", e);
			getManager().close();
		}
	}

	@Override
	public void onManagerInitialized(Manager manager, Config config) {
		this.manager = (ServerManager) manager;
		table.Chathalls.get().walk((k, v) -> {
			Procedure.call(() -> {
				table.Hallnamecache.insert(v.getName(), k);
				return true;
			});
			return true;
		});
		if (table.Hallnamecache.get().getCacheSize() == 0)
			GlobalId.runOnValidation(() -> {
				insertDefaultChatrooms();
				openListen();
			});
		else {
			setViewHallsFromCache();
			openListen();
		}
	}

	@Override
	public void onManagerUninitialized(Manager manager) {
	}

	@Override
	public void onTransportAdded(Transport transport) throws Exception {
		if (Trace.isInfoEnabled())
			Trace.info("onTransportAdded " + transport);
	}

	@Override
	public void onTransportRemoved(Transport transport) throws Exception {
		if (Trace.isInfoEnabled())
			Trace.info("onTransportRemoved " + transport);
	}

	@Override
	public void onTransportDuplicate(Transport transport) throws Exception {
		if (Trace.isInfoEnabled())
			Trace.info("onTransportDuplicate " + transport);
		manager.close(transport);
	}

}
