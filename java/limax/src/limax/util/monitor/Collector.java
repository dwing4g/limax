package limax.util.monitor;

import java.util.Map;

import javax.management.ObjectName;

public interface Collector {
	void onController(CollectorController controller);

	void onRecord(String host, ObjectName objname, Map<String, Object> item);

	default void onException(String host, Exception e) {
	}
}
