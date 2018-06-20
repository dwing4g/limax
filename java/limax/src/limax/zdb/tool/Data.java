package limax.zdb.tool;

import limax.codec.Marshal;

public interface Data extends Marshal {
	void convertTo(Data target);
}
