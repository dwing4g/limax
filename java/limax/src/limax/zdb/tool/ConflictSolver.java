package limax.zdb.tool;

import limax.codec.MarshalException;
import limax.codec.Octets;

/**
 * <p>
 * key conflict solver when merging 2 tables.
 * <p>
 * called after Converter.convert and insert failed.
 * <p>
 * source and target has same target structure;
 * <p>
 * after solve called, DBC.Table will replace target with the returned data.
 */
public interface ConflictSolver {
	Octets solve(Octets sourceValue, Octets targetValue, Octets key) throws MarshalException;
}
