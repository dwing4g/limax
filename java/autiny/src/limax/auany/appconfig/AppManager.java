package limax.auany.appconfig;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.w3c.dom.Element;

import limax.auany.json.SwitcherInfo;
import limax.http.HttpHandler;

public class AppManager {

	public static void updateJSON(int pvid, String json) {
	}

	public static int getMaxSubordinates(int appid) {
		return 0;
	}

	public static Integer checkAppId(Set<Integer> pvids) {
		return 0;
	}

	public static SwitcherInfo randomSwitcher(ServiceType type, int appid) {
		return null;
	}

	private static final Map<Integer, TinyProvider> providermap = new ConcurrentHashMap<>();

	public static boolean verifyProviderKey(int pvid, String key) {
		return true;
	}

	public static void providerUp(int pvid, boolean paySupport, Set<Integer> switcherIds) {
		providermap.computeIfAbsent(pvid, TinyProvider::new)
				.updateStatus(paySupport ? Provider.Status.ON_PAY : Provider.Status.ON);
	}

	public static void providerDown(Integer pvid, Set<Integer> switcherIds) {
		providermap.computeIfAbsent(pvid, TinyProvider::new).updateStatus(Provider.Status.OFF);
	}

	public static long initProvider(int pvid, String json) {
		providermap.computeIfAbsent(pvid, TinyProvider::new).updateJSON(json);
		return 30000l;
	}

	public static String updateSwitcher(String host, String key, ArrayList<Integer> nativeIds, ArrayList<Integer> wsIds,
			ArrayList<Integer> wssIds) {
		return "";
	}

	public static void initialize(Element self, BiConsumer<String, HttpHandler> httphandlers) throws Exception {
	}

}
