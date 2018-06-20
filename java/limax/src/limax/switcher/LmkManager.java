package limax.switcher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import limax.codec.Octets;
import limax.net.Engine;
import limax.switcher.switcherauany.Exchange;
import limax.switcherauany.AuanyAuthArg;

class LmkManager {
	private final static LmkManager instance = new LmkManager();
	private final static long RETRY_PERIOD = 10000;

	private LmkManager() {
	}

	static LmkManager getInstance() {
		return instance;
	}

	private final Map<String, Future<?>> map = new ConcurrentHashMap<>();
	private volatile LmkMasquerade lmkMasquerade;

	void setup(LmkMasquerade lmkMasquerade) {
		this.lmkMasquerade = lmkMasquerade;
		LmkStore.recover(lmkMasquerade);
	}

	boolean inspect(AuanyAuthArg auth, Octets passphrase) {
		LmkMasquerade lmk = lmkMasquerade;
		return lmk == null || !auth.platflag.startsWith("lmk")
				|| lmk.masquerade(auth.username, auth.token, passphrase, (username, notAfter) -> {
					auth.username = username;
					auth.token = "";
				});
	}

	private static void done(Future<?> future) {
		if (future != null)
			future.cancel(false);
	}

	void upload(LmkInfo lmkInfo) {
		Exchange exchange = new Exchange(Exchange.UPLOAD_LMKDATA, lmkInfo.encode());
		done(map.put(lmkInfo.getUid(), Engine.getProtocolScheduler().scheduleAtFixedRate(() -> {
			try {
				exchange.send(AuanyClientListener.getInstance().getTransport());
			} catch (Exception e) {
			}
		}, 0, RETRY_PERIOD, TimeUnit.MILLISECONDS)));
	}

	void ack(String uid) {
		done(map.remove(uid));
		LmkStore.done(uid);
	}
}
