package limax.net;

/**
 * 
 * <p>
 * Specification
 * 
 * <p>
 * 1. onManagerInitialized MUST BE the first message
 * 
 * <p>
 * 2. onManagerUninitialized MUST BE the last message
 * 
 * <p>
 * 3. according to the same transport, onTransportRemoved MUST happen after
 * onTransportAdded
 * 
 * <p>
 * 4. any circumstance, onTransportAdded, onTransportRemoved MUST deliver
 * paired.
 * 
 * <p>
 * 5. onTransportAdded throws Exception, onTransportRemoved MUST NOT be
 * delivered
 * 
 * <p>
 * 6. message exchange MUST NOT happening before onTransportAdded
 * 
 */

public interface Listener {
	void onManagerInitialized(Manager manager, Config config);

	void onManagerUninitialized(Manager manager);

	void onTransportAdded(Transport transport) throws Exception;

	void onTransportRemoved(Transport transport) throws Exception;
}
