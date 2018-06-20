package limax.net;

/**
 * manager capability
 */

public interface SupportDispatch {
	void dispatch(Runnable r, Object hit);
}
