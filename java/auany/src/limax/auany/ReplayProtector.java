package limax.auany;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ReplayProtector {
	private final static int RECORD_SIZE = 16;
	private final int POOL_SIZE;
	private final int POOL_MASK;
	private final FileBundle.File[] files;
	private final ByteBuffer[] buffers;
	private final Map<Long, Record> map[];
	private final Queue<Record> queue[];
	private final BitSet[] slots;

	private class Record implements Comparable<Record> {
		private final long serial;
		private final long deadline;

		private int record;

		Record(long serial, long deadline) {
			this.serial = serial;
			this.deadline = deadline;
		}

		Record(ByteBuffer bb) {
			this.serial = bb.getLong();
			this.deadline = bb.getLong();
		}

		void save() throws IOException {
			int i = (int) (serial & POOL_MASK);
			buffers[i].putLong(serial).putLong(deadline).flip();
			files[i].write(buffers[i], record * RECORD_SIZE);
			buffers[i].clear();
		}

		@Override
		public int compareTo(Record o) {
			long t = deadline - o.deadline;
			return t > 0 ? 1 : t == 0 ? 0 : -1;
		}
	}

	public boolean accept(long serial, long duration) throws IOException {
		long now = System.currentTimeMillis();
		int i = (int) (serial & POOL_MASK);
		synchronized (map[i]) {
			while (true) {
				Record r = queue[i].peek();
				if (r == null || r.deadline > now)
					break;
				map[i].remove(r.serial);
				queue[i].remove();
				slots[i].clear();
			}
			if (map[i].containsKey(serial))
				return false;
			Record r = new Record(serial, now + duration);
			r.record = slots[i].nextClearBit(0);
			r.save();
			map[i].put(serial, r);
			queue[i].add(r);
			slots[i].set(r.record);
			return true;
		}
	}

	@SuppressWarnings("unchecked")
	public ReplayProtector(Path persistPath, int concurrencyBits) throws IOException {
		this.POOL_SIZE = 1 << concurrencyBits;
		this.POOL_MASK = this.POOL_SIZE - 1;
		this.files = new FileBundle.File[this.POOL_SIZE];
		this.buffers = new ByteBuffer[this.POOL_SIZE];
		this.slots = new BitSet[this.POOL_SIZE];
		this.map = new Map[this.POOL_SIZE];
		this.queue = new Queue[this.POOL_SIZE];
		List<Path> pathReplayProtectors;
		Files.createDirectories(persistPath);
		try (Stream<Path> stream = Files.list(persistPath)) {
			pathReplayProtectors = stream.filter(p -> p.getFileName().toString().startsWith("rp."))
					.collect(Collectors.toList());
		}
		Path pathCommit = persistPath.resolve("commit");
		if (!Files.exists(pathCommit)) {
			Path pathPrepare = persistPath.resolve("prepare");
			try (FileChannel fcPrepare = FileChannel.open(pathPrepare, EnumSet.of(StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
				for (Path p : pathReplayProtectors)
					try (FileChannel fc = FileChannel.open(p, StandardOpenOption.READ)) {
						fc.transferTo(0, fc.size(), fcPrepare);
					}
				fcPrepare.force(false);
			}
			Files.move(pathPrepare, pathCommit, StandardCopyOption.ATOMIC_MOVE);
		}
		for (Path p : pathReplayProtectors)
			Files.deleteIfExists(p);
		for (int i = 0; i < POOL_SIZE; i++) {
			map[i] = new HashMap<>();
			queue[i] = new PriorityQueue<>();
		}
		long now = System.currentTimeMillis();
		ByteBuffer bb = ByteBuffer.allocateDirect(RECORD_SIZE * 1024);
		try (FileChannel fcCommit = FileChannel.open(pathCommit, StandardOpenOption.READ)) {
			for (; fcCommit.read(bb) > 0; bb.clear())
				for (bb.flip(); bb.hasRemaining();) {
					Record r = new Record(bb);
					if (r.deadline > now) {
						int i = (int) (r.serial & POOL_MASK);
						map[i].put(r.serial, r);
						queue[i].add(r);
					}
				}
		}
		for (int i = 0; i < POOL_SIZE; i++) {
			buffers[i] = ByteBuffer.allocateDirect(RECORD_SIZE);
			files[i] = FileBundle.open(persistPath.resolve("rp." + i));
			int record = 0;
			for (Record r : map[i].values()) {
				r.record = record++;
				r.save();
			}
			files[i].force(false);
			slots[i] = new BitSet(record);
			slots[i].set(0, record);
		}
		Files.deleteIfExists(pathCommit);
	}
}
