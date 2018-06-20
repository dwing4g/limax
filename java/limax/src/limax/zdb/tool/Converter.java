package limax.zdb.tool;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;

/**
 * you can use DBTool to generate helper wrapper class
 */
public interface Converter {
	OctetsStream convertKey(OctetsStream key) throws MarshalException;

	OctetsStream convertValue(OctetsStream value) throws MarshalException;
}
