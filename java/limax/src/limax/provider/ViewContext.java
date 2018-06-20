package limax.provider;

import java.util.Map;

public interface ViewContext {
	GlobalView findGlobalView(short classindex);

	SessionView findSessionView(long sessionid, short classindex);

	TemporaryView findTemporaryView(long sessionid, short classindex, int instanceindex);

	TemporaryView createTemporaryView(short classindex, boolean loose, int partition);

	void closeTemporaryView(TemporaryView view);

	int getProviderId();

	Map<String, Integer> getDataDictionary();
}
