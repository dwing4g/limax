package limax.node.js.modules;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import limax.node.js.EventLoop;
import limax.node.js.Module;
import limax.util.Pair;

public final class Dns implements Module {
	@FunctionalInterface
	private interface DirContextConsumer {
		void accept(DirContext consumer) throws Exception;
	}

	private static class DirContextPool {
		private final LinkedBlockingQueue<Pair<DirContext, String>> pool = new LinkedBlockingQueue<>(
				Integer.getInteger("limax.node.js.modules.Dns.corePoolSize", 16));

		void runInDirContext(DirContextConsumer consumer, String url) throws Exception {
			Pair<DirContext, String> pair = pool.poll();
			DirContext ctx;
			if (pair == null) {
				ctx = new InitialDirContext();
				ctx.addToEnvironment(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
				if (url != null)
					ctx.addToEnvironment(Context.DNS_URL, url);
			} else {
				ctx = pair.getKey();
				if (url != pair.getValue())
					if (url != null)
						ctx.addToEnvironment(Context.DNS_URL, url);
					else
						ctx.removeFromEnvironment(Context.DNS_URL);
			}
			try {
				consumer.accept(ctx);
			} finally {
				if (!pool.offer(new Pair<DirContext, String>(ctx, url)))
					ctx.close();
			}
		}
	}

	private final static DirContextPool ctxs = new DirContextPool();
	private final static int MASK_IPV4 = 1;
	private final static int MASK_IPV6 = 2;
	private final EventLoop eventLoop;
	private final int ADDRCONFIG_MASK;
	public final int ADDRCONFIG = 1024;
	public final int V4MAPPED = 2048;
	private volatile String dnsURL = null;

	public Dns(EventLoop eventLoop) throws Exception {
		this.eventLoop = eventLoop;
		int mask = 0;
		try {
			for (Enumeration<NetworkInterface> e0 = NetworkInterface.getNetworkInterfaces(); e0.hasMoreElements();) {
				for (Enumeration<InetAddress> e1 = e0.nextElement().getInetAddresses(); e1.hasMoreElements();) {
					InetAddress address = e1.nextElement();
					if (address.isLoopbackAddress())
						continue;
					if (address instanceof Inet4Address)
						mask |= MASK_IPV4;
					if (address instanceof Inet6Address)
						mask |= MASK_IPV6;
				}
			}
		} catch (Exception e) {
			mask = MASK_IPV4 | MASK_IPV6;
		}
		ADDRCONFIG_MASK = mask;
	}

	public String getServers() throws NamingException {
		return dnsURL == null ? "" : dnsURL;
	}

	public void setServers(String[] servers) throws NamingException {
		StringBuilder sb = new StringBuilder();
		for (String s : servers)
			sb.append("dns://").append(s).append(' ');
		dnsURL = sb.toString();
	}

	private void query(String hostname, String rrtype, List<Object> r) throws Exception {
		ctxs.runInDirContext(ctx -> {
			for (NamingEnumeration<? extends Attribute> e0 = ctx.getAttributes(hostname, new String[] { rrtype })
					.getAll(); e0.hasMore();) {
				for (NamingEnumeration<?> e1 = e0.next().getAll(); e1.hasMore();)
					r.add(e1.next());
			}
			if (r.size() == 1)
				throw new UnknownHostException("query " + rrtype + " ENODATA " + hostname);
		}, dnsURL);
	}

	public void resolve(String hostname, String rrtype, Object callback) {
		eventLoop.execute(callback, r -> query(hostname, rrtype, r));
	}

	private Pair<Integer, String> filterAddress(int mask, InetAddress addr, boolean v4mapped) {
		if ((mask & MASK_IPV4) != 0) {
			if (addr instanceof Inet4Address)
				return new Pair<Integer, String>(4, addr.getHostAddress());
		}
		if ((mask & MASK_IPV6) != 0) {
			if (addr instanceof Inet6Address)
				return new Pair<Integer, String>(6, addr.getHostAddress());
			if (addr instanceof Inet4Address && v4mapped)
				return new Pair<Integer, String>(6, "::ffff:" + addr.getHostAddress());
		}
		return null;
	}

	public void lookup(String hostname, Object family, int hints, boolean all, Object callback) {
		eventLoop.execute(callback, r -> {
			int mask = MASK_IPV4 | MASK_IPV6;
			if (family instanceof Number) {
				switch (((Number) family).intValue()) {
				case 4:
					mask = MASK_IPV4;
					break;
				case 6:
					mask = MASK_IPV6;
				}
			}
			if ((hints & ADDRCONFIG) != 0)
				mask &= ADDRCONFIG_MASK;
			boolean v4mapped = (hints & V4MAPPED) != 0;
			for (InetAddress addr : InetAddress.getAllByName(hostname)) {
				Pair<Integer, String> pair = filterAddress(mask, addr, v4mapped);
				if (pair != null) {
					r.add(pair);
					if (!all)
						break;
				}
			}
			if (r.size() == 1)
				throw new UnknownHostException("query ENODATA " + hostname);
		});
	}
}
