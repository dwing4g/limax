package limax.zdb;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.util.Trace;

class Duration implements AutoCloseable {
	private final Path home;
	private FileChannel fc;
	private Path path;
	private long position;
	private final Queue<Long> window = new ArrayDeque<>();
	private final Timing timing = new Timing();

	private static class Timing {
		private final AtomicLong timestamp = new AtomicLong();

		long update(long want) {
			return timestamp.updateAndGet(value -> value < want ? want : value + 1);
		}

		long update() {
			return update(System.currentTimeMillis());
		}
	}

	class Record {
		private final static byte OP_REPLACE = 0;
		private final static byte OP_REMOVE = 1;
		private final static byte OP_AUTOKEY = 2;
		private final OctetsStream data = new OctetsStream().marshal(0);
		private final Map<String, Long> autoKeys = new HashMap<>();

		void replace(String table, Octets key, Octets value) {
			data.marshal(table).marshal(OP_REPLACE).marshal(key).marshal(value);
		}

		void remove(String table, Octets key) {
			data.marshal(table).marshal(OP_REMOVE).marshal(key);
		}

		void updateAutoKey(String table, long value) {
			autoKeys.put(table, value);
		}

		void commit() {
			autoKeys.forEach((key, value) -> data.marshal(key).marshal(OP_AUTOKEY).marshal(value));
			if (data.size() == 4)
				return;
			try {
				long from;
				synchronized (window) {
					window.add(from = position);
					position += data.size();
				}
				ByteBuffer bb = data.getByteBuffer().putInt(0, data.size() - 4);
				fc.write(bb, from);
				bb.put(0, (byte) (bb.get(0) | 0x80)).position(0).limit(1);
				fc.write(bb, from);
				synchronized (window) {
					while (from != window.peek())
						try {
							window.wait();
						} catch (InterruptedException e) {
						}
					window.remove();
					window.notifyAll();
				}
			} catch (Throwable t) {
				Trace.fatal("Duration.Record.commit", t);
				Runtime.getRuntime().halt(-1);
			}
		}
	}

	Duration(Path home, Tables tables, Runnable checkpoint) throws IOException, MarshalException {
		this.home = home;
		List<String> files;
		try (Stream<Path> stream = Files.list(home)) {
			files = stream.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
		}
		byte[] buffer = new byte[4096];
		for (String file : files) {
			try (DataInputStream dis = new DataInputStream(
					new BufferedInputStream(new FileInputStream(home.resolve(file).toFile())))) {
				while (true) {
					int size = dis.readInt();
					if (size > 0)
						break;
					size &= Integer.MAX_VALUE;
					if (size > buffer.length)
						buffer = new byte[size];
					dis.readFully(buffer, 0, size);
					OctetsStream os = OctetsStream.wrap(Octets.wrap(buffer, size));
					while (!os.eos()) {
						TTable<?, ?> ttable = tables.getTable(os.unmarshal_String());
						switch (os.unmarshal_byte()) {
						case Record.OP_REPLACE:
							ttable.getStorage().getEngine().replace(Octets.wrap(os.unmarshal_bytes()),
									Octets.wrap(os.unmarshal_bytes()));
							break;
						case Record.OP_REMOVE:
							ttable.getStorage().getEngine().remove(Octets.wrap(os.unmarshal_bytes()));
							break;
						case Record.OP_AUTOKEY:
							ttable.recoverKey(os.unmarshal_long());
						}
					}
				}
			} catch (EOFException e) {
			}
		}
		checkpoint.run();
		for (String file : files)
			Files.deleteIfExists(home.resolve(file));
		checkpoint();
	}

	Runnable checkpoint() {
		try {
			close();
			Path prev = path;
			path = home.resolve(String.format("%016x", timing.update()));
			fc = FileChannel.open(path,
					EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.DSYNC));
			position = 0;
			return () -> {
				if (prev != null)
					try {
						Files.deleteIfExists(prev);
					} catch (Exception e) {
					}
			};
		} catch (Throwable t) {
			Trace.fatal("Duration.rotate", t);
			Runtime.getRuntime().halt(-1);
		}
		return null;
	}

	@Override
	public void close() throws IOException {
		if (fc != null) {
			fc.close();
		}
	}

	Record alloc() {
		return new Record();
	}
}
