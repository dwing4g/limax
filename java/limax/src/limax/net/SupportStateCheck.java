package limax.net;

/**
 * transport capability
 */
public interface SupportStateCheck {
	void check(int type, int size) throws InstantiationException, SizePolicyException;
}
