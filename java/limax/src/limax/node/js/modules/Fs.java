package limax.node.js.modules;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.script.Invocable;

import limax.node.js.Buffer;
import limax.node.js.EventLoop;
import limax.node.js.EventLoop.EventObject;
import limax.node.js.Module;
import limax.node.js.modules.Timer.Timeout;

public final class Fs implements Module {
	private final EventLoop eventLoop;
	private final Invocable invocable;
	private final AtomicInteger fdGenerator = new AtomicInteger();
	private final Map<Integer, FileChannel> channelMap = new ConcurrentHashMap<>();
	private final Map<Integer, Path> pathMap = new ConcurrentHashMap<>();
	private final Map<Path, Map<Object, List<Timeout>>> watchFileMap = new HashMap<>();

	@FunctionalInterface
	private interface ConsumerWithException<T> {
		void accept(T path) throws Exception;
	}

	private Path resolvePath(String path) {
		return Process.currentWorkingDirectory().resolve(path).toAbsolutePath().normalize();
	}

	private Path fd2Path(Object fd) {
		Path path = pathMap.get(fd);
		if (path != null)
			return path;
		throw new IllegalArgumentException("fd not found, fd = " + fd);
	}

	private void fd2Path(Object fd, ConsumerWithException<Path> consmuer) throws Exception {
		consmuer.accept(fd2Path(fd));
	}

	private FileChannel fd2fc(Object fd) {
		FileChannel fc = channelMap.get(fd);
		if (fc != null)
			return fc;
		throw new IllegalArgumentException("fd not found, fd = " + fd);
	}

	private void fd2fc(Object fd, ConsumerWithException<FileChannel> consumer) throws Exception {
		consumer.accept(fd2fc(fd));
	}

	private Object dateMethod;

	public void setDateMethod(Object dateMethod) {
		this.dateMethod = dateMethod;
	}

	public static class Constants {
		public final int R_OK = 4;
		public final int W_OK = 2;
		public final int X_OK = 1;
		public final int F_OK = 0;

		private boolean R_OK(int mode) {
			return (mode & R_OK) != 0;
		}

		private boolean W_OK(int mode) {
			return (mode & R_OK) != 0;
		}

		private boolean X_OK(int mode) {
			return (mode & R_OK) != 0;
		}
	}

	private static class FSWatcherService {
		private final static Map<FileSystem, WatchService> watchers = new HashMap<>();
		private final static Map<WatchKey, Consumer<EventLoop.EventAction>> actions = new HashMap<>();

