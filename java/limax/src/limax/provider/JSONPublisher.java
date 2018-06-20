package limax.provider;

import limax.codec.JSONSerializable;

public interface JSONPublisher {
	JSONSerializable getJSON();

	long getDelay();
}
