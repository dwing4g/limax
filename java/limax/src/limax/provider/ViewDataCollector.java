package limax.provider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import limax.codec.Octets;
import limax.providerendpoint.ViewVariableData;
import limax.util.Pair;

public class ViewDataCollector {
	public static class Data {
		private final static Octets NullData = new Octets(0);
		private final static Data Null = new Data(null, "") {
			@Override
			Stream<ViewVariableData> binary(byte varindex) {
				return Stream.empty();
			}

			@Override
			Stream<String> string(String prefix) {
				return Stream.empty();
			}

			@Override
			boolean isRemove() {
				return false;
			}

			@Override
			public boolean equals(Object obj) {
				return obj == this;
			}
		};

		static boolean nonNull(Data obj) {
			return obj != Null;
		}

		private final static Data Touch = new Data(NullData, "U") {
			@Override
			boolean isRemove() {
				return false;
			}
		};

		static ViewVariableData createSpecial(byte i) {
			return new ViewVariableData(i, (byte) -1, NullData, NullData);
		}

		final Octets data;
		final String text;

		public Data(Octets data, String text) {
			this.data = data;
			this.text = text;
		}

		Stream<ViewVariableData> binary(byte varindex) {
			return Stream.of(
					new ViewVariableData((byte) (isRemove() ? varindex | 0x80 : varindex), (byte) -1, data, NullData));
		}

		Stream<String> string(String prefix) {
			return Stream.of(prefix + text);
		}

		boolean isRemove() {
			return data.size() == 0;
		}

		Data asImmutableData() {
			return this;
		}

		@Override
		public boolean equals(Object other) {
			return data.equals(((Data) other).data);
		}
	}

	public static abstract class Field {
		private final byte index;
		private final Octets adata;
		private final Octets rdata;
		private final String text;

		Field(byte index, Octets adata, Octets rdata, String text) {
			this.index = index;
			this.adata = adata;
			this.rdata = rdata;
			this.text = text;
		}

		ViewVariableData binary(byte varindex) {
			return new ViewVariableData(varindex, index, adata, rdata);
		}
	}

	public static class ImmutableField extends Field {
		public ImmutableField(byte index, Octets data, String text) {
			super(index, data, Data.NullData, text);
		}
	}

	public static class MutableField extends Field {
		public MutableField(byte index, Octets adata, Octets rdata, String text) {
			super(index, adata, rdata, text);
		}
	}

	public static class MutableData extends Data {
		private final List<Field> fields;

		public MutableData(Octets data, String text, List<Field> fields) {
			super(data, text);
			this.fields = fields;
		}

		@Override
		Stream<ViewVariableData> binary(byte varindex) {
			if (fields.isEmpty())
				return Stream.empty();
			if (data.size() > fields.stream().mapToInt(e -> e.adata.size() + e.rdata.size() + 8).sum()) {
				Stream.Builder<ViewVariableData> sb = Stream.builder();
				fields.forEach(e -> sb.accept(e.binary(varindex)));
				return sb.add(createSpecial(varindex)).build();
			}
			return super.binary(varindex);
		}

		@Override
		Stream<String> string(String prefix) {
			if (fields.isEmpty())
				return Stream.empty();
			if (text.length() > fields.stream().mapToInt(e -> e.text.length()).sum() + 2)
				return Stream.of(fields.stream().map(e -> e.text)
						.collect(() -> new StringBuilder(prefix + "W"), StringBuilder::append, StringBuilder::append)
						.append(":").toString());
			return super.string(prefix);
		}

		@Override
		Data asImmutableData() {
			return new Data(data, text);
		}
	}

	private interface Collector {
		void add(Data data);

		Data get();

		void set(Data data);

		void reset();

		Stream<ViewVariableData> binary();

		Stream<String> string();
	}

	private abstract class AbstractCollector implements Collector {
		protected final byte varindex;
		private Data recent = Data.Null;

		AbstractCollector(byte varindex) {
			this.varindex = varindex;
		}

		@Override
		public void add(Data data) {
			recent = data.isRemove() ? Data.Null : data.asImmutableData();
		}

		@Override
		public Data get() {
			return recent;
		}

		@Override
		public void set(Data data) {
			recent = data;
		}

		@Override
		public void reset() {
		}

		@Override
		public Stream<ViewVariableData> binary() {
			return recent.binary(varindex);
		}

		@Override
		public Stream<String> string() {
			return recent.string(prefix[varindex]);
		}
	}

	private class ProcessCollectorSet {
		private final List<Supplier<Stream<ViewVariableData>>> blist = new ArrayList<>();
		private final List<Supplier<Stream<String>>> slist = new ArrayList<>();
		private final List<Collector> collectors = new ArrayList<>();

		Collector create(byte varindex) {
			Collector c = new AbstractCollector(varindex) {
				@Override
				public void add(Data data) {
					Data v;
					if (Data.nonNull(get()))
						v = get().equals(data) ? Data.Touch : data;
					else if (!data.isRemove())
						v = data;
					else
						v = Data.Null;
					Data value = v;
					blist.add(() -> value.binary(varindex));
					if (!value.text.isEmpty())
						slist.add(() -> value.string(prefix[varindex]));
					super.add(data);
				}
			};
			collectors.add(c);
			return c;
		}

		void reset() {
			blist.clear();
			slist.clear();
		}

		Stream<ViewVariableData> binary() {
			return blist.stream().flatMap(Supplier::get);
		}

		Stream<String> string() {
			return slist.stream().flatMap(Supplier::get);
		}
	}

	private class ImmutableCollector extends AbstractCollector {
		private Data value = Data.Null;

		public ImmutableCollector(byte varindex) {
			super(varindex);
		}

		@Override
		public void add(Data data) {
			super.add(data);
			value = data;
		}

