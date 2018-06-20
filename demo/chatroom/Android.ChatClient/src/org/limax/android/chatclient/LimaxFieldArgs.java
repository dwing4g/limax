package org.limax.android.chatclient;

import limax.endpoint.ViewChangedType;
import limax.endpoint.variant.Variant;

public interface LimaxFieldArgs {
	String getView();

	long getSessionId();

	String getFieldName();

	Variant getValue();

	ViewChangedType getType();
}
