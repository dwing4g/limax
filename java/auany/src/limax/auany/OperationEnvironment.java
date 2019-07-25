package limax.auany;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.BiConsumer;

import org.w3c.dom.Element;

import limax.http.HttpHandler;
import limax.util.ElementHelper;

public final class OperationEnvironment {
	private static int identity;
	private static byte[][] keys;

	public static int getEnvironmentIdentity() {
		return identity;
	}

	public static KeyManager getKeyManager() {
		return new KeyManager() {

			@Override
			public byte[] getKey(int index) {
				return keys[index];
			}

			@Override
			public int getRecentIndex() {
				return keys.length - 1;
			}
		};
	}

	static void initialize(Element self, BiConsumer<String, HttpHandler> httphandlers) throws Exception {
		ElementHelper eh = new ElementHelper(self);
		identity = eh.getInt("identity");
		keys = Arrays.stream(eh.getString("keys").split(",")).map(s -> s.getBytes(StandardCharsets.UTF_8))
				.toArray(byte[][]::new);
		Invite.init(self, httphandlers);
		eh.warnUnused("parserClass", "inviteExpire");
	}

	static void unInitialize() {

	}
}
