package limax.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

public final class LockEnvironment {
	public interface DetectableLock {
		void lock() throws InterruptedException;

		boolean tryLock();

		void unlock();
	}

	public interface StatusMXBean {
		String dump();
	}

	private static class Status implements StatusMXBean {
		private static List<LockEnvironment> list = new ArrayList<>();

		void add(LockEnvironment env) {
			list.add(env);
		}

		@Override
		public String dump() {
			StringBuilder sb = new StringBuilder();
			list.forEach(env -> {
				sb.append(env).append("\n");
				Map<Thread, Pair<Object, Map<Object, Integer>>> stat = new TreeMap<>(
						Comparator.comparingLong(Thread::getId));
				env.waiting.forEach((t, o) -> stat.put(t, new Pair<>(o, null)));
				env.holding.forEach((t, m) -> stat.compute(t, (k, v) -> new Pair<>(v == null ? null : v.getKey(), m)));
				stat.forEach((t, v) -> {
					sb.append("\t").append('"').append(t.getName()).append('"').append(" id=").append(t.getId())
							.append("\n");
					for (StackTraceElement e1 : t.getStackTrace())
						sb.append("\t\tat ").append(e1).append("\n");
					if (v.getKey() != null) {
						sb.append("\tWaiting:\n");
						sb.append("\t\t").append(v.getKey()).append('\n');
					}
					if (v.getValue() != null) {
						sb.append("\tHolding:\n");
						v.getValue()
								.forEach((o, c) -> sb.append("\t\t").append(o).append("[ref=").append(c).append("]\n"));
					}
				});
			});
			return sb.toString();
		}
	}

	private final static Object lock = new Object();
	private static ScheduledExecutorService scheduler;
	private static Status status;
	private final Map<Thread, Object> waiting = new ConcurrentHashMap<>();
	private final Map<Thread, Map<Object, Integer>> holding = new ConcurrentHashMap<>();
	private final AtomicLong deadlockCount = new AtomicLong();

	public LockEnvironment(LongSupplier detectDelay) {
		synchronized (lock) {
			if (scheduler == null) {
				scheduler = ConcurrentEnvironment.getInstance().newScheduledThreadPool("LockEnvironmentScheduler", 1,
						true);
				MBeans.register(MBeans.root(), status = new Status(), "limax.util:type=LockEnvironment");
			}
			status.add(this);
		}
		schedule(detectDelay);
	}

	public long getDeadlockCount() {
		return deadlockCount.get();
	}

	private void schedule(LongSupplier detectDelay) {
		scheduler.schedule(() -> {
			new DeadlockDetector(waiting, holding).execute().forEach((t, m) -> {
				t.interrupt();
				deadlockCount.incrementAndGet();
				Trace.fatal(m);
			});
			schedule(detectDelay);
		}, detectDelay.getAsLong(), TimeUnit.MILLISECONDS);
	}

	private void addHolder(Object o) {
		holding.compute(Thread.currentThread(), (k, v) -> {
			if (v == null)
				v = new ConcurrentHashMap<>();
			v.compute(o, (k0, v0) -> v0 == null ? 1 : v0 + 1);
			return v;
		});
	}

	private void removeHolder(Object o) {
		holding.compute(Thread.currentThread(), (k, v) -> {
			if (v == null)
				return null;
			v.compute(o, (k0, v0) -> v0 == null || v0 == 1 ? null : v0 - 1);
			return v.isEmpty() ? null : v;
		});
	}

	public DetectableLock create(Lock lock, Object ref) {
		return new DetectableLock() {
			@Override
			public void lock() throws InterruptedException {
				waiting.put(Thread.currentThread(), ref);
				try {
					lock.lockInterruptibly();
					addHolder(ref);
				} finally {
					waiting.remove(Thread.currentThread());
				}
			}

			@Override
			public boolean tryLock() {
				boolean r = lock.tryLock();
				if (r)
					addHolder(ref);
				return r;
			}

			@Override
			public void unlock() {
				lock.unlock();
				removeHolder(ref);
			}
		};
	}

	private static class DeadlockDetector {
		private final Map<Object, Set<Thread>> waiting;
		private final Map<Thread, List<Object>> holding;
		private final Set<Thread> listing;

		DeadlockDetector(Map<Thread, Object> waiting, Map<Thread, Map<Object, Integer>> holding) {
			this.waiting = waiting.entrySet().stream().filter(e -> e.getKey().getState() == Thread.State.WAITING)
					.collect(Collectors.groupingBy(e -> e.getValue(),
							Collectors.mapping(e -> e.getKey(), Collectors.toSet())));
			this.holding = holding.entrySet().stream()
					.collect(Collectors.toMap(e -> e.getKey(), e -> new ArrayList<>(e.getValue().keySet())));
			this.listing = this.holding.entrySet().stream()
					.filter(e -> waiting.containsKey(e.getKey()) && e.getKey().getState() == Thread.State.WAITING)
					.sorted((a, b) -> a.getValue().size() - b.getValue().size()).map(e -> e.getKey())
					.collect(Collectors.toCollection(LinkedHashSet<Thread>::new));
		}

		private Collection<Pair<Thread, Object>> test(Thread victim) {
			Map<Thread, Pair<Thread, Object>> path = new HashMap<>();
			Collection<Thread> tset0 = Arrays.asList(victim);
			do {
				Collection<Thread> tmp = tset0;
				tset0 = null;
				for (Thread t0 : tmp) {
					Collection<Object> lset = holding.get(t0);
					if (lset != null)
						for (Object l : lset) {
							Collection<Thread> tset1 = waiting.get(l);
							if (tset1 != null)
								for (Thread t1 : tset1)
									if (path.putIfAbsent(t1, new Pair<>(t0, l)) == null) {
										if (t1 == victim) {
											Deque<Pair<Thread, Object>> list = new ArrayDeque<>();
											do {
												Pair<Thread, Object> it = path.get(t1);
												list.addFirst(it);
												t1 = it.getKey();
											} while (t1 != victim);
											return list;
										}
										if (tset0 == null)
											tset0 = new ArrayList<>();
										tset0.add(t1);
									}
						}
				}
			} while (tset0 != null);
			return null;
		}

		Map<Thread, String> execute() {
			Map<Thread, String> map = new IdentityHashMap<>();
			while (!listing.isEmpty()) {
				Iterator<Thread> it = listing.iterator();
				Thread victim = it.next();
				Collection<Pair<Thread, Object>> r = test(victim);
				if (r == null)
					it.remove();
				else {
					StringBuilder sb = new StringBuilder("Deadlock detected, cycle-size=").append(r.size())
							.append("\n");
					r.forEach(e -> {
						Thread t = e.getKey();
						holding.remove(t);
						listing.remove(t);
						sb.append('"').append(t.getName()).append('"').append(" id=").append(t.getId()).append(' ')
								.append(" owns=").append(e.getValue()).append("\n");
						for (StackTraceElement e1 : t.getStackTrace())
							sb.append("\tat ").append(e1).append("\n");
					});
					map.put(victim, sb.toString());
				}
			}
			return map;
		}
	}
}
