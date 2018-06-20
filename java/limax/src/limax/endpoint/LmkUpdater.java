package limax.endpoint;

import limax.codec.Octets;

public interface LmkUpdater {
	void update(Octets lmkdata, Runnable done) throws Exception;
}
