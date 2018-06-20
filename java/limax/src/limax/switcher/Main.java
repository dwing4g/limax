package limax.switcher;

import limax.defines.SessionType;
import limax.xmlconfig.Service;

public class Main {
	private Main() {
	}

	public static boolean isSessionUseViewProtocol(byte v) {
		return isSessionUseStatic(v) || isSessionUseVariant(v);
	}

	public static boolean isSessionUseStatic(byte v) {
		return SessionType.ST_STATIC == (SessionType.ST_STATIC & v);
	}

	public static boolean isSessionUseVariant(byte v) {
		return SessionType.ST_VARIANT == (SessionType.ST_VARIANT & v);
	}

	public static boolean isSessionUseScript(byte v) {
		return SessionType.ST_SCRIPT == (SessionType.ST_SCRIPT & v);
	}

	public static void main(String[] args) throws Exception {
		final String netioxml = args.length == 0 ? "service-switcher.xml" : args[0];
		limax.provider.states.ProviderClient.getDefaultState();
		Service.run(netioxml);
	}
}
