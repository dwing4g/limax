package limax.node.js.modules;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import limax.node.js.Buffer;
import limax.node.js.EventLoop;
import limax.node.js.EventLoop.Callback;
import limax.node.js.EventLoop.EventObject;
import limax.node.js.Module;
import limax.util.Trace;

public final class Dgram implements Module {
	private final static Selector selector;
	private final static int MTUMAX;
	static {
		Selector sel = null;
		try {
			sel = Selector.open();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		int mtu = 0;
		try {
			for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();)
				mtu = Math.max(e.nextElement().getMTU(), mtu);
		} catch (Exception e) {
			mtu = 9000;
		}
		MTUMAX = mtu;
		selector = sel;
		@SuppressWarnings("unchecked")
		Thread thread = new Thread(() -> {
			while (true) {
				try {
					synchronized (Dgram.class) {
					}
					selector.selectedKeys().clear();
					selector.select();
					for (SelectionKey key : selector.selectedKeys()) {
						if (key.isReadable()) {
							Object[] r;
							try {
								ByteBuffer bb = ByteBuffer.allocate(MTUMAX);
								InetSocketAddress sa = (InetSocketAddress) ((DatagramChannel) key.channel())
										.receive(bb);
								InetAddress addr = sa.getAddress();
								bb.flip();
								r = new Object[] { null, new Buffer(bb), sa.getPort(),
										addr instanceof Inet4Address ? "IPv4" : "IPv6", addr.getHostAddress() };
							} catch (Exception e1) {
								r = new Object[] { e1 };
							}
							((Consumer<Object[]>) key.attachment()).accept(r);
						}
					}
				} catch (Exception e) {
				}
			}
		}, Dgram.class.getName());
		thread.setDaemon(true);
		thread.start();
	}

	private static void register(DatagramChannel ch, Consumer<Object[]> att) {
		try {
			synchronized (Dgram.class) {
				ch.register(selector.wakeup(), SelectionKey.OP_READ, att);
			}
		} catch (ClosedChannelException e) {
		}
	}

	private static List<NetworkInterface> getUpMulticastInterfaces() {
		List<NetworkInterface> list = new ArrayList<>();
		try {
			for (Enumeration<NetworkInterface> e0 = NetworkInterface.getNetworkInterfaces(); e0.hasMoreElements();) {
				try {
					NetworkInterface interf = e0.nextElement();
					if (interf.isVirtual()) {
						for (Enumeration<NetworkInterface> e1 = interf.getSubInterfaces(); e1.hasMoreElements();) {
							interf = e1.nextElement();
							if (interf.isUp() && interf.supportsMulticast())
								list.add(interf);
						}
					} else if (interf.isUp() && interf.supportsMulticast())
						list.add(interf);
				} catch (Exception ex1) {
				}
			}
		} catch (Exception ex2) {
		}
		return list;
	}

	private final EventLoop eventLoop;

	public Dgram(EventLoop eventLoop) {
		this.eventLoop = eventLoop;
	}

	private static class MulticastKey {
		private final InetAddress group;
		private final NetworkInterface interf;

		MulticastKey(InetAddress group, NetworkInterface interf) {
			this.group = group;
			this.interf = interf;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof MulticastKey) {
				MulticastKey key = (MulticastKey) obj;
				return group.equals(key.group) && interf.equals(key.interf);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return Objects.hash(group, interf);
		}
	}

	public class Socket {
		private final DatagramChannel channel;
		private final Map<MulticastKey, MembershipKey> groups = new HashMap<>();
		private final EventObject evo;
		private final AtomicBoolean closed = new AtomicBoolean();

		Socket(boolean udp6, boolean reuseAddr, Object callback) throws Exception {
			this.channel = DatagramChannel.open(udp6 ? StandardProtocolFamily.INET6 : StandardProtocolFamily.INET)
					.setOption(StandardSocketOptions.SO_REUSEADDR, reuseAddr);
			this.evo = eventLoop.createEventObject();
			this.channel.configureBlocking(false);
			Callback cb = eventLoop.createCallback(callback);
			register(this.channel, r -> cb.call(r));
		}

