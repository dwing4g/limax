package limax.zdb;

public interface Listener {
	default void onChanged(Object key, Object value) {
	}

	default void onRemoved(Object key, Object value) {
	}

	default void onChanged(Object key, Object value, String fullVarName, Note note) {
	}
}
