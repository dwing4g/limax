package limax.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import limax.codec.BoundCheck;
import limax.codec.ChannelSource;
import limax.codec.CharSink;
import limax.codec.CodecCollection;
import limax.codec.ExceptionJail;
import limax.codec.SinkChannel;
import limax.codec.StreamSource;

public final class HttpClient {
	private final static Charset defaultCharset = Charset.forName("UTF-8");
	private final static Pattern charsetPattern = Pattern.compile("charset\\s*=\\s*([^\\s;]+)",
			Pattern.CASE_INSENSITIVE);
	private final URI uri;
	private final long timeout;
	private final int maxsize;
	private RandomAccessFile raf;
	private final File path;
	private FileLock lock;
	private String etag;
	private Charset charset;
	private final ByteBuffer headBuffer = ByteBuffer.allocate(64);
	private final boolean staleEnable;

	private FileLock lockOpen(File path) {
		try {
			raf = new RandomAccessFile(path, "rw");
			FileChannel fc = raf.getChannel();
			try {
				return fc.lock();
			} catch (IOException e) {
				fc.close();
				return null;
			}
		} catch (IOException e) {
			return null;
		}
	}

	static Charset getContentCharset(HttpURLConnection conn, Charset defaultCharset) {
		try {
			Matcher matcher = charsetPattern.matcher(conn.getContentType());
			if (matcher.find())
				return Charset.forName(matcher.group(1));
		} catch (Exception e) {
		}
		return defaultCharset;
	}

	public HttpClient(String url, long timeout, int maxsize, File cacheDir, boolean staleEnable) {
		this.uri = URI.create(url);
		this.timeout = timeout;
		this.maxsize = maxsize;
		this.staleEnable = staleEnable;
		File path = cacheDir == null ? null : new File(cacheDir, Helper.toFileNameString(uri.toString()));
		if (path != null) {
			lock = lockOpen(path);
			if (lock == null)
				path = null;
			else {
				try {
					lock.channel().read(headBuffer);
					headBuffer.flip();
					if (headBuffer.getInt() != lock.channel().size())
						throw new IOException();
					byte[] b = new byte[headBuffer.get()];
					headBuffer.get(b);
					String sign = new String(b, defaultCharset);
					int pos = sign.indexOf(' ');
					charset = Charset.forName(sign.substring(0, pos));
					etag = sign.substring(pos + 1);
				} catch (Exception e) {
					try {
						lock.channel().truncate(0);
					} catch (IOException e1) {
						try {
							lock.channel().close();
						} catch (IOException e2) {
						}
						path.delete();
						path = null;
						lock = null;
						etag = null;
						charset = null;
					}
				}
			}
		}
		this.path = path;
	}

	public void transfer(CharSink sink) throws IOException {
		try {
			_transfer(sink);
		} finally {
			if (lock != null)
				try {
					lock.channel().close();
				} catch (IOException e) {
				}
			if (raf != null)
				try {
					raf.close();
				} catch (IOException e) {
				}
		}
	}

	private void _transfer(CharSink sink) throws IOException {
		long starttime = System.currentTimeMillis();
		HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
		conn.setConnectTimeout((int) timeout);
		conn.setReadTimeout((int) timeout);
		if (etag != null)
			conn.setRequestProperty("If-None-Match", etag);
		int responseCode;
		try {
			responseCode = conn.getResponseCode();
		} catch (Exception e) {
			responseCode = HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
		}
		if (responseCode != HttpURLConnection.HTTP_OK) {
			if (responseCode != HttpURLConnection.HTTP_NOT_MODIFIED && !staleEnable || lock == null || charset == null)
				throw new IOException("HttpClient cache missing. responseCode = " + responseCode);
			try {
				sink.setCharset(charset);
				new ChannelSource(lock.channel(), sink).flush();
			} catch (Exception e) {
				throw new IOException("HttpClient transfer cache data.", e);
			}
			return;
		}
		charset = getContentCharset(conn, defaultCharset);
		if (lock != null)
			try {
				etag = conn.getHeaderField("ETag");
				if (etag == null)
					throw new IOException();
				headBuffer.clear();
				byte[] sign = (charset.name() + " " + etag).getBytes(defaultCharset);
				headBuffer.putInt(0).put((byte) sign.length).put(sign).position(0);
				lock.channel().position(0).write(headBuffer);
			} catch (Exception e) {
				try {
					lock.channel().close();
				} catch (IOException e1) {
				}
				path.delete();
				lock = null;
			}
		InputStream is = null;
		try {
			is = conn.getInputStream();
			ExceptionJail ej = null;
			sink.setCharset(charset);
			new StreamSource(is,
					new BoundCheck(maxsize, timeout - (System.currentTimeMillis() - starttime), lock == null ? sink
							: new CodecCollection(sink, ej = new ExceptionJail(new SinkChannel(lock.channel())))))
									.flush();
			if (ej != null && ej.get() != null)
				throw ej.get();
			if (lock != null) {
				headBuffer.clear();
				headBuffer.putInt((int) lock.channel().size());
				headBuffer.flip();
				lock.channel().write(headBuffer, 0);
			}
		} catch (Exception e) {
			if (lock != null) {
				try {
					lock.channel().close();
				} catch (IOException e1) {
				}
				path.delete();
				lock = null;
			}
			throw new IOException("HttpClient transfer net data.", e);
		} finally {
			if (is != null)
				try {
					is.close();
				} catch (IOException e) {
				}
		}
	}
}
