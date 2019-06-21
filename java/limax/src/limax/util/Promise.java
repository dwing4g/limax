package limax.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class Promise<T> {
	private enum State {
		PENDING, RESOLVED, REJECTED
	};

	private final List<Pair<Object[], Promise<?>>> pendings = new ArrayList<>();
	private State state = State.PENDING;
	private Object value;

	@FunctionalInterface
	public interface Thenable<T> {
		void then(Consumer<T> resolve, Consumer<Object> reject) throws Throwable;
	}

	@FunctionalInterface
	public interface Then<U> {
		U apply(Object obj) throws Throwable;
	}

	@FunctionalInterface
	public interface Catch<U> {
		U apply(Object obj) throws Throwable;
	}

	@FunctionalInterface
	public interface Finally {
		void run() throws Throwable;
	}

	private Promise() {
	}

	private Promise(Thenable<T> promise) {
		try {
			promise.then(v -> _resolve(v), v -> _reject(v));
		} catch (Throwable e) {
			_reject(e);
		}
	}

	private void _resolve(Object v) {
		boolean promise;
		synchronized (pendings) {
			if (state != State.PENDING)
				return;
			if (!(promise = v instanceof Promise)) {
				state = State.RESOLVED;
				value = v;
			}
		}
		if (promise) {
			((Promise<?>) v).then(_v -> {
				_resolve(_v);
				return null;
			})._catch(e -> {
				_reject(e);
				return null;
			});
		} else {
			pendings.forEach(pair -> _resolve(pair.getKey(), pair.getValue()));
			pendings.clear();
		}
	}

	private void _reject(Object v) {
		synchronized (pendings) {
			if (state != State.PENDING)
				return;
			state = State.REJECTED;
			value = v;
		}
		pendings.forEach(pair -> _reject(pair.getKey(), pair.getValue()));
		pendings.clear();
	}

	private <U> void _finally(Object action, Promise<U> chain) {
		if (action instanceof Finally)
			try {
				((Finally) action).run();
			} catch (Throwable e) {
				chain._reject(e);
			}
	}

	@SuppressWarnings("unchecked")
	private <U> void _resolve(Object[] actions, Promise<U> chain) {
		if (actions[0] instanceof Then)
			try {
				chain._resolve(((Then<U>) actions[0]).apply(value));
			} catch (Throwable e) {
				chain._reject(e);
			}
		else {
			_finally(actions[0], chain);
			chain._resolve(value);
		}
	}

	@SuppressWarnings("unchecked")
	private <U> void _reject(Object[] actions, Promise<U> chain) {
		if (actions[0] instanceof Catch || actions.length == 2)
			try {
				chain._resolve(((Catch<U>) actions[actions.length - 1]).apply(value));
			} catch (Throwable e) {
				chain._reject(e);
			}
		else {
			_finally(actions[0], chain);
			chain._reject(value);
		}
	}

	private <U> Promise<U> add(Object... actions) {
		Promise<U> chain = new Promise<>();
		State state;
		synchronized (pendings) {
			if ((state = this.state) == State.PENDING)
				pendings.add(new Pair<>(actions, chain));
		}
		switch (state) {
		case RESOLVED:
			_resolve(actions, chain);
			break;
		case REJECTED:
			_reject(actions, chain);
		default:
		}
		return chain;
	}

	public <U> Promise<U> then(Then<U> then) {
		return add(then);
	}

	public <U> Promise<U> then(Then<U> then, Catch<U> _catch) {
		return add(then, _catch);
	}

	public <U> Promise<U> _catch(Catch<U> _catch) {
		return add(_catch);
	}

	public <U> Promise<U> _finally(Finally _finally) {
		return add(_finally);
	}

	public static <T> Promise<T> of(Thenable<T> promise) {
		return new Promise<>(promise);
	}

	private static Promise<?> all(List<Promise<?>> promises) {
		Promise<?> promise = new Promise<>();
		int size = promises.size();
		if (size == 0)
			promise._resolve(Collections.emptyList());
		else {
			List<Object> results = new ArrayList<>(promises);
			int[] count = new int[] { size };
			for (int i = 0; i < size; i++) {
				int index = i;
				promises.get(i).then(r -> {
					results.set(index, r);
					if (--count[0] == 0)
						promise._resolve(results);
					return null;
				})._catch(e -> {
					promise._reject(e);
					return null;
				});
			}
		}
		return promise;
	}

	public static Promise<?> all(Promise<?>... promises) {
		return all(Arrays.asList(promises));
	}

	public static Promise<?> all(Iterable<Promise<?>> promises) {
		if (promises instanceof List)
			return all((List<Promise<?>>) promises);
		List<Promise<?>> list = new ArrayList<>();
		for (Promise<?> p : promises)
			list.add(p);
		return all(list);
	}

	public static Promise<?> race(Promise<?>... promises) {
		return race(Arrays.asList(promises));
	}

	public static Promise<?> race(Iterable<Promise<?>> promises) {
		Promise<?> promise = new Promise<>();
		promises.forEach(p -> p.then(r -> {
			promise._resolve(r);
			return null;
		})._catch(e -> {
			promise._reject(e);
			return null;
		}));
		return promise;
	}

	public static <T> Promise<T> resolve(T obj) {
		Promise<T> promise = new Promise<>();
		promise._resolve(obj);
		return promise;
	}

	public static <T> Promise<T> resolve() {
		return resolve((T) null);
	}

	public static <T> Promise<T> resolve(Thenable<T> thenable) {
		return Promise.of(thenable);
	}

	public static <T> Promise<T> reject(Object obj) {
		Promise<T> promise = new Promise<>();
		promise._reject(obj);
		return promise;
	}

	public static <T> Promise<T> reject() {
		return reject((Object) null);
	}

	public static <T> Promise<T> reject(Thenable<T> thenable) {
		return reject((Object) thenable);
	}
}
