package limax.auany;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import limax.auany.switcherauany.Exchange;
import limax.http.HttpHandler;
import limax.net.Transport;
import limax.pkix.SSLContextAllocator;
import limax.switcher.LmkConfigData;
import limax.switcher.LmkInfo;
import limax.switcher.LmkMasquerade;
import limax.switcher.LmkStore;
import limax.util.ElementHelper;
import limax.util.Trace;
import limax.zdb.ManagementFactory;

public class LmkManager {
	private static final long defaultLifetime = Long.getLong("limax.auany.LmkManager.defaultLifetime", 8640000000L);
	private static final int renewConcurrency = Integer.getInteger("limax.auany.LmkManager.renewConcurrency", 16);
	private static SSLContextAllocator sslContextAllocator;
	private static volatile Exchange exchange;
	private static volatile LmkMasquerade lmkMasquerade;
	private static final Object doneLock = new Object();
	private static List<String> doneCurr = new ArrayList<>();
	private static List<String> doneLast = new ArrayList<>();
	private static int countCheckpoint;

	private static final Consumer<LmkInfo> lmkInfoConsumer = lmkInfo -> {
		String uid = lmkInfo.getUid();
		Account.addLmkData(uid, lmkInfo.getLmkData(), () -> {
			List<String> done = null;
			synchronized (doneLock) {
				int count = ManagementFactory.getCheckpointMBean().getCountCheckpoint();
				switch (count - countCheckpoint) {
				case 0:
					doneCurr.add(uid);
					break;
				case 1:
					doneLast = doneCurr;
					doneCurr = Arrays.asList(uid);
					break;
				case 2:
					done = doneLast;
					doneLast = doneCurr;
					doneCurr = Arrays.asList(uid);
					break;
				default:
					done = doneLast;
					done.addAll(doneCurr);
					doneLast = new ArrayList<>();
					doneCurr = Arrays.asList(uid);
				}
				countCheckpoint = count;
			}
			if (done != null)
				done.forEach(u -> LmkStore.done(u));
		});
	};

	private static Exchange reload(ElementHelper eh) throws Exception {
		LmkConfigData lmkConfigData = new LmkConfigData(sslContextAllocator, Paths.get(eh.getString("trustsPath")),
				eh.getString("revocationCheckerOptions"), eh.getBoolean("validateDate", true), defaultLifetime,
				renewConcurrency);
		lmkMasquerade = lmkConfigData.createLmkMasquerade(lmkInfoConsumer);
		return new Exchange(Exchange.CONFIG_LMKMASQUERADE, lmkConfigData.encode());
	}

	static void initialize(Element self, BiConsumer<String, HttpHandler> httphandlers) throws Exception {
		ElementHelper eh = new ElementHelper(self);
		if (!eh.getBoolean("enable", false))
			return;
		String passphrase = eh.getString("passphrase", null);
		sslContextAllocator = new SSLContextAllocator(URI.create(eh.getString("location")), passphrase == null
				? prompt -> System.console().readPassword(prompt) : prompt -> passphrase.toCharArray());
		exchange = reload(eh);
		LmkStore.recover(lmkMasquerade);
		sslContextAllocator.addChangeListener(ctx -> {
			try {
				SessionManager.broadcast(exchange = reload(eh));
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("LmkManager reload", e);
			}
		});
		eh.warnUnused();
	}

	static void sendConfigData(Transport transport) throws Exception {
		if (exchange != null)
			exchange.send(transport);
	}

	static LmkMasquerade getLmkMasquerade() {
		return lmkMasquerade;
	}
}
