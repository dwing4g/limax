package limax.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ZipUtils {
	private ZipUtils() {
	}

	private static void appendEntry(ZipOutputStream zos, String name, File file, BasicFileAttributes attrs)
			throws IOException {
		ZipEntry entry = new ZipEntry(name);
		entry.setLastModifiedTime(attrs.lastModifiedTime());
		entry.setCreationTime(attrs.creationTime());
		entry.setLastAccessTime(attrs.lastAccessTime());
		zos.putNextEntry(entry);
		if (file != null) {
			byte[] data = new byte[65536];
			try (InputStream in = new FileInputStream(file)) {
				while (true) {
					int nread = in.read(data, 0, data.length);
					if (nread == -1)
						break;
					zos.write(data, 0, nread);
				}
			}
		}
		zos.closeEntry();
	}

	public static void zip(Path src, OutputStream out) throws IOException {
		try (ZipOutputStream zos = new ZipOutputStream(out)) {
			if (Files.isDirectory(src)) {
				Files.walkFileTree(src, new FileVisitor<Path>() {
					private String toEntryName(Path path) {
						List<String> l = new ArrayList<>();
						for (Path p : path)
							l.add(p.toString());
						return String.join("/", l);
					}

					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
						Path path = src.relativize(dir);
						if (!path.toString().isEmpty())
							appendEntry(zos, toEntryName(path) + "/", null, attrs);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						appendEntry(zos, toEntryName(src.relativize(file)), file.toFile(), attrs);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
						return FileVisitResult.CONTINUE;
					}
				});
			} else {
				appendEntry(zos, src.getFileName().toString(), src.toFile(),
						Files.readAttributes(src, BasicFileAttributes.class));
			}
		}
	}

	public static void zip(Path src, Path dst) throws IOException {
		try (OutputStream out = new FileOutputStream(dst.toFile())) {
			zip(src, out);
		}
	}

	public static void unzip(InputStream in, Path dst) throws IOException {
		if (!Files.isDirectory(dst))
			Files.createDirectories(dst);
		byte[] data = new byte[65536];
		try (ZipInputStream zis = new ZipInputStream(in)) {
			for (ZipEntry entry; (entry = zis.getNextEntry()) != null;) {
				Path path = dst.resolve(Paths.get("", entry.getName().split("/")));
				if (entry.isDirectory()) {
					Files.createDirectories(path);
				} else {
					try (OutputStream os = new BufferedOutputStream(new FileOutputStream(path.toFile()))) {
						while (true) {
							int nread = zis.read(data, 0, data.length);
							if (nread == -1)
								break;
							os.write(data, 0, nread);
						}
					}
				}
				Files.setLastModifiedTime(path, entry.getLastModifiedTime());
				Files.setAttribute(path, "lastAccessTime", entry.getLastAccessTime());
				Files.setAttribute(path, "creationTime", entry.getCreationTime());
			}
		}
	}

	public static void unzip(Path src, Path dst) throws IOException {
		try (InputStream in = new FileInputStream(src.toFile())) {
			unzip(in, dst);
		}
	}
}
