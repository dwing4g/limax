package limax.net;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import limax.codec.CodecException;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.util.Trace;

public final class State {

	private static class Stub {
		private final Constructor<? extends Skeleton> constructor;
		private final int maxsize;

		public Stub(Constructor<? extends Skeleton> constructor, int maxsize) {
			this.constructor = constructor;
			this.maxsize = maxsize;
		}

		public Skeleton newInstance() throws InstantiationException {
			try {
				return constructor.newInstance();
			} catch (Exception e) {
				throw new InstantiationException(e.getMessage());
			}
		}

		public void check(int size) throws SizePolicyException {
			if (size < 0 || (maxsize > 0 && size > maxsize))
				throw new SizePolicyException("checkSize of " + this + " size=" + size);
		}

		@Override
		public String toString() {
			return "Stub(" + constructor.getDeclaringClass().getName() + ")";
		}
	}

	private final Map<Integer, Stub> stubs = new HashMap<>();

	public State() {
	}

	private Stub getStub(int type) {
		Stub stub = stubs.get(type);
		if (null == stub)
			throw new RuntimeException("Protocol.Stub NOT found type=" + type);
		return stub;
	}

	public void addStub(Class<? extends Skeleton> skel, int type, int maxsize) {
		Stub stub;
		try {
			stub = new Stub(skel.getDeclaredConstructor(), maxsize);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		if (null != stubs.put(type, stub))
			throw new RuntimeException("duplicate type of " + stub + "with type = " + type);
	}

	public void merge(State s) {
		stubs.putAll(s.stubs);
	}

	public Map<Integer, Integer> getSizePolicy() {
		Map<Integer, Integer> map = new HashMap<>();
		for (Entry<Integer, Stub> e : stubs.entrySet())
			map.put(e.getKey(), e.getValue().maxsize);
		return map;
	}

	private Stub _check(int type, int size) throws InstantiationException, SizePolicyException {
		final Stub stub = getStub(type);
		if (null == stub)
			throw new InstantiationException("unknow type = " + type);
		stub.check(size);
		return stub;
	}

	public void check(int type, int size) throws InstantiationException, SizePolicyException {
		_check(type, size);
	}

	public Skeleton decode(int type, Octets data, Transport transport)
			throws InstantiationException, SizePolicyException, CodecException {
		final Skeleton p = _check(type, data.size()).newInstance();
		try {
			p._unmarshal(OctetsStream.wrap(data));
		} catch (MarshalException e) {
			throw new CodecException(e);
		}
		p.setTransport(transport);
		return p;
	}

	Collection<Skeleton> decode(final OctetsStream os, final Transport transport)
			throws InstantiationException, SizePolicyException, CodecException {
		final Collection<Skeleton> skels = new ArrayList<>();
		final Manager manager = transport.getManager();
		final UnknownProtocolHandler unknownProtocolHandler = manager instanceof SupportUnknownProtocol
				? ((SupportUnknownProtocol) manager).getUnknownProtocolHandler()
				: null;

		while (os.remain() > 0) {
			os.begin();
			try {
				final int type = os.unmarshal_int();
				final int size = Engine.checkLimitProtocolSize(type, os.unmarshal_size());
				final Stub stub = stubs.get(type);
				if (null == stub) {
					if (unknownProtocolHandler != null) {
						unknownProtocolHandler.check(type, size, transport);
						os.rollback();
						if (size > os.remain())
							break; // not enough
						os.begin();
						os.position(os.position() + 4);
						final Octets data = os.unmarshal_Octets();
						((SupportDispatch) manager).dispatch(new Runnable() {
							@Override
							public void run() {
								try {
									unknownProtocolHandler.dispatch(type, data, transport);
								} catch (Exception e) {
									if (Trace.isErrorEnabled())
										Trace.error("dispatch unknown Protocol type = " + type + " size = " + size, e);
									manager.close(transport);
								}
							}
						}, transport);
					} else {
						throw new CodecException("unknown protocol (" + type + ", " + size + ")");
					}
				} else {
					stub.check(size);
					if (size > os.remain()) {
						os.rollback();
						break; // not enough
					}
					final int startpos = os.position();
					final Skeleton p = stub.newInstance();
					try {
						p._unmarshal(os);
					} catch (MarshalException e) {
						throw new CodecException("(" + type + ", " + size + ")", e);
					}
					p.setTransport(transport);
					skels.add(p);
					if ((os.position() - startpos) != size)
						throw new CodecException("(" + type + ", " + size + ")=" + (os.position() - startpos));
				}
				os.commit();
			} catch (MarshalException e) {
				os.rollback();
				break;
			}
		}
		return skels;
	}
}