
package chat.chatserver.chatviews;

import limax.provider.GlobalView;

public final class CommonInfo extends chat.chatserver.chatviews._CommonInfo {

	private CommonInfo(GlobalView.CreateParameter param) {
		super(param);
		// bind here
	}

	@Override
	protected void onClose() {
	}

	@Override
	protected void onMessage(String message, long sessionid) {
	}

}
