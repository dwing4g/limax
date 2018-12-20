package limax.zdb;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import limax.codec.Marshal;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;

public abstract class XBean implements Marshal {
	private static final AtomicLong objid = new AtomicLong();

	final Long _objid_ = objid.incrementAndGet();
	private XBean _parent_;
	private String _varname_;

	protected XBean(XBean parent, String varname) {
		_parent_ = parent;
		_varname_ = varname;
	}

	final void link(XBean parent, String varname, boolean log) {
		if (null != parent) {
			if (null != _parent_) // parent != null && _parent_ != null
				throw new XManagedError("ambiguously managed");
			if (parent == this)
				throw new XManagedError("loop managed");
		} else {
			if (null == _parent_) // parent == null && _parent_ == null
				throw new XManagedError("not managed");
		}
		if (log)
			Transaction.currentSavepoint().addIfAbsent(new LogKey(this, "_parent_"), new Log() {
				private final XBean parent = _parent_;
				private final String varname = _varname_;

				@Override
				public void commit() {
				}

				@Override
				public void rollback() {
					_parent_ = parent;
					_varname_ = varname;
				}
			});
		_parent_ = parent;
		_varname_ = varname;
	}

	void notify(LogNotify notify) {
		if (_parent_ != null)
			_parent_.notify(notify.push(new LogKey(_parent_, _varname_)));
	}

	private TRecord<?, ?> getRecord() {
		XBean self = this;
		do {
			if (self instanceof TRecord<?, ?>)
				return (TRecord<?, ?>) self;
			self = self._parent_;
		} while (self != null);
		return null;
	}

	private final static Runnable donothing = () -> {
	};

	protected final Runnable verifyStandaloneOrLockHeld(String methodName, boolean readonly) {
		if (!Zdb.meta().isZdbVerify())
			return donothing;
		Transaction current = Transaction.current();
		if (current == null)
			return donothing;
		TRecord<?, ?> record = getRecord();
		if (record == null)
			return donothing;
		switch (current.getLockeyHolderType(record.getLockey())) {
		case WRITE:
			return donothing;
		case READ:
			if (readonly)
				return () -> {
					throw new XLockLackedError(getClass().getName() + "." + methodName);
				};
		default:
		}
		throw new XLockLackedError(getClass().getName() + "." + methodName);
	}

	public static class XLockLackedError extends XError {
		private static final long serialVersionUID = -2377572699783238493L;

		public XLockLackedError(String message) {
			super(message);
		}
	}

	public static class XManagedError extends XError {
		static final long serialVersionUID = 7269011645942640931L;

		XManagedError(String message) {
			super(message);
		}
	}

	public static class DynamicData implements Marshal {
		private Map<Integer, byte[]> entries = new LinkedHashMap<>();

		public DynamicData() {
		}

		public DynamicData(OctetsStream os) throws MarshalException {
			unmarshal(os);
		}

		@Override
		public OctetsStream marshal(OctetsStream os) {
			os.marshal_size(entries.size());
			entries.forEach((serial, data) -> os.marshal_size(serial).marshal(data));
			return os;
		}

		@Override
		public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
			for (int count = os.unmarshal_size(); count > 0; count--)
				entries.put(os.unmarshal_size(), os.unmarshal_bytes());
			return os;
		}

		public Octets get(int serial) {
			byte[] value = entries.get(serial);
			return value != null ? Octets.wrap(value) : null;
		}

		public int size() {
			return entries.size();
		}

		public Set<Integer> serials() {
			return entries.keySet();
		}

		public void ForEach(BiConsumer<Integer, byte[]> consumer) {
			entries.forEach(consumer);
		}
	}

	@Override
	public String toString() {
		return getClass().getName();
	}
}
