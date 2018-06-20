package limax.auany;

public interface KeyManager {
	byte[] getKey(int index);

	int getRecentIndex();
}
