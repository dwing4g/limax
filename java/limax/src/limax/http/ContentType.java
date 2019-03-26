package limax.http;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ContentType {
	private final static int COMPRESS = 1;
	private final static int ETAG = 2;

	public enum Major {
		text(true, true), application(false, false), image(false, true), audio(false, false), video(false, false),
		multipart(false, false), model(true, false), font(false, false), example(false, false), message(false, false);

		private final int flag;

		Major(boolean compress, boolean etag) {
			this.flag = (compress ? COMPRESS : 0) | (etag ? ETAG : 0);
		}

		boolean compress() {
			return (flag & COMPRESS) != 0;
		}

		boolean etag() {
			return (flag & ETAG) != 0;
		}
	}

	private final static Map<String, ContentType> map = new HashMap<>();
	private final static ContentType defaultContentType = new ContentType(Major.application, "octet-stream", false,
			false);
	private final Major major;
	private final String minor;
	private final int flag;

	static {
		register("txt", Major.text, "plain");
		register("css", Major.text, "css");
		register("csv", Major.text, "csv");
		register("htm", Major.text, "html");
		register("html", Major.text, "html");
		register("zip", Major.application, "zip");
		register("js", Major.application, "javascript", true, true);
		register("json", Major.application, "json", true, true);
		register("ogx", Major.application, "ogg");
		register("pdf", Major.application, "pdf", true, false);
		register("gif", Major.image, "gif");
		register("jpg", Major.image, "jpeg");
		register("jpeg", Major.image, "jpeg");
		register("tif", Major.image, "tiff");
		register("tiff", Major.image, "tiff");
		register("png", Major.image, "png");
		register("ico", Major.image, "x-icon", true, true);
		register("acc", Major.audio, "acc");
		register("ac3", Major.audio, "ac3");
		register("mp3", Major.audio, "mp3");
		register("oga", Major.audio, "ogg");
		register("mid", Major.audio, "midi");
		register("midi", Major.audio, "midi");
		register("3gpp", Major.video, "3gpp");
		register("mpeg", Major.video, "mpeg");
		register("mp4", Major.video, "mp4");
		register("ogv", Major.video, "ogg");
	}

	ContentType(Major major, String minor, boolean compress, boolean etag) {
		this.major = major;
		this.minor = minor;
		this.flag = (compress ? COMPRESS : 0) | (etag ? ETAG : 0);
	}

	public static void register(String prefix, Major major, String minor) {
		register(prefix, major, minor, false, false);
	}

	public static void register(String prefix, Major major, String minor, boolean compress, boolean etag) {
		map.put(prefix.toLowerCase(), new ContentType(major, minor.toLowerCase(), compress, etag));
	}

	public static ContentType of(Path path) {
		String name = path.getFileName().toString();
		return map.getOrDefault(name.substring(name.lastIndexOf(".") + 1).toLowerCase(), defaultContentType);
	}

	public boolean is(Major major) {
		return this.major == major;
	}

	public boolean compress() {
		return major.compress() || (flag & COMPRESS) != 0;
	}

	public boolean etag() {
		return major.etag() || (flag & ETAG) != 0;
	}

	@Override
	public String toString() {
		return major + "/" + minor;
	}
}