		synchronized static WatchKey register(Path path, Consumer<EventLoop.EventAction> consumer) throws Exception {
			FileSystem fs = path.getFileSystem();
			WatchService ws = watchers.get(fs);
			if (ws == null) {
				watchers.put(fs, ws = fs.newWatchService());
				WatchService watchService = ws;
				Thread thread = new Thread(() -> {
					while (true) {
						try {
							WatchKey key = watchService.take();
							Consumer<EventLoop.EventAction> action;
							synchronized (FSWatcherService.class) {
								action = actions.get(key);
							}
							for (WatchEvent<?> event : key.pollEvents()) {
								WatchEvent.Kind<?> kind = event.kind();
								String changeType;
								if (kind == StandardWatchEventKinds.ENTRY_CREATE
										|| kind == StandardWatchEventKinds.ENTRY_DELETE) {
									changeType = "rename";
								} else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
									changeType = "change";
								} else {
									continue;
								}
								String filename = ((Path) event.context()).toAbsolutePath().toString();
								action.accept(r -> {
									r.add(changeType);
									r.add(filename);
								});
							}
							key.reset();
						} catch (Exception e) {
						}
					}
				}, "FSWatcher-" + fs);
				thread.setDaemon(true);
				thread.start();
			}
			WatchKey key = path.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
					StandardWatchEventKinds.ENTRY_MODIFY);
			actions.put(key, consumer);
			return key;
		}

		synchronized static void unregister(WatchKey key) {
			actions.remove(key);
			key.cancel();
		}
	}

	public class FSWatcher {
		private EventObject evo;
		private WatchKey key;

		private FSWatcher(Path path, boolean persistent, Object callback) {
			try {
				this.key = FSWatcherService.register(path, r -> eventLoop.execute(callback, r));
				this.evo = persistent ? eventLoop.createEventObject() : null;
			} catch (Exception e) {
				eventLoop.createCallback(callback).call(e);
			}
		}

		public void close() {
			if (key == null)
				return;
			FSWatcherService.unregister(key);
			key = null;
			if (evo != null)
				evo.queue();
		}
	}

	private static LinkOption[] getLinkOption(boolean follow) {
		return follow ? new LinkOption[0] : new LinkOption[] { LinkOption.NOFOLLOW_LINKS };
	}

	public class Stats {
		public final double size;
		public final Object atime;
		public final Object ctime;
		public final Object mtime;
		public final Object birthtime;
		private final boolean isSymbolicLink;
		private final boolean isRegularFile;
		private final boolean isDirectory;
		private final boolean isFile;
		private final boolean isReadable;
		private final boolean isWritable;
		private final boolean isExecutable;
		public final boolean isValid;

		private Stats(Path path, boolean follow) throws Exception {
			File file = path.toFile();
			if (!file.exists())
				throw new NoSuchFileException(path.toString());
			Map<String, Object> map = Files.readAttributes(path, "*", getLinkOption(follow));
			this.size = ((Number) map.get("size")).doubleValue();
			this.atime = invocable.invokeMethod(dateMethod, "call", null,
					(double) ((FileTime) map.get("lastAccessTime")).toMillis());
			this.ctime = this.mtime = invocable.invokeMethod(dateMethod, "call", null,
					(double) ((FileTime) map.get("lastModifiedTime")).toMillis());
			this.birthtime = invocable.invokeMethod(dateMethod, "call", null,
					(double) ((FileTime) map.get("creationTime")).toMillis());
			this.isSymbolicLink = (Boolean) map.get("isSymbolicLink");
			this.isRegularFile = (Boolean) map.get("isRegularFile");
			this.isDirectory = (Boolean) map.get("isDirectory");
			this.isFile = file.isFile();
			this.isReadable = file.canRead();
			this.isWritable = file.canWrite();
			this.isExecutable = file.canExecute();
			this.isValid = true;
		}

		private Stats() throws Exception {
			this.size = 0;
			this.atime = this.ctime = this.mtime = this.birthtime = invocable.invokeMethod(dateMethod, "call", null, 0);
			this.isSymbolicLink = this.isRegularFile = this.isDirectory = this.isFile = this.isReadable = this.isWritable = this.isExecutable = this.isValid = false;
		}

		public boolean isDirectory() {
			return isDirectory;
		}

		public boolean isSymbolicLink() {
			return isSymbolicLink;
		}

		public boolean isRegularFile() {
			return isRegularFile;
		}

		public boolean isFile() {
			return isFile;
		}

		public boolean isReadable() {
			return isReadable;
		}

		public boolean isWritable() {
			return isWritable;
		}

		public boolean isExecutable() {
			return isExecutable;
		}
	}

	private static Set<PosixFilePermission> posixFilePermissions(int x) {
		Set<PosixFilePermission> set = new HashSet<>();
		if ((x & 0400) != 0)
			set.add(PosixFilePermission.OWNER_READ);
		if ((x & 0200) != 0)
			set.add(PosixFilePermission.OWNER_WRITE);
		if ((x & 0100) != 0)
			set.add(PosixFilePermission.OWNER_EXECUTE);
		if ((x & 040) != 0)
			set.add(PosixFilePermission.GROUP_READ);
		if ((x & 020) != 0)
			set.add(PosixFilePermission.GROUP_WRITE);
		if ((x & 010) != 0)
			set.add(PosixFilePermission.GROUP_EXECUTE);
		if ((x & 04) != 0)
			set.add(PosixFilePermission.OTHERS_READ);
		if ((x & 02) != 0)
			set.add(PosixFilePermission.OTHERS_WRITE);
		if ((x & 01) != 0)
			set.add(PosixFilePermission.OTHERS_EXECUTE);
		return set;
	}

	private void posixTruncate(FileChannel fc, int len) throws Exception {
		long position = fc.position();
		try {
			if (len > fc.size()) {
				fc.position(len - 1);
				fc.write(ByteBuffer.wrap(new byte[] { 0 }));
			} else
				fc.truncate(len);
		} finally {
			fc.position(position);
		}
	}

	private static Constants constantsStub = new Constants();

	public Fs(EventLoop eventLoop) {
		this.eventLoop = eventLoop;
		this.invocable = eventLoop.getInvocable();
	}

	public void access(String path, int mode, Object callback) {
		eventLoop.execute(callback, r -> accessSync(path, mode));
	}

	public void accessSync(String path, int mode) throws Exception {
		File file = resolvePath(path).toFile();
		if (!file.exists())
			throw new NoSuchFileException(file.toString());
		if (constants.R_OK(mode) && !file.canRead())
			throw new AccessDeniedException("Cannot read " + file);
		if (constants.W_OK(mode) && !file.canWrite())
			throw new AccessDeniedException("Cannot write " + file);
		if (constants.X_OK(mode) && !file.canExecute())
			throw new AccessDeniedException("Cannot execute " + file);
	}

	public void appendFile(Object file, Buffer data, Object callback) {
		eventLoop.execute(callback, r -> appendFileSync(file, data));
	}

	public void appendFileSync(Object file, Buffer data) throws Exception {
		if (file instanceof String)
			try (FileChannel fc = FileChannel.open(resolvePath((String) file), StandardOpenOption.APPEND,
					StandardOpenOption.CREATE)) {
				fc.write(data.toByteBuffer());
			}
		else
			fd2fc(file, fc -> fc.write(data.toByteBuffer(), fc.size()));
	}

	private static boolean isPosixFileSystem(Path path) {
		return path.getFileSystem().supportedFileAttributeViews().contains("posix");
	}

	private void chmod(Path path, int mode, boolean follow, Object callback) {
		if (isPosixFileSystem(path))
			eventLoop.execute(callback, r -> chmodSync(path, mode, follow));
	}

	private void chmodSync(Path path, int mode, boolean follow) throws Exception {
		if (isPosixFileSystem(path))
			Files.getFileAttributeView(path, PosixFileAttributeView.class, getLinkOption(follow))
					.setPermissions(posixFilePermissions(mode));
	}

	private void chown(Path path, Object uid, Object gid, boolean follow, Object callback) {
		if (isPosixFileSystem(path))
			eventLoop.execute(callback, r -> chownSync(path, uid, gid, follow));
	}

	private void chownSync(Path path, Object uid, Object gid, boolean follow) throws Exception {
		if (isPosixFileSystem(path)) {
			UserPrincipalLookupService upls = path.getFileSystem().getUserPrincipalLookupService();
			PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class,
					getLinkOption(follow));
			view.setOwner(upls.lookupPrincipalByName(uid.toString()));
			view.setGroup(upls.lookupPrincipalByGroupName(gid.toString()));
		}
	}

	public void chmod(String path, int mode, Object callback) {
		chmod(resolvePath(path), mode, true, callback);
	}

	public void chmodSync(String path, int mode) throws Exception {
		chmodSync(resolvePath(path), mode, true);
	}

	public void chown(String path, Object uid, Object gid, Object callback) {
		chown(resolvePath(path), uid, gid, true, callback);
	}

	public void chownSync(String path, Object uid, Object gid) throws Exception {
		chownSync(resolvePath(path), uid, gid, true);
	}

	public void close(int fd, Object callback) {
		eventLoop.execute(callback, r -> closeSync(fd));
	}

	public void closeSync(int fd) throws Exception {
		fd2fc(fd, fc -> fc.close());
		pathMap.remove(fd);
		channelMap.remove(fd);
	}

	public final Constants constants = constantsStub;

	public void exists(String path, Object callback) {
		eventLoop.execute(callback, r -> {
			if (!existsSync(path))
				throw new NoSuchFileException(path);
		});
	}

	public boolean existsSync(String path) {
		return Files.exists(resolvePath(path));
	}

	public void fchmod(int fd, int mode, Object callback) throws Exception {
		fd2Path(fd, path -> chmod(path, mode, true, callback));
	}

	public void fchmodSync(int fd, int mode) throws Exception {
		fd2Path(fd, path -> chmodSync(path, mode, true));
	}

	public void fchown(int fd, Object uid, Object gid, Object callback) throws Exception {
		fd2Path(fd, path -> chown(path, uid, gid, true, callback));
	}

	public void fchownSync(int fd, Object uid, Object gid) throws Exception {
		fd2Path(fd, path -> chownSync(path, uid, gid, true));
	}

	public void fdatasync(int fd, Object callback) {
		eventLoop.execute(callback, r -> fdatasyncSync(fd));
	}

	public void fdatasyncSync(int fd) throws Exception {
		fd2fc(fd, fc -> fc.force(false));
	}

	public void fstat(int fd, Object callback) {
		eventLoop.execute(callback, r -> r.add(fstatSync(fd)));
	}

	public Stats fstatSync(int fd) throws Exception {
		return new Stats(fd2Path(fd), true);
	}

	public void fsync(int fd, Object callback) {
		eventLoop.execute(callback, r -> fsyncSync(fd));
	}

	public void fsyncSync(int fd) throws Exception {
		fd2fc(fd, fc -> fc.force(true));
	}

	public void ftruncate(int fd, int len, Object callback) {
		eventLoop.execute(callback, r -> ftruncateSync(fd, len));
	}

	public void ftruncateSync(int fd, int len) throws Exception {
		fd2fc(fd, fc -> posixTruncate(fc, len));
	}

	public void futimes(int fd, int atime, int mtime, Object callback) {
		eventLoop.execute(callback, r -> futimesSync(fd, atime, mtime));
	}

	public void futimesSync(int fd, int atime, int mtime) throws Exception {
		fd2Path(fd,
				path -> Files.setAttribute(
						Files.setAttribute(path, "lastAccessTime", FileTime.fromMillis(1000L * atime)),
						"lastModifiedTime", FileTime.fromMillis(1000L * mtime)));
	}

	public void lchmod(int fd, int mode, Object callback) throws Exception {
		fd2Path(fd, path -> chmod(path, mode, false, callback));
	}

	public void lchmodSync(int fd, int mode) throws Exception {
		fd2Path(fd, path -> chmodSync(path, mode, false));
	}

	public void lchown(int fd, Object uid, Object gid, Object callback) throws Exception {
		fd2Path(fd, path -> chown(path, uid, gid, false, callback));
	}

	public void lchownSync(int fd, Object uid, Object gid) throws Exception {
		fd2Path(fd, path -> chownSync(path, uid, gid, false));
	}

	public void lstat(String path, Object callback) {
		eventLoop.execute(callback, r -> r.add(lstatSync(path)));
	}

	public Stats lstatSync(String path) throws Exception {
		return new Stats(resolvePath(path), false);
	}

	public void mkdir(String path, Object callback) {
		eventLoop.execute(callback, r -> mkdirSync(path));
	}

	public void mkdirSync(String path) throws Exception {
		Files.createDirectory(resolvePath(path));
	}

	public void mkdtemp(String prefix, Object callback) {
		eventLoop.execute(callback, r -> r.add(mkdtempSync(prefix)));
	}

	public String mkdtempSync(String prefix) throws Exception {
		return Files.createTempDirectory(prefix).toAbsolutePath().toString();
	}

	public void open(String path, String flags, int mode, Object callback) {
		eventLoop.execute(callback, r -> r.add(openSync(path, flags, mode)));
	}

	public int openSync(String path, String flags, int mode) throws Exception {
		Set<StandardOpenOption> options;
		switch (flags) {
		case "r":
			options = EnumSet.of(StandardOpenOption.READ);
			break;
		case "r+":
			options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE);
			break;
		case "rs+":
			options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
			break;
		case "w":
			options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING);
			break;
		case "wx":
			options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
			break;
		case "w+":
			options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING);
			break;
		case "wx+":
			options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
			break;
		case "a":
			options = EnumSet.of(StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
			break;
		case "ax":
			options = EnumSet.of(StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
			break;
		case "a+":
			options = EnumSet.of(StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
			break;
		case "ax+":
			options = EnumSet.of(StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
			break;
		default:
			throw new IllegalArgumentException("flag=" + flags);
		}
		Path file = resolvePath(path);
		FileChannel fc = isPosixFileSystem(file)
				? FileChannel.open(file, options, PosixFilePermissions.asFileAttribute(posixFilePermissions(mode)))
				: FileChannel.open(file, options);
		int fd;
		do {
			fd = fdGenerator.getAndIncrement();
		} while (fd >= 0 && fd < 3);
		channelMap.put(fd, fc);
		pathMap.put(fd, file);
		return fd;
	}

	public void range(int fd, Object callback) {
		eventLoop.execute(callback, r -> fd2fc(fd, fc -> {
			r.add((int) fc.position());
			r.add((int) fc.size());
		}));
	}

	public void read(int fd, Buffer buffer, Integer offset, Integer length, Integer position, Object callback) {
		eventLoop.execute(callback, r -> r.add(readSync(fd, buffer, offset, length, position)));
	}

	public void readdir(String path, Object callback) {
		eventLoop.execute(callback, r -> r.add(readdirSync(path)));
	}

	public List<String> readdirSync(String path) throws Exception {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(resolvePath(path))) {
			return StreamSupport.stream(stream.spliterator(), false).map(p -> p.getFileName().toString())
					.collect(Collectors.toList());
		}
	}

	public void readFile(Object file, Object callback) {
		eventLoop.execute(callback, r -> r.add(readFileSync(file)));
	}

	public Buffer readFileSync(Object file) throws Exception {
		if (file instanceof String)
			return new Buffer(Files.readAllBytes(resolvePath((String) file)));
		FileChannel fc = fd2fc(file);
		long position = fc.position();
		try {
			int size = (int) fc.size();
			ByteBuffer bb = ByteBuffer.allocateDirect((int) size);
			fc.position(0);
			fc.read(bb);
			bb.rewind();
			return new Buffer(bb);
		} finally {
			fc.position(position);
		}
	}

	public void readlink(String file, Object callback) {
		eventLoop.execute(callback, r -> r.add(readlinkSync(file)));
	}

	public String readlinkSync(String file) throws Exception {
		return Files.readSymbolicLink(resolvePath(file)).toString();
	}

	public int readSync(int fd, Buffer buffer, Integer offset, Integer length, Integer position) throws Exception {
		FileChannel fc = fd2fc(fd);
		if (position != null)
			fc.position(position);
		ByteBuffer bb = buffer.toByteBuffer();
		bb.position(offset);
		bb.limit(offset + length);
		return fc.read(bb);
	}

	public void realpath(String file, Object callback) {
		eventLoop.execute(callback, r -> r.add(realpathSync(file)));
	}

	public String realpathSync(String file) throws Exception {
		return resolvePath(file).normalize().toAbsolutePath().toString();
	}

	public void rename(String oldPath, String newPath, Object callback) {
		eventLoop.execute(callback, r -> renameSync(oldPath, newPath));
	}

	public void renameSync(String oldPath, String newPath) throws Exception {
		Files.move(resolvePath(oldPath), resolvePath(newPath));
	}

	public void rmdir(String path, Object callback) {
		eventLoop.execute(callback, r -> rmdirSync(path));
	}

	public void rmdirSync(String path) throws Exception {
		Files.delete(resolvePath(path));
	}

	public void stat(String path, Object callback) {
		eventLoop.execute(callback, r -> r.add(statSync(path)));
	}

	public Stats statSync(String path) throws Exception {
		return new Stats(resolvePath(path), true);
	}

	public void symlink(String target, String path, Object callback) {
		eventLoop.execute(callback, r -> symlinkSync(target, path));
	}

	public void symlinkSync(String target, String path) throws Exception {
		Files.createSymbolicLink(resolvePath(path), resolvePath(target));
	}

	public void truncate(Object path, Integer len, Object callback) {
		eventLoop.execute(callback, r -> truncateSync(path, len));
	}

	public void truncateSync(Object path, int len) throws Exception {
		if (path instanceof String) {
			try (FileChannel fc = FileChannel.open(resolvePath((String) path), StandardOpenOption.WRITE)) {
				posixTruncate(fc, len);
			}
		} else {
			fd2fc(path, fc -> posixTruncate(fc, len));
		}
	}

	public void unlink(String path, Object callback) {
		eventLoop.execute(callback, r -> unlinkSync(path));
	}

	public void unlinkSync(String path) throws Exception {
		Files.delete(resolvePath(path));
	}

	public void unwatchFile(String filename, Object listener) {
		if (listener != null) {
			Map<Object, List<Timeout>> listenerMap = watchFileMap.get(resolvePath(filename));
			if (listenerMap != null) {
				List<Timeout> timeoutList = listenerMap.remove(listener);
				if (timeoutList != null)
					timeoutList.forEach(Timeout::clear);
			}
		} else {
			Map<Object, List<Timeout>> listenerMap = watchFileMap.remove(resolvePath(filename));
			if (listenerMap != null)
				listenerMap.values().stream().flatMap(List::stream).forEach(Timeout::clear);
		}
	}

	public void utimes(String path, int atime, int mtime, Object callback) {
		eventLoop.execute(callback, r -> utimesSync(path, atime, mtime));
	}

	public void utimesSync(String path, int atime, int mtime) throws Exception {
		Files.setAttribute(Files.setAttribute(resolvePath(path), "lastAccessTime", FileTime.fromMillis(1000L * atime)),
				"lastModifiedTime", FileTime.fromMillis(1000L * mtime));
	}

	public FSWatcher watch(String filename, boolean persistent, Object callback) {
		return new FSWatcher(resolvePath(filename), persistent, callback);
	}

	public void watchFile(String filename, Object listener, Timeout timeout) {
		watchFileMap.computeIfAbsent(resolvePath(filename), k -> new HashMap<>())
				.computeIfAbsent(listener, k -> new ArrayList<>()).add(timeout);
	}

	public Stats watchFileStats(String filename) throws Exception {
		try {
			return new Stats(resolvePath(filename), true);
		} catch (Exception e) {
			return new Stats();
		}
	}

	public void write(int fd, Buffer buffer, Integer offset, Integer length, Integer position, Object callback) {
		eventLoop.execute(callback, r -> r.add(writeSync(fd, buffer, offset, length, position)));
	}

	public void write(int fd, Buffer buffer, Integer position, Object callback) {
		eventLoop.execute(callback, r -> r.add(writeSync(fd, buffer, position)));
	}

	public void writeBulk(int fd, Object[] buffers, Integer position, Object callback) {
		eventLoop.execute(callback, r -> fd2fc(fd, fc -> {
			if (position != null)
				fc.position(position);
			ByteBuffer[] bbs = new ByteBuffer[buffers.length];
			for (int i = 0; i < bbs.length; i++)
				bbs[i] = ((Buffer) buffers[i]).toByteBuffer();
			r.add((int) fc.write(bbs));
		}));
	}

	public void writeFile(Object file, Buffer buffer, Object callback) {
		eventLoop.execute(callback, r -> writeFileSync(file, buffer));
	}

	public void writeFileSync(Object file, Buffer buffer) throws Exception {
		if (file instanceof String)
			try (FileChannel fc = FileChannel.open(resolvePath((String) file), StandardOpenOption.WRITE,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				fc.write(buffer.toByteBuffer());
			}
		else
			fd2fc(file, fc -> fc.write(buffer.toByteBuffer()));
	}

	public int writeSync(int fd, Buffer buffer, Integer offset, Integer length, Integer position) throws Exception {
		FileChannel fc = fd2fc(fd);
		if (position != null)
			fc.position(position);
		ByteBuffer bb = buffer.toByteBuffer();
		bb.position(offset);
		bb.limit(offset + length);
		return fc.write(bb);
	}

	public int writeSync(int fd, Buffer buffer, Integer position) throws Exception {
		FileChannel fc = fd2fc(fd);
		if (position != null)
			fc.position(position);
		return fc.write(buffer.toByteBuffer());
	}
}
