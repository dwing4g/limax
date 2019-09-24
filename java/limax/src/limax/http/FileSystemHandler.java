package limax.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import limax.codec.SHA1;
import limax.http.ContentType.Major;
import limax.http.HttpServer.Parameter;
import limax.net.Engine;
import limax.util.Closeable;

class FileSystemHandler implements HttpHandler, Closeable {
	private final DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
	private final Path htdocs;
	private final WatchService watchService;
	private final Map<Path, FileHandler> cache = new ConcurrentHashMap<>();
	private final Map<Path, WatchKey> monitors = new ConcurrentHashMap<>();
	private final String textCharset;
	private final int mmapThreshold;
	private final double compressThreshold;
	private final String[] indexes;
	private final boolean browseDir;
	private final String[] browseDirExceptions;

	private class FileHandler implements HttpHandler {
		private final ByteBuffer data;
		private final ByteBuffer gzip;
		private final ByteBuffer[] huge;
		private final String contentType;
		private final String etag;

		private ByteBuffer gzip(byte[] data) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
			try (OutputStream os = new GZIPOutputStream(baos)) {
				os.write(data);
			} catch (Exception e) {
			}
			byte[] gzip = baos.toByteArray();
			return gzip.length < data.length * compressThreshold ? ByteBuffer.wrap(gzip) : null;
		}

		FileHandler(String html) {
			byte[] data = html.getBytes(StandardCharsets.UTF_8);
			this.data = ByteBuffer.wrap(data);
			this.gzip = gzip(data);
			this.huge = null;
			this.contentType = "text/html; charset=utf-8";
			this.etag = Base64.getUrlEncoder().encodeToString(SHA1.digest(data));
		}

