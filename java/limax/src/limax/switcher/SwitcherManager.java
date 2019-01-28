package limax.switcher;

import java.io.IOException;

import limax.codec.CodecException;
import limax.codec.Octets;
import limax.net.Config;
import limax.net.Engine;
import limax.net.Listener;
import limax.net.Manager;
import limax.net.ServerManager;
import limax.net.ServerManagerConfig;
import limax.net.SizePolicyException;
import limax.net.SupportDispatch;
import limax.net.SupportRpcContext;
import limax.net.SupportTypedDataBroadcast;
import limax.net.SupportUnknownProtocol;
import limax.net.SupportWebSocketBroadcast;
import limax.net.SupportWebSocketProtocol;
import limax.net.Transport;
import limax.net.UnknownProtocolHandler;
import limax.net.WebSocketProtocol;
import limax.util.Closeable;

final class SwitcherManager implements ServerManager, SupportTypedDataBroadcast, SupportRpcContext,
		SupportUnknownProtocol, SupportDispatch, SupportWebSocketProtocol, SupportWebSocketBroadcast {

	private final ServerManager manager;
	private final ServerManagerConfig config;

	SwitcherManager(ServerManagerConfig config) throws Exception {
		manager = (ServerManager) Engine.add(config, SwitcherListener.getInstance(), this);
		this.config = config;
	}

	@Override
	public UnknownProtocolHandler getUnknownProtocolHandler() {
		return SwitcherListener.getInstance().unknownProtocolHandler;
	}

	@Override
	public void closeAllContexts() {
		((SupportRpcContext) manager).closeAllContexts();
	}

	@Override
	public Closeable removeContext(int sid) {
		return ((SupportRpcContext) manager).removeContext(sid);
	}

	@Override
	public <T> T removeContext(int sid, T hint) {
		return ((SupportRpcContext) manager).removeContext(sid, hint);
	}

	@Override
	public int addContext(Closeable obj) {
		return ((SupportRpcContext) manager).addContext(obj);
	}

	@Override
	public void broadcast(int type, Octets data)
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
		((SupportTypedDataBroadcast) manager).broadcast(type, data);
	}

	@Override
	public void close() {
		if (Engine.remove(this))
			return;
		manager.close();
	}

	@Override
	public void close(Transport transport) {
		manager.close(transport);
	}

	@Override
	public void dispatch(Runnable r, Object hit) {
		((SupportDispatch) manager).dispatch(r, hit);
	}

	@Override
	public Listener getListener() {
		return manager.getListener();
	}

	@Override
	public Config getConfig() {
		return config;
	}

	@Override
	public String toString() {
		return manager.toString();
	}

	@Override
	public Manager getWrapperManager() {
		return null;
	}

	@Override
	public boolean isListening() {
		return manager.isListening();
	}

	@Override
	public void openListen() throws IOException {
		manager.openListen();
	}

	@Override
	public void closeListen() throws IOException {
		manager.closeListen();
	}

	@Override
	public void broadcast(String data) throws CodecException, ClassCastException {
		((SupportWebSocketBroadcast) manager).broadcast(data);
	}

	@Override
	public void broadcast(byte[] data) throws CodecException, ClassCastException {
		((SupportWebSocketBroadcast) manager).broadcast(data);
	}

	@Override
	public WebSocketProtocol createWebSocketProtocol(String text) {
		return new WebSocketProtocol(text) {
			@Override
			public void process() throws Exception {
				SwitcherListener.getInstance().process(this);
			}
		};
	}

	@Override
	public WebSocketProtocol createWebSocketProtocol(byte[] binary) {
		return new WebSocketProtocol(binary) {
			@Override
			public void process() throws Exception {
				SwitcherListener.getInstance().process(this);
			}
		};
	}
}
