package limax.auany;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FileBundle {
	private static Path TRANSACTION_HOME;
	private static final Map<Path, File> files = new HashMap<>();
	private static final Queue<Transaction> pool = new ConcurrentLinkedQueue<>();
	private static final ThreadLocal<Transaction> transaction = new ThreadLocal<>();

	public static class File implements AutoCloseable {
		private final Path path;
		private final FileChannel fc;

		File(Path path) throws IOException {
			this.path = path;
			this.fc = FileChannel.open(path,
					EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE));
		}

		public void close() throws IOException {
			synchronized (files) {
				files.remove(path);
			}
			fc.close();
		}

		private Path getPath() {
			return path;
		}

		public int read(ByteBuffer dst, long position) throws IOException {
			return fc.read(dst, position);
		}

		public int write(ByteBuffer src, long position) throws IOException {
			Transaction t = transaction.get();
			return t == null ? _write(src, position) : t.prepare(this, src, position);
		}

		private int _write(ByteBuffer src, long position) throws IOException {
			return fc.write(src, position);
		}

		public void force(boolean metaData) throws IOException {
			fc.force(metaData);
		}
	}

	private static class Transaction {
		private FileChannel logfc;
		private final List<Record> records = new ArrayList<>();
		private Throwable exception = null;
		private int ref = 0;

		private class Record {
			private final FileBundle.File file;
			private final ByteBuffer data;
			private final long position;

			Record(FileBundle.File file, ByteBuffer src, int size, long position) throws IOException {
				byte[] fn = file.getPath().toString().getBytes();
				this.file = file;
				this.position = position;
				data = ByteBuffer.allocate(4 + 4 + fn.length + 8 + size);
				data.putInt(data.capacity()).putInt(fn.length).put(fn).putLong(position).put(src).flip();
				logfc.write(data);
				data.position(data.capacity() - size);
			}

			void commit() throws IOException {
				file._write(data, position);
				file.force(false);
			}
		}

		Transaction() {
		}

		Transaction(Path path) throws IOException {
			this.logfc = FileChannel.open(path,
					EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE));
			ByteBuffer ib = ByteBuffer.allocate(4);
			if (logfc.read(ib) != 4)
				return;
			ib.flip();
			if (ib.getInt() == 0)
				return;
			for (byte[] fn; true;) {
				ib.clear();
				if (logfc.read(ib) != 4)
					return;
				ib.flip();
				ByteBuffer bb = ByteBuffer.allocate(ib.getInt() - 4);
				logfc.read(bb);
				bb.flip();
				bb.get(fn = new byte[bb.getInt()]);
				FileBundle.open(Paths.get(new String(fn))).write(bb, bb.getLong());
			}
		}

		int prepare(FileBundle.File file, ByteBuffer src, long position) throws IOException {
			try {
				Files.createTempFile("a", null);
				if (logfc == null)
					logfc = FileChannel.open(Files.createTempFile(TRANSACTION_HOME, "trn.", ""),
							EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE));
				if (records.isEmpty())
					logfc.truncate(0).position(4);
				int size = src.limit() - src.position();
				records.add(new Record(file, src, size, position));
				return size;
			} catch (Throwable e) {
				throw (exception = e) instanceof IOException ? (IOException) e : new IOException(e);
			}
		}

		void commit() throws IOException {
			if (records.isEmpty())
				return;
			if (exception == null) {
				ByteBuffer head = ByteBuffer.allocate(4).putInt(-1);
				head.flip();
				logfc.force(false);
				logfc.write(head, 0);
				logfc.force(false);
				for (Record r : records)
					r.commit();
			} else {
				exception = null;
			}
			records.clear();
			logfc.truncate(0);
		}

		void close() throws IOException {
			logfc.close();
		}
	}

	private FileBundle() {
	}

	public static File open(Path path) throws IOException {
		Path p = path.toAbsolutePath();
		synchronized (files) {
			File file = files.get(p);
			if (file == null)
				files.put(p, file = new File(p));
			return file;
		}
	}

	private static void close() throws IOException {
		IOException ex = new IOException("FileBundle.close");
		synchronized (files) {
			files.values().forEach(file -> {
				try {
					file.fc.close();
				} catch (IOException e) {
					ex.addSuppressed(e);
				}
			});
			files.clear();
		}
		if (ex.getSuppressed().length > 0)
			throw ex;
	}

	public static void initialize(Path home) throws IOException {
		Files.createDirectories(TRANSACTION_HOME = home);
		try (Stream<Path> stream = Files.list(home)) {
			for (Path path : stream.filter(path -> path.getFileName().toString().startsWith("trn."))
					.collect(Collectors.toList())) {
				if (Files.size(path) == 0) {
					Files.deleteIfExists(path);
				} else {
					pool.offer(new Transaction(path));
				}
			}
		}
	}

	public static void unInitialize() {
		for (Transaction t : pool)
			try {
				t.close();
			} catch (IOException e) {
			}
		pool.clear();
		try {
			close();
		} catch (IOException e) {
		}
	}

	public static void begin() {
		Transaction t = transaction.get();
		if (t == null) {
			t = pool.poll();
			if (t == null)
				t = new Transaction();
			transaction.set(t);
		}
		t.ref++;
	}

	public static void end() throws IOException {
		Transaction t = transaction.get();
		if (t == null || --t.ref > 0)
			return;
		transaction.set(null);
		try {
			t.commit();
		} finally {
			pool.offer(t);
		}
	}
}
