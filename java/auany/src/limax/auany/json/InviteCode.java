package limax.auany.json;

import limax.codec.JSONSerializable;

public final class InviteCode implements JSONSerializable {
	@SuppressWarnings("unused")
	private final String code;
	@SuppressWarnings("unused")
	private final SwitcherInfo switcher;

	public InviteCode(SwitcherInfo switcher, long code) {
		this.code = Long.toString(code);
		this.switcher = switcher;
	}
}
