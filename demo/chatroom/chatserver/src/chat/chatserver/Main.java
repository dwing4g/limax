package chat.chatserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.chatserver.chatviews.ChatRoom;
import chat.chatserver.chatviews.UserInfo;
import chat.chatviews.ChatMessage;
import limax.net.Engine;
import limax.provider.GlobalId;
import limax.provider.ProcedureHelper;
import limax.util.Pair;
import limax.xmlconfig.Service;
import limax.zdb.Procedure;

public class Main {

	private interface Command {
		void runCommand(long sessionid, String[] params);
	}

	private static String commandPrefixString = ".cm ";
	private static String commandPassword = "123456";

	public static String groupRoomNames = "roomnames";
	public static String groupHallNames = "hallnames";

	private static Map<String, Command> cmdmap = new HashMap<>();

	private static void updateViewHallsFromCache(long sessionid, String message, List<ChatRoom> destroylist) {
		UserInfo.getInstance(sessionid).setRecvedmessage(new ChatMessage(message, sessionid));
		ProcedureHelper.executeWhileCommit(() -> Engine.getApplicationExecutor().execute(() -> {
			SessionManager.setViewHallsFromCache();
			destroylist.forEach(room -> room.destroyInstance());
		}));
	}

	static {
		cmdmap.put("on", (sessionid, params) -> {
			if (params.length < 2)
				return;
			if (params[1].equals(commandPassword)) {
				UserInfo.getInstance(sessionid).setCommandMode(true);
				UserInfo.getInstance(sessionid).setRecvedmessage(new ChatMessage("command on succeed", sessionid));
			} else
				UserInfo.getInstance(sessionid).setRecvedmessage(new ChatMessage("command on bad password", sessionid));
		});

		cmdmap.put("off", (sessionid, params) -> {
			UserInfo.getInstance(sessionid).setCommandMode(false);
			UserInfo.getInstance(sessionid).setRecvedmessage(new ChatMessage("command off succeed", sessionid));
		});

		cmdmap.put("shutdown", (sessionid, params) -> {
			if (!UserInfo.getInstance(sessionid).isCommandMode())
				return;
			UserInfo.getInstance(sessionid).setRecvedmessage(new ChatMessage("command shutdown succeed", sessionid));
			long ms = 1;
			if (params.length > 1)
				ms = Long.parseLong(params[1]);
			if (ms < 1)
				ms = 1;
			Service.stop(ms);
		});

		cmdmap.put("createhall", (sessionid, params) -> {
			if (params.length < 2 || !UserInfo.getInstance(sessionid).isCommandMode())
				return;
			Procedure.execute(() -> {
				if (!GlobalId.create(groupHallNames, params[1])) {
					UserInfo.getInstance(sessionid)._setRecvedmessage(new ChatMessage("bad hall name", sessionid));
					return false;
				}
				Pair<Long, xbean.ChatHallInfo> pvs = table.Chathalls.insert();
				pvs.getValue().setName(params[1]);
				table.Hallnamecache.insert(params[1], pvs.getKey());
				updateViewHallsFromCache(sessionid, "create hall succeed", Collections.emptyList());
				return true;
			});
		});

		cmdmap.put("deletehall", (sessionid, params) -> {
			if (params.length < 2 || !UserInfo.getInstance(sessionid).isCommandMode())
				return;
			Procedure.execute(() -> {
				Long hallid = table.Hallnamecache.update(params[1]);
				if (null == hallid) {
					UserInfo.getInstance(sessionid)._setRecvedmessage(new ChatMessage("bad hall name", sessionid));
					return false;
				}
				xbean.ChatHallInfo hallinfo = table.Chathalls.update(hallid);
				List<ChatRoom> roomlist = new ArrayList<>();
				for (Long roomid : hallinfo.getRooms()) {
					xbean.ChatRoomInfo roominfo = table.Chatrooms.update(roomid);
					GlobalId.delete(groupRoomNames, roominfo.getName());
					table.Chatrooms.delete(roomid);
					ChatRoom view = table.Roominfocache.update(roomid);
					if (null != view) {
						roomlist.add(view);
						table.Roominfocache.delete(roomid);
					}
				}
				GlobalId.delete(groupHallNames, params[1]);
				table.Hallnamecache.delete(params[1]);
				table.Chathalls.delete(hallid);
				updateViewHallsFromCache(sessionid, "delete hall succeed", roomlist);
				return true;
			});
		});

		cmdmap.put("createroom", (sessionid, params) -> {
			if (params.length < 3 || !UserInfo.getInstance(sessionid).isCommandMode())
				return;
			Procedure.execute(() -> {
				Long hallid = table.Hallnamecache.select(params[1]);
				if (null == hallid) {
					UserInfo.getInstance(sessionid)._setRecvedmessage(new ChatMessage("bad hall name", sessionid));
					return false;
				}
				if (!GlobalId.create(groupRoomNames, params[2])) {
					UserInfo.getInstance(sessionid)._setRecvedmessage(new ChatMessage("bad room name", sessionid));
					return false;
				}
				Pair<Long, xbean.ChatRoomInfo> pvs = table.Chatrooms.insert();
				pvs.getValue().setName(params[2]);
				xbean.ChatHallInfo hallinfo = table.Chathalls.update(hallid);
				hallinfo.getRooms().add(pvs.getKey());
				updateViewHallsFromCache(sessionid, "create room succeed", Collections.emptyList());
				return true;
			});
		});

		cmdmap.put("deleteroom", (sessionid, params) -> {
			if (params.length < 3 || !UserInfo.getInstance(sessionid).isCommandMode())
				return;
			Procedure.execute(() -> {
				Long hallid = table.Hallnamecache.select(params[1]);
				if (null == hallid) {
					UserInfo.getInstance(sessionid)._setRecvedmessage(new ChatMessage("bad hall name", sessionid));
					return false;
				}
				xbean.ChatHallInfo hallinfo = table.Chathalls.update(hallid);
				Long removeroomid = null;
				for (Long roomid : hallinfo.getRooms()) {
					xbean.ChatRoomInfo roominfo = table.Chatrooms.update(roomid);
					if (params[2].equals(roominfo.getName())) {
						removeroomid = roomid;
						break;
					}
				}
				if (null == removeroomid) {
					UserInfo.getInstance(sessionid)._setRecvedmessage(new ChatMessage("bad room name", sessionid));
					return false;
				}
				GlobalId.delete(groupRoomNames, params[2]);
				table.Chatrooms.delete(removeroomid);
				hallinfo.getRooms().remove(removeroomid);
				ChatRoom view = table.Roominfocache.update(removeroomid);
				List<ChatRoom> roomlist = new ArrayList<>();
				if (null != view) {
					roomlist.add(view);
					table.Roominfocache.delete(removeroomid);
				}
				updateViewHallsFromCache(sessionid, "delete room succeed", roomlist);
				return true;
			});
		});

	}

	private static boolean enableCommand;

	public static boolean checkAndRunCommand(long sessionid, String msg) {
		if (!enableCommand)
			return false;
		msg = msg.toLowerCase();
		if (!msg.startsWith(commandPrefixString))
			return false;
		msg = msg.substring(commandPrefixString.length());
		if (msg.isEmpty())
			return true;
		String[] params = msg.split(" ");
		Command cmd = cmdmap.get(params[0]);
		if (null != cmd)
			cmd.runCommand(sessionid, params);
		return true;
	}

	public static void main(String[] args) throws Exception {
		enableCommand = args.length == 1 && args[0].equals("enableCommand");
		Service.run("service-chatserver.xml");
	}
}
