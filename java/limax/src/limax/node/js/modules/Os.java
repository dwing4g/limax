package limax.node.js.modules;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import limax.node.js.EventLoop;
import limax.node.js.Module;

public final class Os implements Module {
	private final static Properties properties = System.getProperties();

	public Os(EventLoop eventLoop) {
		this.EOL = properties.getProperty("line.separator");
	}

	public final String EOL;

	public String arch() {
		return properties.getProperty("os.arch");
	}

	public String endianness() {
		return properties.getProperty("sun.cpu.endian").equals("little") ? "LE" : "BE";
	}

	public String homedir() {
		return properties.getProperty("user.home");
	}

	public String hostname() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			return "";
		}
	}

	private List<Map<String, Object>> getInterfaceInfo(NetworkInterface interf) throws Exception {
		List<Map<String, Object>> list = new ArrayList<>();
		byte[] b = interf.getHardwareAddress();
		String mac = b == null ? "00:00:00:00:00:00"
				: String.format("%02x:%02x:%02x:%02x:%02x:%02x", b[0], b[1], b[2], b[3], b[4], b[5]);
		for (InterfaceAddress ia : interf.getInterfaceAddresses()) {
			Map<String, Object> map = new HashMap<>();
			map.put("mac", mac);
			map.put("internal", interf.isLoopback());
			InetAddress address = ia.getAddress();
			map.put("address", ia.getAddress().getHostAddress());
			System.out.println("[" + ia.getAddress().getHostAddress() + "]");
			int plen = ia.getNetworkPrefixLength();
			int ilen = plen >> 3;
			int blen = plen & 7;
			b = address.getAddress();
			Arrays.fill(b, (byte) 0);
			Arrays.fill(b, 0, ilen, (byte) -1);
			if (blen != 0)
				b[ilen] = (byte) (-1 << (8 - blen));
			map.put("netmask", InetAddress.getByAddress(b).getHostAddress());
			if (address instanceof Inet4Address) {
				map.put("family", "IPv4");
			} else {
				map.put("family", "IPv6");
				map.put("scopeid", ((Inet6Address) address).getScopeId());
			}
			list.add(map);
		}
		return list;
	}

	private void getNetworkInterfaces(Map<String, List<Map<String, Object>>> r) throws Exception {
		for (Enumeration<NetworkInterface> e0 = NetworkInterface.getNetworkInterfaces(); e0.hasMoreElements();) {
			NetworkInterface interf = e0.nextElement();
			if (interf.isVirtual()) {
				for (Enumeration<NetworkInterface> e1 = interf.getSubInterfaces(); e1.hasMoreElements();) {
					interf = e1.nextElement();
					if (interf.isUp())
						r.put(interf.getName(), getInterfaceInfo(interf));
				}
			} else if (interf.isUp())
				r.put(interf.getName(), getInterfaceInfo(interf));
		}
	}

	public Map<String, List<Map<String, Object>>> networkInterfaces() {
		Map<String, List<Map<String, Object>>> r = new HashMap<>();
		try {
			getNetworkInterfaces(r);
		} catch (Exception e) {
		}
		return r;
	}

	public String platform() {
		return properties.getProperty("os.name");
	}

	public String release() {
		return properties.getProperty("os.version");
	}

	public String tmpdir() {
		return properties.getProperty("java.io.tmpdir");
	}

	public String type() {
		return properties.getProperty("os.name");
	}

	public String[] userInfo() {
		return new String[] { properties.getProperty("user.name"), properties.getProperty("user.home"), };
	}

}
