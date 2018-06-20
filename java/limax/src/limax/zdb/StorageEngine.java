package limax.zdb;

import limax.codec.Octets;

interface StorageEngine {
	Octets find(Octets key);

	boolean exist(Octets key);

	boolean insert(Octets key, Octets value);

	void replace(Octets key, Octets value);

	void remove(Octets key);

	void walk(IWalk iw);
}
