package chat.chatclient.chatviews;

import limax.endpoint.ViewContext;
import chat.chatclient.ChatRoomFrame;
import chat.chatclient.MainFrame;

public final class ChatRoom extends _ChatRoom {

	private ChatRoomFrame frame = null;

	private ChatRoom(ViewContext vc) {
		super(vc);
	}

	protected void onOpen(java.util.Collection<Long> sessionids) {
		frame = MainFrame.getInstance().showChatRoom(this);
	}

	protected void onClose() {
		frame.onViewClose();
	}

	protected void onAttach(long sessionid) {
		frame.onMemberAttach(sessionid);
	}

	protected void onDetach(long sessionid, byte reason) {
		frame.onMemberDetach(sessionid, reason);
	}

}
