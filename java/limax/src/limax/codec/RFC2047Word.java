package limax.codec;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RFC2047Word {
	private static byte[] encodeQP(byte[] data) {
		byte nibble;
		byte[] r = new byte[data.length * 3];
		int pos = 0;
		for (int i = 0; i < data.length; i++) {
			if (data[i] > 32 && data[i] <= 127 && data[i] != '=' && data[i] != '?' && data[i] != '_')
				r[pos++] = data[i];
			else if (data[i] == 32) {
				r[pos++] = '_';
			} else {
				r[pos++] = '=';
				nibble = (byte) ((data[i] >> 4) & 0xf);
				r[pos++] = (byte) (nibble < 10 ? nibble + '0' : (nibble - 10) + 'A');
				nibble = (byte) (data[i] & 0xf);
				r[pos++] = (byte) (nibble < 10 ? nibble + '0' : (nibble - 10) + 'A');
			}
		}
		return Arrays.copyOf(r, pos);
	}

	private static byte[] decodeQP(byte[] data) {
		byte[] r = new byte[data.length];
		int pos = 0;
		for (int i = 0; i < data.length; i++) {
			if (data[i] == '_') {
				r[pos++] = ' ';
			} else if (data[i] != '=') {
				r[pos++] = data[i];
			} else {
				byte b0 = data[++i];
				byte b1 = data[++i];
				b0 = (byte) (b0 - (b0 >= 'A' ? 'A' - 10 : '0'));
				b1 = (byte) (b1 - (b1 >= 'A' ? 'A' - 10 : '0'));
				r[pos++] = (byte) (b0 * 16 + b1);
			}
		}
		return Arrays.copyOf(r, pos);
	}

	public static String decode(String s) {
		try {
			StringBuffer sb = new StringBuffer();
			Matcher m = Pattern.compile("=\\?([^?]+)\\?([bBqQ])\\?([^?]+)\\?=")
					.matcher(s.replaceAll("\\?=\\s*=\\?", "?==?"));
			while (m.find())
				m.appendReplacement(sb, new String(m.group(2).equalsIgnoreCase("B")
						? Base64Decode.transform(m.group(3).getBytes()) : decodeQP(m.group(3).getBytes()), m.group(1)));
			m.appendTail(sb);
			return sb.toString().trim();
		} catch (Exception e) {
		}
		return s.trim();
	}

	private static String encode(String s, Charset charset, String encoding) {
		StringBuilder sb = new StringBuilder();
		sb.append("=?").append(charset.name()).append("?").append(encoding).append("?");
		sb.append(new String(encoding.equalsIgnoreCase("B") ? Base64Encode.transform(s.getBytes(charset))
				: encodeQP(s.getBytes(charset))));
		sb.append("?=");
		return sb.toString();
	}

	private static String encode(String s, int firstLength, Charset charset) {
		String b = encode(s, charset, "B");
		if (b.length() <= firstLength)
			return b;
		String q = encode(s, charset, "Q");
		if (q.length() <= firstLength)
			return q;
		String encoding = b.length() <= q.length() ? "B" : "Q";
		StringBuffer sb = new StringBuffer();
		int from = 0;
		String last = null;
		for (int i = 1; i <= s.length(); i++) {
			String trys = encode(s.substring(from, i), charset, encoding);
			if (trys.length() > firstLength) {
				if (last != null)
					sb.append(last);
				sb.append("\r\n ");
				from = i - 1;
				firstLength = 73;
				last = null;
			} else {
				last = trys;
			}
		}
		return sb.append(last != null ? last : encode(s.substring(from, s.length()), charset, encoding)).toString();
	}

	static String encode(String s, int firstLength) {
		try {
			return s.length() <= firstLength && Charset.forName("us-ascii").newEncoder().canEncode(s) ? s
					: encode(s, firstLength, Charset.forName("UTF-8"));
		} catch (Throwable e) {
		}
		return s;
	}
}
