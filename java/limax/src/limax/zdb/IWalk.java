package limax.zdb;

/**
 * 
 * @see DBC.Table
 *
 */

public interface IWalk {
	boolean onRecord(byte[] key, byte[] data);
}