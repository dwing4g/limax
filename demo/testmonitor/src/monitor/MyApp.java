package monitor;

import java.util.Random;

import limax.util.MBeanServer;
import testmonitor.AuthProvider;
import testmonitor.Online;

public class MyApp {

	public static void main(String[] args) throws Exception {
		MBeanServer.start("127.0.0.1", 10202, 10201, null, null);
		String[] plat = { "apple", "onesdk", "xiaomi", "meizu" };

		Random r = new Random();
		while (true) {
			AuthProvider.increment_auth(plat[r.nextInt(plat.length)], (short) r.nextInt(33), r.nextInt(10));
			AuthProvider.increment_newaccount(plat[r.nextInt(plat.length)], (short) r.nextInt(33), r.nextInt(3));
			Online.set_online((short) r.nextInt(100), (byte) r.nextInt(2), (short) r.nextInt(5),
					(long) r.nextInt(1000));
			Thread.sleep(r.nextInt(10));
		}

	}

}
