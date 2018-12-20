package limax.auany.local;

import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import limax.util.Dispatcher;
import limax.util.Trace;

class Ldap implements Authenticate {
	private final ScheduledExecutorService scheduler;
	private final Dispatcher dispatcher;
	private final String url;
	private final LdapName baseDN;
	private final String key;
	private final Queue<RequestContext> contexts = new ConcurrentLinkedQueue<>();
	private final long timeout;
	private boolean stopped = false;

	private class RequestContext {
		private volatile LdapContext ctx;
		private volatile Future<?> future;
		private volatile AtomicReference<Consumer<Result>> ref = new AtomicReference<>();

		void response(Result r) {
			Consumer<Result> response = ref.getAndSet(null);
			if (response != null) {
				response.accept(r);
				future.cancel(false);
			}
		}

		boolean requestBind(String username, String password, Consumer<Result> response) {
			ref.set(response);
			future = scheduler.schedule(() -> response(Result.Timeout), timeout, TimeUnit.MILLISECONDS);
			try {
				LdapName dn = (LdapName) baseDN.clone();
				dn.add(new Rdn(key, username));
				if (ctx == null) {
					Properties prop = new Properties();
					prop.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
					prop.put(Context.PROVIDER_URL, url);
					prop.put(Context.SECURITY_AUTHENTICATION, "simple");
					prop.put(Context.SECURITY_PRINCIPAL, dn.toString());
					prop.put(Context.SECURITY_CREDENTIALS, password);
					ctx = new InitialLdapContext(prop, null);
				} else {
					ctx.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
					ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, dn.toString());
					ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, password);
					ctx.reconnect(null);
				}
				response(Result.Accept);
			} catch (AuthenticationException e) {
				response(Result.Reject);
			} catch (Throwable t) {
				if (Trace.isDebugEnabled())
					Trace.debug("ldap authentication", t);
				close();
				return false;
			}
			return true;
		}

		void close() {
			response(Result.Fail);
			try {
				ctx.close();
			} catch (Exception e) {
			}
		}
	}

	public Ldap(ScheduledExecutorService scheduler, String url, String baseDN, String key, long timeout)
			throws Exception {
		this.scheduler = scheduler;
		this.dispatcher = new Dispatcher(scheduler);
		this.url = url;
		this.baseDN = new LdapName(baseDN);
		this.key = key;
		this.timeout = timeout;
	}

	@Override
	public synchronized void access(String username, String password, Consumer<Result> response) {
		if (stopped) {
			response.accept(Result.Fail);
			return;
		}
		dispatcher.execute(() -> {
			RequestContext r = contexts.poll();
			if (r == null)
				r = new RequestContext();
			if (r.requestBind(username, password, response))
				contexts.offer(r);
		}, null);
	}

	@Override
	public synchronized void stop() {
		if (stopped)
			return;
		stopped = true;
		dispatcher.await();
		contexts.forEach(RequestContext::close);
	}
}