		private Exception addMembership(InetAddress group, NetworkInterface interf) {
			try {
				MulticastKey key = new MulticastKey(group, interf);
				if (!groups.containsKey(key))
					groups.put(key, channel.join(group, interf));
				return null;
			} catch (Exception e) {
				return e;
			}
		}

		private Exception dropMembership(InetAddress group, NetworkInterface interf) {
			try {
				MembershipKey mkey = groups.remove(new MulticastKey(group, interf));
				if (mkey != null)
					mkey.drop();
				return null;
			} catch (Exception e) {
				return e;
			}
		}

		public Exception addMembership(String multicastAddress, String multicastInterface) {
			try {
				InetAddress group = InetAddress.getByName(multicastAddress);
				if (multicastInterface != null)
					return addMembership(group, NetworkInterface.getByName(multicastInterface));
				List<Exception> exceptions = getUpMulticastInterfaces().stream()
						.map(interf -> addMembership(group, interf)).filter(Objects::nonNull)
						.collect(Collectors.toList());
				if (exceptions.isEmpty())
					return null;
				SocketException ex = new SocketException();
				exceptions.stream().forEach(e -> ex.addSuppressed(e));
				return ex;
			} catch (Exception e) {
				return e;
			}
		}

		public Object[] address() {
			InetSocketAddress sa;
			try {
				sa = (InetSocketAddress) channel.getLocalAddress();
				InetAddress addr = sa.getAddress();
				return new Object[] { sa.getPort(), addr instanceof Inet4Address ? "IPv4" : "IPv6",
						addr.getHostAddress() };
			} catch (IOException e) {
				return new Object[] {};
			}
		}

		public Exception bind(int port, String address) {
			try {
				channel.bind(address == null ? new InetSocketAddress(port) : new InetSocketAddress(address, port));
				return null;
			} catch (Exception e) {
				return e;
			}
		}

		public void close() {
			if (closed.compareAndSet(false, true)) {
				groups.clear();
				try {
					channel.close();
				} catch (Exception e) {
				}
				evo.queue();
			}
		}

		public Exception dropMembership(String multicastAddress, String multicastInterface) {
			try {
				InetAddress group = InetAddress.getByName(multicastAddress);
				if (multicastInterface != null)
					return dropMembership(group, NetworkInterface.getByName(multicastInterface));
				List<Exception> exceptions = groups.entrySet().stream().filter(e -> group.equals(e.getKey().group))
						.map(e -> e.getKey().interf).collect(Collectors.toList()).stream()
						.map(interf -> dropMembership(group, interf)).filter(Objects::nonNull)
						.collect(Collectors.toList());
				if (exceptions.isEmpty())
					return null;
				SocketException ex = new SocketException();
				exceptions.stream().forEach(e -> ex.addSuppressed(e));
				return ex;
			} catch (Exception e) {
				return e;
			}
		}

		public void send(Buffer buffer, int offset, int length, int port, String address, Object callback) {
			eventLoop.execute(callback, r -> {
				ByteBuffer b = buffer.toByteBuffer();
				b.position(offset);
				b.limit(offset + length);
				channel.send(b, new InetSocketAddress(address, port));
			});
		}

		public void setBroadcast(boolean on) {
			try {
				channel.setOption(StandardSocketOptions.SO_BROADCAST, on);
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("Dgram.setBroadcast", e);
			}
		}

		public void setMulticastLoopback(boolean on) {
			try {
				channel.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, on);
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("Dgram.setMulticastLoopback", e);
			}
		}

		public void setMulticastTTL(int ttl) {
			try {
				channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, ttl);
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("Dgram.setMulticastTTL", e);
			}
		}

		public void setTTL(int ttl) {
			try {
				channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, ttl);
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("Dgram.setTTL", e);
			}
		}

		public void ref() {
			evo.ref();
		}

		public void unref() {
			evo.unref();
		}
	}

	public Socket createSocket(boolean udp6, boolean reuseAddr, Object callback) throws Exception {
		return new Socket(udp6, reuseAddr, callback);
	}
}
