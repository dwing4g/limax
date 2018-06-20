package limax.endpoint.script;

import java.util.Set;

import limax.codec.Octets;
import limax.endpoint.ProviderLoginDataManager;

public interface ScriptEngineHandle {
	Set<Integer> getProviders();

	int action(int t, Object p) throws Exception;

	void registerScriptSender(ScriptSender sender) throws Exception;

	void registerLmkDataReceiver(LmkDataReceiver receiver);

	void registerProviderLoginManager(ProviderLoginDataManager pldm) throws Exception;

	DictionaryCache getDictionaryCache();

	void tunnel(int providerid, int label, String data) throws Exception;

	void tunnel(int providerid, int label, Octets data) throws Exception;
}
