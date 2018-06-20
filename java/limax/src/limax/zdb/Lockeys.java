package limax.zdb;

import java.lang.ref.WeakReference;

final class Lockeys {
	private final static int bucketShift = Integer.getInteger("limax.zdb.Lockeys.bucketShift", 10);
	private final static Lockeys instance = new Lockeys();
	private final Bucket buckets[] = new Bucket[1 << bucketShift];

	private Lockeys() {
		for (int i = 0; i < buckets.length; ++i)
			buckets[i] = new Bucket();
	}

	private static class Bucket {
		private static class Entry extends WeakReference<Lockey> {
			private Entry next;

			Entry(Lockey referent, Entry next) {
				super(referent);
				this.next = next;
			}
		}

		private final Entry head = new Entry(null, null);

		synchronized Lockey get(Lockey key) {
			Entry e = head;
			while (e.next != null) {
				Lockey _key = e.next.get();
				if (_key == null)
					e.next = e.next.next;
				else if (_key.equals(key))
					return _key;
				else
					e = e.next;
			}
			e.next = new Entry(key, null);
			return key.alloc();
		}
	}

	private Lockey get(Lockey lockey) {
		Transaction current = Transaction.current();
		if (current != null) {
			Lockey lockey1 = current.get(lockey);
			if (lockey1 != null)
				return lockey1;
		}
		int h = lockey.hashCode();
		h += (h << 15) ^ 0xffffcd7d;
		h ^= (h >>> 10);
		h += (h << 3);
		h ^= (h >>> 6);
		h += (h << 2) + (h << 14);
		h = (h ^ (h >>> 16)) >>> (32 - bucketShift);
		return buckets[h].get(lockey);
	}

	static Lockey getLockey(int lockId, Object key) {
		return instance.get(new Lockey(lockId, key));
	}
}