		@Override
		public Stream<ViewVariableData> binary() {
			return value.binary(varindex);
		}

		@Override
		public Stream<String> string() {
			return value.string(prefix[varindex]);
		}
	}

	private class MutableCollector extends AbstractCollector {
		private final int cycle;
		private final Queue<Map<Byte, Field>> qifields;
		private final Queue<Map<Byte, List<Field>>> qmfields;
		private Map<Byte, Field> ifields;
		private Map<Byte, List<Field>> mfields;
		private Data value = Data.Null;
		private List<Field> cache;

		MutableCollector(byte varindex, int cycle) {
			super(varindex);
			this.cycle = cycle;
			this.qifields = new ArrayDeque<>(cycle);
			this.qmfields = new ArrayDeque<>(cycle);
			reset();
		}

		@Override
		public void add(Data data) {
			super.add(data);
			cache = null;
			if (data instanceof MutableData) {
				((MutableData) data).fields.forEach(e -> {
					if (e instanceof ImmutableField)
						ifields.put(e.index, e);
					else
						mfields.computeIfAbsent(e.index, k -> new ArrayList<>()).add(e);
				});
			} else {
				value = data;
				ifields.clear();
				mfields.clear();
				qifields.clear();
				qmfields.clear();
				qifields.add(ifields);
				qmfields.add(mfields);
			}
		}

		@Override
		public void reset() {
			cache = null;
			if (qifields.size() == cycle) {
				value = Data.Null;
				qifields.add(ifields = qifields.remove());
				qmfields.add(mfields = qmfields.remove());
				ifields.clear();
				mfields.clear();
			} else {
				qifields.add(ifields = new HashMap<>());
				qmfields.add(mfields = new HashMap<>());
			}
		}

		<R> Stream<R> cache(Function<Field, ? extends R> mapper) {
			if (cache == null)
				cache = Stream
						.concat(qifields.stream().flatMap(e -> e.entrySet().stream())
								.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue(), (a, b) -> b)).values()
								.stream(),
								qmfields.stream().flatMap(e -> e.entrySet().stream())
										.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue(),
												(a, b) -> Stream.concat(a.stream(), b.stream())
														.collect(Collectors.toList())))
										.values().stream().flatMap(e -> e.stream()))
						.collect(Collectors.toList());
			return cache.isEmpty() ? null : cache.stream().map(mapper);
		}

		@Override
		public Stream<ViewVariableData> binary() {
			Stream<ViewVariableData> r = value.binary(varindex);
			Stream<ViewVariableData> i = cache(e -> e.binary(varindex));
			return i != null ? Stream.concat(r, Stream.concat(i, Stream.of(Data.createSpecial(varindex)))) : r;
		}

		@Override
		public Stream<String> string() {
			Stream<String> r = value.string(prefix[varindex]);
			Stream<String> i = cache(e -> e.text);
			return i != null ? Stream.concat(r, Stream.of(i.collect(() -> new StringBuilder(prefix[varindex] + "W"),
					StringBuilder::append, StringBuilder::append).append(":").toString())) : r;
		}
	}

	class CollectorBundle {
		private final Map<Byte, Collector> map = new HashMap<>();
		private final ProcessCollectorSet pcs = new ProcessCollectorSet();
		private final List<Collector> scs = new ArrayList<>();

		CollectorBundle(byte[][] config, int cycle) {
			Collector c;
			for (byte varindex : config[0])
				map.put(varindex, pcs.create(varindex));
			for (byte varindex : config[1]) {
				map.put(varindex, c = new ImmutableCollector(varindex));
				scs.add(c);
			}
			for (byte varindex : config[2]) {
				map.put(varindex, c = new MutableCollector(varindex, cycle));
				scs.add(c);
			}
		}

		void add(byte varindex, Data data) {
			map.get(varindex).add(data);
		}

		Data get(byte varindex) {
			return map.get(varindex).get();
		}

		void set(byte varindex, Data data) {
			map.get(varindex).set(data);
		}

		Stream<Pair<Byte, Data>> snapshot() {
			return map.entrySet().stream().map(e -> new Pair<>(e.getKey(), e.getValue().get()));
		}

		Stream<ViewVariableData> binary() {
			return Stream.concat(pcs.binary(), scs.stream().flatMap(Collector::binary));
		}

		Stream<String> string() {
			return Stream.concat(pcs.string(), scs.stream().flatMap(Collector::string));
		}

		Stream<ViewVariableData> binary(boolean type) {
			return type ? pcs.binary() : scs.stream().flatMap(Collector::binary);
		}

		Stream<String> string(boolean type) {
			return type ? pcs.string() : scs.stream().flatMap(Collector::string);
		}

		void reset() {
			pcs.reset();
			scs.forEach(Collector::reset);
		}
	}

	private final String[] prefix;

	CollectorBundle createCollectorBundle(byte[][] config, int cycle) {
		return new CollectorBundle(config, cycle);
	}

	Stream<ViewVariableData> binary(byte varindex, Data data) {
		return data.binary(varindex);
	}

	Stream<ViewVariableData> binary(Pair<Byte, Data> e) {
		return binary(e.getKey(), e.getValue());
	}

	Stream<ViewVariableData> binary(Map.Entry<Byte, Data> e) {
		return binary(e.getKey(), e.getValue());
	}

	Stream<String> string(byte varindex, Data data) {
		return data.string(prefix[varindex]);
	}

	Stream<String> string(Pair<Byte, Data> e) {
		return string(e.getKey(), e.getValue());
	}

	Stream<String> string(Map.Entry<Byte, Data> e) {
		return string(e.getKey(), e.getValue());
	}

	ViewDataCollector(String[] prefix) {
		this.prefix = prefix;
	}
}