		FileHandler(Path path) throws Exception {
			ContentType contentType = ContentType.of(path);
			this.contentType = contentType.is(Major.text) ? contentType + "; charset=" + textCharset
					: contentType.toString();
			MessageDigest md = contentType.etag() ? MessageDigest.getInstance("SHA-1") : null;
			if (contentType.compress()) {
				byte[] data = Files.readAllBytes(path);
				this.data = ByteBuffer.wrap(data);
				this.gzip = gzip(data);
				this.huge = null;
				if (md != null)
					md.update(data);
			} else {
				long size = Files.size(path);
				if (size < mmapThreshold) {
					try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
						fc.read(this.data = ByteBuffer.allocateDirect((int) size));
						this.data.flip();
						this.huge = null;
						if (md != null)
							md.update(this.data.duplicate());
					}
				} else {
					DataSupplier ds = DataSupplier.from(path);
					List<ByteBuffer> list = new ArrayList<>();
					for (ByteBuffer bb; (bb = ds.get()) != null;) {
						list.add(bb);
						if (md != null)
							md.update(bb.duplicate());
					}
					if (list.size() > 1) {
						this.data = null;
						this.huge = list.toArray(new ByteBuffer[0]);
					} else {
						this.data = list.get(0);
						this.huge = null;
					}
				}
				this.gzip = null;
			}
			this.etag = md != null ? Base64.getUrlEncoder().encodeToString(md.digest()) : null;
		}

		@Override
		public DataSupplier handle(HttpExchange exchange) {
			Headers reqHeaders = exchange.getRequestHeaders();
			Headers repHeaders = exchange.getResponseHeaders();
			if (etag != null && etag.equals(reqHeaders.getFirst("If-None-Match"))) {
				repHeaders.set(":status", HttpURLConnection.HTTP_NOT_MODIFIED);
				return null;
			}
			if (etag != null)
				repHeaders.set("ETag", etag);
			repHeaders.set("Content-Type", contentType);
			if (gzip != null) {
				String encoding = reqHeaders.getFirst("Accept-Encoding");
				if (encoding != null && encoding.contains("gzip")) {
					repHeaders.set("Content-Encoding", "gzip");
					return DataSupplier.from(gzip.duplicate());
				}
			}
			String range = reqHeaders.getFirst("range");
			if (range != null && range.startsWith("bytes=")) {
				try {
					int pos = range.indexOf('-', 6);
					long low = Long.parseLong(range.substring(6, pos));
					String last = range.substring(pos + 1);
					long high = last.isEmpty() ? Long.MAX_VALUE : Long.parseLong(last);
					if (high >= low) {
						long length;
						if (data != null) {
							length = data.remaining();
						} else {
							length = 0;
							for (ByteBuffer bb : huge)
								length += bb.remaining();
						}
						high = Math.min(high, length - 1);
						repHeaders.set("Accept-Ranges", "bytes");
						repHeaders.set("Content-Range", "bytes " + low + "-" + high + "/" + length);
						repHeaders.set(":status", HttpURLConnection.HTTP_PARTIAL);
						if (data != null) {
							ByteBuffer bb = data.duplicate();
							bb.position((int) low).limit((int) (high + 1));
							return DataSupplier.from(bb);
						} else {
							for (int i = 0;; i++) {
								int remaining = huge[i].remaining();
								if (low >= remaining) {
									low -= remaining;
									high -= remaining;
								} else {
									ByteBuffer bb = huge[i].duplicate();
									bb.position((int) low);
									if (high < remaining) {
										bb.limit((int) (high + 1));
										return DataSupplier.from(bb);
									}
									for (List<ByteBuffer> bbs = new ArrayList<>();;) {
										high -= remaining;
										bbs.add(bb);
										bb = huge[++i].duplicate();
										remaining = bb.remaining();
										if (high < remaining) {
											bbs.add(bb);
											bb.limit((int) (high + 1));
											return DataSupplier.from(bbs.toArray(new ByteBuffer[0]));
										}
									}
								}
							}
						}
					}
				} catch (Exception e) {
				}
			}
			if (data != null)
				return DataSupplier.from(data.duplicate());
			ByteBuffer[] bbs = new ByteBuffer[huge.length];
			for (int i = 0; i < huge.length; i++)
				bbs[i] = huge[i].duplicate();
			return DataSupplier.from(bbs);
		}
	}

	private FileHandler handleDirectory(Path path, String _path) throws IOException {
		StringBuilder sb = new StringBuilder("<!doctype html><html><title>");
		sb.append(_path).append("</title><body><table><tr>");
		int pos = _path.lastIndexOf('/');
		String parent = pos <= 0 ? "/" : _path.substring(0, pos);
		sb.append("<td><a href=\"").append(parent).append("\">..</a></td><td></td><td></td></tr>");
		List<Object[]> list0 = new ArrayList<>();
		List<Object[]> list1 = new ArrayList<>();
		try (Stream<Path> paths = Files.list(path).sorted()) {
			for (Iterator<Path> it = paths.iterator(); it.hasNext();) {
				Path p = it.next();
				String name = p.getFileName().toString();
				String time = Instant.ofEpochMilli(Files.getLastModifiedTime(p).toMillis())
						.atZone(ZoneId.systemDefault()).format(sdf);
				String href = _path + "/" + name;
				if (Files.isDirectory(p))
					list0.add(new Object[] { href, name + "/", time, "" });
				else
					list1.add(new Object[] { href, name, time, Files.size(p) });
			}
		}
		list0.addAll(list1);
		for (Object[] a : list0)
			sb.append("<tr><td><a href=\"").append(a[0]).append("\">").append(a[1]).append("</a></td><td>").append(a[2])
					.append("</td><td>").append(a[3]).append("</td>");
		sb.append("</table></body></html>");
		return new FileHandler(sb.toString());
	}

	private HttpHandler getHandler(Host host, URI contextURI, URI requestURI) {
		String _path = requestURI.getPath();
		Path path = htdocs.resolve(contextURI.relativize(requestURI).getPath());
		FileHandler data = cache.get(path);
		if (data == null) {
			try {
				for (FileTime t0 = null, t1; !(t1 = Files.getLastModifiedTime(path)).equals(t0); t0 = t1) {
					if (Files.isDirectory(path)) {
						_path = removeSlash(_path);
						for (String index : indexes) {
							String location = _path + "/" + index;
							if (Files.isReadable(path.resolve(index))) {
								return exchange -> {
									Headers headers = exchange.getResponseHeaders();
									headers.set("location", location);
									headers.set(":status", HttpURLConnection.HTTP_MOVED_TEMP);
									return null;
								};
							}
						}
						if (browseDir) {
							for (String exception : browseDirExceptions)
								if (_path.equalsIgnoreCase(exception))
									return (HttpHandler) host.get(Parameter.HANDLER_403);
							data = handleDirectory(path, _path);
						} else {
							for (String exception : browseDirExceptions)
								if (_path.equalsIgnoreCase(exception)) {
									data = handleDirectory(path, _path);
									break;
								}
							if (data == null)
								return (HttpHandler) host.get(Parameter.HANDLER_403);
						}
					} else {
						data = new FileHandler(path);
					}
					cache.put(path, data);
				}
				monitors.computeIfAbsent(Files.isDirectory(path) ? path : path.getParent(), key -> {
					try {
						return key.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
								StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
					} catch (Exception e) {
						return null;
					}
				});
			} catch (Exception e) {
				return (HttpHandler) host.get(Parameter.HANDLER_404);
			}
		}
		return data;
	}

	private static String removeSlash(String s) {
		int l = s.length() - 1;
		return l >= 0 && s.charAt(l) == '/' ? s.substring(0, l) : s;
	}

	FileSystemHandler(Path htdocs, String textCharset, int mmapThreshold, double compressThreshold, String[] indexes,
			boolean browseDir, String[] browseDirExceptions) throws IOException {
		this.htdocs = htdocs;
		this.textCharset = textCharset;
		this.mmapThreshold = mmapThreshold;
		this.compressThreshold = compressThreshold;
		this.indexes = indexes == null ? new String[] {} : indexes.clone();
		this.browseDir = browseDir;
		this.browseDirExceptions = browseDirExceptions == null ? new String[] {} : browseDirExceptions.clone();
		for (int i = 0; i < this.browseDirExceptions.length; i++)
			this.browseDirExceptions[i] = removeSlash(this.browseDirExceptions[i]);
		this.watchService = htdocs.getFileSystem().newWatchService();
		Engine.getApplicationExecutor().execute(() -> {
			while (true) {
				try {
					WatchKey key = watchService.take();
					Path base = (Path) key.watchable();
					for (WatchEvent<?> event : key.pollEvents()) {
						Path p = base.resolve((Path) event.context());
						if (event.kind().equals(StandardWatchEventKinds.ENTRY_DELETE)) {
							WatchKey dirKey = monitors.remove(p);
							if (dirKey != null)
								dirKey.cancel();
						}
						cache.remove(p);
						cache.remove(p.getParent());
					}
					key.reset();
				} catch (ClosedWatchServiceException e) {
					break;
				} catch (Exception e) {
				}
			}
		});
	}

	@Override
	public DataSupplier handle(HttpExchange exchange) throws Exception {
		return getHandler(exchange.getHost(), exchange.getContextURI(), exchange.getRequestURI()).handle(exchange);
	}

	@Override
	public void close() {
		try {
			watchService.close();
		} catch (IOException e) {
		}
	}
}
