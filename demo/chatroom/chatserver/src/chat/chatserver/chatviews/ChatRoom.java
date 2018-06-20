package chat.chatserver.chatviews;

import chat.chatserver.Main;
import chat.chatviews.ChatMessage;
import chat.chatviews.monitor.RoomInfo;
import limax.provider.TemporaryView;
import limax.provider.TemporaryView.Membership.AbortReason;

public final class ChatRoom extends chat.chatserver.chatviews._ChatRoom {

	private ChatRoom(TemporaryView.CreateParameter param) {
		super(param);
		ChatRoom.this.setLastmessage(new ChatMessage("", -1));
	}

	@Override
	protected void onClose() {
	}

	@Override
	protected void onAttachAbort(long sessionid, AbortReason reason) {
	}

	@Override
	protected void onDetachAbort(long sessionid, AbortReason reason) {
	}

	@Override
	protected void onAttached(long sessionid) {
		RoomInfo.increment_memberCount_mapkey(roomid, 1L);
	}

	@Override
	protected void onDetached(long sessionid, byte reason) {
		RoomInfo.increment_memberCount_mapkey(roomid, -1L);
		UserInfo info = UserInfo.getInstance(sessionid);
		if (info != null)
			info.checkUpdateHallInfos();
	}

	private long roomid;

	void setRoomId(long id) {
		roomid = id;
	}

	@Override
	protected void onMessage(String message, long sessionid) {
		int index = message.indexOf('=');
		if (-1 == index)
			return;
		final String cmd = message.substring(0, index).trim();
		final String params = message.substring(index + 1);
		switch (cmd) {
		case "message": {
			index = params.indexOf(',');
			if (-1 == index)
				return;
			final long toid = Long.parseLong(params.substring(0, index).trim());
			final String msg = params.substring(index + 1).trim();
			if (Main.checkAndRunCommand(sessionid, msg))
				return;
			if (-1L == toid) {
				ChatRoom.this.setLastmessage(new ChatMessage(msg, sessionid));
				RoomInfo.increment_publicMessages_mapkey(roomid);
			} else {
				UserInfo.getInstance(toid).setRecvedmessage(new ChatMessage(msg, sessionid));
				UserInfo.getInstance(sessionid).setSendedmessage(new ChatMessage(msg, toid));
				RoomInfo.increment_privateMessages_mapkey(roomid);
			}
			return;
		}
		case "leave": {
			getMembership().remove(sessionid, (byte) 1);
			return;
		}
		}
	}

}
