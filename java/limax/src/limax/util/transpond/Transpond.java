package limax.util.transpond;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.function.Supplier;

import limax.net.io.NetModel;
import limax.net.io.NetTask;
import limax.net.io.ServerContext;

public final class Transpond {
	final SocketAddress peer;
	final int buffersize;
	final boolean asynchronous;

	private Transpond(SocketAddress peer, int buffersize, boolean asynchronous) {
		this.peer = peer;
		this.buffersize = buffersize;
		this.asynchronous = asynchronous;
	}

	private ServerContext.NetTaskConstructor createNetTaskConstructor() {
		return new ServerContext.NetTaskConstructor() {
			@Override
			public NetTask newInstance(ServerContext context) {
				ProxyServerSession pss = new ProxyServerSession(Transpond.this);
				NetStation nc = new NetStation(pss, buffersize);
				pss.setAssocNetTask(nc);
				return NetModel.createServerTask(context, nc);
			}

			@Override
			public String getServiceName() {
				return Transpond.class.getName();
			}
		};
	}

	public static ServerContext startTranspond(SocketAddress local, SocketAddress peer, int buffersize,
			boolean asynchronous) throws IOException {
		return NetModel.addServer(local, 0, buffersize, buffersize, null, 0,
				new Transpond(peer, buffersize, asynchronous).createNetTaskConstructor(), false, asynchronous);
	}

	public static ServerContext startListenOnly(SocketAddress local, int buffersize, String name,
			Supplier<FlowControlProcessor> processor, boolean asynchronous) throws IOException {
		return NetModel.addServer(local, 0, buffersize / 2, buffersize, null, 0,
				new ServerContext.NetTaskConstructor() {
					@Override
					public NetTask newInstance(ServerContext context) {
						return NetModel.createServerTask(context, new NetStation(processor.get(), buffersize));
					}

					@Override
					public String getServiceName() {
						return name;
					}
				}, false, asynchronous);
	}

	public static void startConnectOnly(SocketAddress peer, int buffersize, FlowControlProcessor processor,
			boolean asynchronous) throws IOException {
		NetModel.addClient(peer, NetModel.createClientTask(buffersize / 2, buffersize, null,
				new NetStation(processor, buffersize), asynchronous));
	}
}
