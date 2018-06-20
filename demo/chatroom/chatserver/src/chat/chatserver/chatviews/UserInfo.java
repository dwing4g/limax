package chat.chatserver.chatviews;

import chat.chatserver.SessionManager;
import chat.chatviews.ErrorCodes;
import chat.chatviews.ViewChatRoomInfo;
import chat.chatviews.monitor.SysInfo;
import limax.provider.GlobalId;
import limax.provider.ProcedureHelper;
import limax.provider.SessionView;
import limax.zdb.Procedure;

public final class UserInfo extends chat.chatserver.chatviews._UserInfo {
	private long logintime;
	private long updateroominfotime;
	private boolean commandmode;

	private UserInfo(SessionView.CreateParameter param) {
		super(param);
		// bind here
		long sessionid = param.getSessionId();
		bindUserinfo(sessionid);
		Procedure.execute(ProcedureHelper.nameProcedure("UserInfo init nickname", () -> {
			xbean.UserInfo userinfo = table.Userinfo.insert(sessionid);
			if (userinfo != null)
				userinfo.setNickname("");
			return true;
		}));
		logintime = System.currentTimeMillis();
		checkUpdateHallInfos();
	}

	void checkUpdateHallInfos() {
		if (updateroominfotime < SessionManager.lastSetHallsTimeStamp) {
			updateroominfotime = System.currentTimeMillis();
			CommonInfo.getInstance().syncToClient(getSessionId());
		}
	}

	public boolean isCommandMode() {
		return commandmode;
	}

	public void setCommandMode(boolean commandmode) {
		this.commandmode = commandmode;
	}

	@Override
	protected void onClose() {
		SysInfo.increment_loginTime(System.currentTimeMillis() - logintime);
	}

	@Override
	protected void onMessage(String message, long sessionid) {
		final int index = message.indexOf('=');
		if (-1 == index)
			return;
		checkUpdateHallInfos();
		final String cmd = message.substring(0, index).trim();
		final String param = message.substring(index + 1).trim();
		switch (cmd) {
		case "nickname":
			Procedure.execute(ProcedureHelper.nameProcedure("onMessage nickname", () -> {
				xbean.UserInfo info = table.Userinfo.update(sessionid);
				if (param.equals(info.getNickname())) {
					UserInfo.this._setLasterror(ErrorCodes.EC_NAME_UNMODIFIED);
					return false;
				}
				if (!GlobalId.create("chatserver", param)) {
					UserInfo.this._setLasterror(ErrorCodes.EC_NAME_EXISTING);
					return false;
				}
				info.setNickname(param);
				SysInfo.increment_nickNameChange();
				return true;
			}));
			return;
		case "join":
			joinRoom(Long.parseLong(param), sessionid);
			return;
		case "joinbyname":
			Procedure.execute(ProcedureHelper.nameProcedure("onMessage joinbyname", () -> {
				String[] args = param.split(",");
				if (args.length < 2) {
					UserInfo.this._setLasterror(ErrorCodes.EC_BAD_ARGS);
					return false;
				}
				Long hallid = table.Hallnamecache.select(args[0]);
				if (null == hallid) {
					UserInfo.this._setLasterror(ErrorCodes.EC_BAD_HALL_NAME);
					return false;
				}
				xbean.ChatHallInfo hallinfo = table.Chathalls.select(hallid);
				Long removeroomid = null;
				for (Long roomid : hallinfo.getRooms()) {
					xbean.ChatRoomInfo roominfo = table.Chatrooms.select(roomid);
					if (args[1].equals(roominfo.getName())) {
						removeroomid = roomid;
						break;
					}
				}
				if (null == removeroomid) {
					UserInfo.this._setLasterror(ErrorCodes.EC_BAD_ROOM_NAME);
					return false;
				}
				joinRoom(removeroomid, sessionid);
				return true;
			}));
			return;
		}
	}

	private void joinRoom(long roomid, long sessionid) {
		Procedure.execute(ProcedureHelper.nameProcedure("joinRoom", () -> {
			ChatRoom view = table.Roominfocache.update(roomid);
			if (null == view) {
				final xbean.ChatRoomInfo info = table.Chatrooms.select(roomid);
				if (null == info) {
					UserInfo.this._setLasterror(ErrorCodes.EC_BAD_ROOM_ID);
					return false;
				}
				view = ChatRoom.createInstance();
				final ChatRoom _view = view;
				ProcedureHelper.executeWhileRollback(() -> _view.destroyInstance());
				view.setInfo(new ViewChatRoomInfo(info.getName(), roomid));
				view.setRoomId(roomid);
				table.Roominfocache.insert(roomid, view);
			}
			view.getMembership().add(sessionid);
			return true;
		}));
	}
}
