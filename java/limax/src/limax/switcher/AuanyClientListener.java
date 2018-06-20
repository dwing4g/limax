package limax.switcher;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import limax.net.ClientListener;
import limax.net.Config;
import limax.net.Engine;
import limax.net.Manager;
import limax.net.State;
import limax.net.StateTransport;
import limax.net.Transport;
import limax.switcher.switcherauany.Kick;
import limax.util.Trace;

public final class AuanyClientListener implements ClientListener {
	private static final AuanyClientListener instance = new AuanyClientListener();

	public static AuanyClientListener getInstance() {
		return instance;
	}

	private AuanyClientListener() {
	}

	private volatile Transport transport;
	private volatile Future<?> keepAliveFuture;

	Transport getTransport() {
		return transport;
	}

	@Override
	public void onTransportAdded(Transport transport) {
		if (Trace.isInfoEnabled())
			Trace.info("AuanyClientManager addTransport " + transport);
		State newstate = new State();
		newstate.merge(limax.switcher.states.AuanyClient.AuanyClient);
		newstate.merge(limax.switcher.states.ProviderServer.ForProvider);
		((StateTransport) transport).setState(newstate);
		ProviderListener.getInstance().auanyOnline(transport);
		this.transport = transport;
	}

	@Override
	public void onTransportRemoved(Transport transport) {
		if (Trace.isInfoEnabled())
			Trace.info("AuanyClientManager removeTransport", transport.getCloseReason());
		this.transport = null;
		ProviderListener.getInstance().doUnBind(transport);
	}

	@Override
	public void onAbort(Transport transport) {
	}

	@Override
	public void onManagerInitialized(Manager manager, Config config) {
		long keepAliveTimeout = Engine.getIntranetKeepAliveTimeout();
		if (keepAliveTimeout > 0)
			keepAliveFuture = Engine.getProtocolScheduler().scheduleWithFixedDelay(() -> {
				try {
					new limax.switcher.switcherprovider.KeepAlive(keepAliveTimeout).send(transport);
				} catch (Exception e) {
				}
			}, 0, keepAliveTimeout / 2, TimeUnit.MILLISECONDS);
	}

	@Override
	public void onManagerUninitialized(Manager manager) {
		if (keepAliveFuture != null)
			keepAliveFuture.cancel(true);
	}

	public void process(Kick p) {
		Trace.fatal(p.message);
		System.exit(0);
	}
}
