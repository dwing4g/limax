package limax.codec;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

public final class QrCode {
	private static final int[] gf_exp = new int[512];
	private static final int[] gf_log = new int[256];
	static {
		gf_exp[0] = 1;
		for (int x = 1, i = 1; i < 255; i++) {
			if (((x <<= 1) & 0x100) != 0)
				x ^= 0x11d;
			gf_exp[i] = x;
			gf_log[x] = i;
		}
		for (int i = 255; i < 512; i++)
			gf_exp[i] = gf_exp[i - 255];
	}

	private static int gf_mul(int x, int y) {
		return x == 0 || y == 0 ? 0 : gf_exp[gf_log[x] + gf_log[y]];
	}

	private static int gf_div(int x, int y) {
		if (y == 0)
			throw new ArithmeticException();
		if (x == 0)
			return 0;
		return gf_exp[gf_log[x] + 255 - gf_log[y]];
	}

	private static void quadrilateralToSquare(float x0, float y0, float x1, float y1, float x2, float y2, float x3,
			float y3, float[] coef) {
		float[] r = new float[9];
		squareToQuadrilateral(x0, y0, x1, y1, x2, y2, x3, y3, r);
		coef[0] = r[4] * r[8] - r[5] * r[7];
		coef[1] = r[2] * r[7] - r[1] * r[8];
		coef[2] = r[1] * r[5] - r[2] * r[4];
		coef[3] = r[5] * r[6] - r[3] * r[8];
		coef[4] = r[0] * r[8] - r[2] * r[6];
		coef[5] = r[2] * r[3] - r[0] * r[5];
		coef[6] = r[3] * r[7] - r[4] * r[6];
		coef[7] = r[1] * r[6] - r[0] * r[7];
		coef[8] = r[0] * r[4] - r[1] * r[3];
	}

	private static boolean rs_correct_msg(byte[] msg, int msg_length, int nsym) {
		int[] syndromes = new int[nsym];
		boolean err = false;
		for (int i = 0; i < nsym; i++) {
			int s = msg[0] & 0xff;
			for (int j = 1; j < msg_length; j++)
				s = gf_mul(s, gf_exp[i]) ^ (msg[j] & 0xff);
			if ((syndromes[i] = s) != 0)
				err = true;
		}
		if (err) {
			int[] err_poly = new int[nsym + 1];
			int[] old_poly = new int[nsym + 1];
			int[] tmp_poly = new int[nsym + 1];
			err_poly[0] = old_poly[0] = 1;
			int err_poly_length = 1;
			int old_poly_length = 1;
			for (int i = 0; i < nsym; i++) {
				old_poly[old_poly_length++] = 0;
				int delta = syndromes[i];
				for (int j = 1; j < err_poly_length; j++)
					delta ^= gf_mul(err_poly[err_poly_length - 1 - j], syndromes[i - j]);
				if (delta == 0)
					continue;
				int dinv = gf_div(1, delta);
				if (old_poly_length > err_poly_length) {
					for (int j = 0; j < old_poly_length; j++)
						tmp_poly[j] = gf_mul(old_poly[j], delta);
					for (int j = 0; j < err_poly_length; j++)
						old_poly[j] = gf_mul(err_poly[j], dinv);
					int[] t0 = err_poly;
					err_poly = tmp_poly;
					tmp_poly = t0;
					int t1 = old_poly_length;
					old_poly_length = err_poly_length;
					err_poly_length = t1;
				}
				int off = err_poly_length - old_poly_length;
				for (int j = 0; j < old_poly_length; j++)
					err_poly[j + off] ^= gf_mul(old_poly[j], delta);
			}
			int errs = err_poly_length - 1;
			if (errs << 1 > nsym)
				return false;
			int[] err_pos = tmp_poly;
			int err_pos_length = 0;
			for (int i = 0; i < msg_length; i++) {
				int x = gf_exp[255 - i];
				int y = err_poly[0];
				for (int j = 1; j < err_poly_length; j++)
					y = gf_mul(y, x) ^ err_poly[j];
				if (y == 0)
					err_pos[err_pos_length++] = msg_length - 1 - i;
			}
			if (err_pos_length != errs)
				return false;
			int q_length = errs + 1;
			int[] q = err_poly;
			q[0] = 1;
			q[1] = 0;
			for (int i = 0; i < errs; i++) {
				int a, b, x = gf_exp[msg_length - 1 - err_pos[i]];
				q[0] = gf_mul(a = q[0], x);
				for (int j = 0; j <= i; j++, a = b)
					q[j + 1] = a ^ gf_mul(b = q[j + 1], x);
				q[i + 2] = 0;
			}
			int[] p = syndromes;
			int p_length = errs;
			for (int i = 0; i < p_length >> 1; i++) {
				int t = p[i];
				p[i] = p[p_length - i - 1];
				p[p_length - i - 1] = t;
			}
			int[] r = old_poly;
			for (int i = 0; i < p_length + q_length - 1; i++)
				r[i] = 0;
			for (int i = 0; i < p_length; i++)
				for (int j = 0; j < q_length; j++)
					r[i + j] ^= gf_mul(p[i], q[j]);
			p = r;
			for (int i = 0; i < p_length; i++)
				p[i] = r[i + q_length - 1];
			for (int k = q_length & 1, i = 0; i < errs; i++) {
				int x1 = gf_exp[err_pos[i] + 256 - msg_length];
				int x2 = gf_mul(x1, x1);
				int y = p[0];
				for (int j = 1; j < p_length; j++)
					y = gf_mul(y, x1) ^ p[j];
				int z = q[k];
				for (int j = k + 2; j < q_length; j += 2)
					z = gf_mul(z, x2) ^ q[j];
				msg[err_pos[i]] ^= gf_div(y, gf_mul(x1, z));
			}
		}
		return true;
	}

	private static void squareToQuadrilateral(float x0, float y0, float x1, float y1, float x2, float y2, float x3,
			float y3, float[] coef) {
		float dx3 = x0 - x1 + x2 - x3;
		float dy3 = y0 - y1 + y2 - y3;
		if (dx3 == 0.0f && dy3 == 0.0f) {
			coef[0] = x1 - x0;
			coef[1] = y1 - y0;
			coef[2] = 0.0f;
			coef[3] = x2 - x1;
			coef[4] = y2 - y1;
			coef[5] = 0.0f;
		} else {
			float dx1 = x1 - x2;
			float dx2 = x3 - x2;
			float dy1 = y1 - y2;
			float dy2 = y3 - y2;
			float denominator = dx1 * dy2 - dx2 * dy1;
			float a13 = (dx3 * dy2 - dx2 * dy3) / denominator;
			float a23 = (dx1 * dy3 - dx3 * dy1) / denominator;
			coef[0] = x1 - x0 + a13 * x1;
			coef[1] = y1 - y0 + a13 * y1;
			coef[2] = a13;
			coef[3] = x3 - x0 + a23 * x3;
			coef[4] = y3 - y0 + a23 * y3;
			coef[5] = a23;
		}
		coef[6] = x0;
		coef[7] = y0;
		coef[8] = 1.0f;
	}

	private static void quadrilateralToQuadrilateral(float x0, float y0, float x1, float y1, float x2, float y2,
			float x3, float y3, float x0p, float y0p, float x1p, float y1p, float x2p, float y2p, float x3p, float y3p,
			float[] coef) {
		float[] a = new float[9];
		float[] b = new float[9];
		quadrilateralToSquare(x0, y0, x1, y1, x2, y2, x3, y3, b);
		squareToQuadrilateral(x0p, y0p, x1p, y1p, x2p, y2p, x3p, y3p, a);
		coef[0] = a[0] * b[0] + a[3] * b[1] + a[6] * b[2];
		coef[1] = a[1] * b[0] + a[4] * b[1] + a[7] * b[2];
		coef[2] = a[2] * b[0] + a[5] * b[1] + a[8] * b[2];
		coef[3] = a[0] * b[3] + a[3] * b[4] + a[6] * b[5];
		coef[4] = a[1] * b[3] + a[4] * b[4] + a[7] * b[5];
		coef[5] = a[2] * b[3] + a[5] * b[4] + a[8] * b[5];
		coef[6] = a[0] * b[6] + a[3] * b[7] + a[6] * b[8];
		coef[7] = a[1] * b[6] + a[4] * b[7] + a[7] * b[8];
		coef[8] = a[2] * b[6] + a[5] * b[7] + a[8] * b[8];
	}

	private static void transform(float[] coef, float[] points, int length) {
		float a11 = coef[0];
		float a12 = coef[1];
		float a13 = coef[2];
		float a21 = coef[3];
		float a22 = coef[4];
		float a23 = coef[5];
		float a31 = coef[6];
		float a32 = coef[7];
		float a33 = coef[8];
		for (int i = 0; i < length; i += 2) {
			float x = points[i];
			float y = points[i + 1];
			float denominator = a13 * x + a23 * y + a33;
			points[i] = (a11 * x + a21 * y + a31) / denominator;
			points[i + 1] = (a12 * x + a22 * y + a32) / denominator;
		}
	}

	public final static int ECL_M = 0;
	public final static int ECL_L = 1;
	public final static int ECL_H = 2;
	public final static int ECL_Q = 3;
	private final static int[][] errorCorrectionCharacteristics = { { 26, 1, 1, 1, 1, 10, 7, 17, 13 },
			{ 44, 1, 1, 1, 1, 16, 10, 28, 22 }, { 70, 1, 1, 2, 2, 26, 15, 22, 18 }, { 100, 2, 1, 4, 2, 18, 20, 16, 26 },
			{ 134, 2, 1, 4, 4, 24, 26, 22, 18 }, { 172, 4, 2, 4, 4, 16, 18, 28, 24 },
			{ 196, 4, 2, 5, 6, 18, 20, 26, 18 }, { 242, 4, 2, 6, 6, 22, 24, 26, 22 },
			{ 292, 5, 2, 8, 8, 22, 30, 24, 20 }, { 346, 5, 4, 8, 8, 26, 18, 28, 24 },
			{ 404, 5, 4, 11, 8, 30, 20, 24, 28 }, { 466, 8, 4, 11, 10, 22, 24, 28, 26 },
			{ 532, 9, 4, 16, 12, 22, 26, 22, 24 }, { 581, 9, 4, 16, 16, 24, 30, 24, 20 },
			{ 655, 10, 6, 18, 12, 24, 22, 24, 30 }, { 733, 10, 6, 16, 17, 28, 24, 30, 24 },
			{ 815, 11, 6, 19, 16, 28, 28, 28, 28 }, { 901, 13, 6, 21, 18, 26, 30, 28, 28 },
			{ 991, 14, 7, 25, 21, 26, 28, 26, 26 }, { 1085, 16, 8, 25, 20, 26, 28, 28, 30 },
			{ 1156, 17, 8, 25, 23, 26, 28, 30, 28 }, { 1258, 17, 9, 34, 23, 28, 28, 24, 30 },
			{ 1364, 18, 9, 30, 25, 28, 30, 30, 30 }, { 1474, 20, 10, 32, 27, 28, 30, 30, 30 },
			{ 1588, 21, 12, 35, 29, 28, 26, 30, 30 }, { 1706, 23, 12, 37, 34, 28, 28, 30, 28 },
			{ 1828, 25, 12, 40, 34, 28, 30, 30, 30 }, { 1921, 26, 13, 42, 35, 28, 30, 30, 30 },
			{ 2051, 28, 14, 45, 38, 28, 30, 30, 30 }, { 2185, 29, 15, 48, 40, 28, 30, 30, 30 },
			{ 2323, 31, 16, 51, 43, 28, 30, 30, 30 }, { 2465, 33, 17, 54, 45, 28, 30, 30, 30 },
			{ 2611, 35, 18, 57, 48, 28, 30, 30, 30 }, { 2761, 37, 19, 60, 51, 28, 30, 30, 30 },
			{ 2876, 38, 19, 63, 53, 28, 30, 30, 30 }, { 3034, 40, 20, 66, 56, 28, 30, 30, 30 },
			{ 3196, 43, 21, 70, 59, 28, 30, 30, 30 }, { 3362, 45, 22, 74, 62, 28, 30, 30, 30 },
			{ 3532, 47, 24, 77, 65, 28, 30, 30, 30 }, { 3706, 49, 25, 81, 68, 28, 30, 30, 30 } };

	private static int getTotalNumberOfCodewords(int version) {
		return errorCorrectionCharacteristics[version - 1][0];
	}

	private static int getNumberOfErrorCorrectionBlocks(int version, int ecl) {
		return errorCorrectionCharacteristics[version - 1][ecl + 1];
	}

	private static int getNumberOfErrorCorrectionCodewordsPerBlock(int version, int ecl) {
		return errorCorrectionCharacteristics[version - 1][ecl + 5];
	}

	private static int getBitCapacity(int version, int ecl) {
		return (getTotalNumberOfCodewords(version) - getNumberOfErrorCorrectionBlocks(version, ecl)
				* getNumberOfErrorCorrectionCodewordsPerBlock(version, ecl)) * 8;
	}

	private static class BitStream {
		private long[] data;
		private int nbits;
		private int pos;

		public BitStream() {
			data = new long[16];
		}

		private BitStream(byte[] b) {
			this.nbits = b.length << 3;
			int n = b.length >> 3;
			int m = b.length & 7;
			this.data = new long[n + (m == 0 ? 0 : 1)];
			int p = 0;
			for (int i = 0; i < n; i++) {
				long v = (b[p++] & 0xffl) << 56;
				v |= (b[p++] & 0xffl) << 48;
				v |= (b[p++] & 0xffl) << 40;
				v |= (b[p++] & 0xffl) << 32;
				v |= (b[p++] & 0xffl) << 24;
				v |= (b[p++] & 0xffl) << 16;
				v |= (b[p++] & 0xffl) << 8;
				v |= (b[p++] & 0xffl);
				data[i] = v;
			}
			if (m > 0) {
				long v = 0;
				for (int i = 0; i < m; i++)
					v = (v << 8) | (b[p++] & 0xffl);
				data[data.length - 1] = v << ((8 - m) << 3);
			}
		}

		public byte[] toByteArray() {
			byte[] r = new byte[(nbits >> 3) + ((nbits & 7) != 0 ? 1 : 0)];
			int n = nbits >> 6;
			int j = 0;
			for (int i = 0; i < n; i++) {
				long v = data[i];
				r[j++] = (byte) (v >> 56);
				r[j++] = (byte) (v >> 48);
				r[j++] = (byte) (v >> 40);
				r[j++] = (byte) (v >> 32);
				r[j++] = (byte) (v >> 24);
				r[j++] = (byte) (v >> 16);
				r[j++] = (byte) (v >> 8);
				r[j++] = (byte) v;
			}
			int m = nbits & 63;
			m = (m >> 3) + ((m & 7) != 0 ? 1 : 0);
			for (int i = 0; i < m; i++)
				r[j++] = (byte) (data[n] >> (56 - (i << 3)));
			return r;
		}

		public static BitStream fromByteArray(byte[] data) {
			return new BitStream(data);
		}

		public void append(long val, int len) {
			if (nbits + len > data.length * 64)
				data = Arrays.copyOf(data, data.length * 2);
			int shift = 64 - (nbits & 63) - len;
			if (shift >= 0) {
				data[nbits >> 6] |= val << shift;
			} else {
				data[nbits >> 6] |= val >>> -shift;
				data[(nbits >> 6) + 1] = val << (64 + shift);
			}
			nbits += len;
		}

		public long get(int len) {
			if (len + pos > nbits)
				len = nbits - pos;
			if (len == 0)
				return 0;
			int shift = 64 - (pos & 63) - len;
			long v;
			if (shift >= 0) {
				v = data[pos >> 6] >> shift;
			} else {
				v = data[pos >> 6] << -shift;
				v |= data[(pos >> 6) + 1] >>> (64 + shift);
			}
			pos += len;
			return v & (0xffffffffffffffffl >>> (64 - len));
		}
	}

	private static boolean testBit(int x, int i) {
		return (x & (1 << i)) != 0;
	}

	private static void initializeVersion(int version, boolean[] modules, boolean[] funmask, int size) {
		int n = size - 7;
		for (int i = 0, j = size - 1; i < 7; i++) {
			int k = n + i;
			modules[i] = modules[6 * size + i] = modules[i * size] = modules[i * size + 6] = true;
			modules[n * size + i] = modules[j * size + i] = modules[i * size + n] = modules[i * size + j] = true;
			modules[k] = modules[6 * size + k] = modules[k * size] = modules[k * size + 6] = true;
		}
		for (int i = 2; i < 5; i++)
			for (int j = 2; j < 5; j++)
				modules[j * size + i] = modules[(n + j) * size + i] = modules[j * size + n + i] = true;
		n--;
		for (int i = 0; i < 8; i++)
			for (int j = 0; j < 8; j++)
				funmask[j * size + i] = funmask[(n + j) * size + i] = funmask[j * size + n + i] = true;
		for (int i = 8; i < n; i++) {
			modules[6 * size + i] = modules[i * size + 6] = (i & 1) == 0;
			funmask[6 * size + i] = funmask[i * size + 6] = true;
		}
		if (version > 1) {
			n = version / 7 + 2;
			int s = version == 32 ? 26 : (version * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
			int[] r = new int[n];
			for (int i = n, p = version * 4 + 10; --i > 0; p -= s)
				r[i] = p;
			r[0] = 6;
			for (int a = 0; a < n; a++)
				for (int b = 0; b < n; b++) {
					if (a == 0 && b == 0 || a == 0 && b == n - 1 || a == n - 1 && b == 0)
						continue;
					int x = r[b];
					int y = r[a];
					for (int i = -2; i <= 2; i++) {
						for (int j = -2; j <= 2; j++)
							funmask[(y + j) * size + x + i] = true;
						modules[(y - 2) * size + x + i] = true;
						modules[(y + 2) * size + x + i] = true;
						modules[(y + i) * size + x - 2] = true;
						modules[(y + i) * size + x + 2] = true;
					}
					modules[y * size + x] = true;
				}
		}
		if (version > 6) {
			int r = version;
			for (int i = 0; i < 12; i++)
				r = (r << 1) ^ ((r >> 11) * 0x1f25);
			int v = version << 12 | r;
			for (int i = 0; i < 18; i++) {
				int x = i / 3;
				int y = size - 11 + i % 3;
				funmask[y * size + x] = funmask[x * size + y] = true;
				if (testBit(v, i))
					modules[y * size + x] = modules[x * size + y] = true;
			}
		}
		for (int i = 0; i < 9; i++)
			funmask[i * size + 8] = funmask[8 * size + i] = true;
		for (int i = 0; i < 8; i++)
			funmask[8 * size + size - i - 1] = funmask[(size - 8 + i) * size + 8] = true;
		modules[(size - 8) * size + 8] = true;
	}

	private static int selectMaskPattern(boolean[] modules, boolean[] funmask, int size, int ecl) {
		int pattern = 0;
		int minPenaltyScore = Integer.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			placeMask(modules, funmask, size, ecl, i);
			maskPattern(modules, funmask, size, i);
			int penaltyScore = computePenaltyScore(modules, size);
			if (penaltyScore < minPenaltyScore) {
				minPenaltyScore = penaltyScore;
				pattern = i;
			}
			maskPattern(modules, funmask, size, i);
		}
		return pattern;
	}

	private static void maskPattern(boolean[] modules, boolean[] funmask, int size, int pattern) {
		for (int p = 0, i = 0; i < size; i++) {
			for (int j = 0; j < size; j++, p++) {
				if (!funmask[p])
					switch (pattern) {
					case 0:
						modules[p] ^= (i + j) % 2 == 0;
						break;
					case 1:
						modules[p] ^= i % 2 == 0;
						break;
					case 2:
						modules[p] ^= j % 3 == 0;
						break;
					case 3:
						modules[p] ^= (i + j) % 3 == 0;
						break;
					case 4:
						modules[p] ^= (i / 2 + j / 3) % 2 == 0;
						break;
					case 5:
						modules[p] ^= i * j % 2 + i * j % 3 == 0;
						break;
					case 6:
						modules[p] ^= (i * j % 2 + i * j % 3) % 2 == 0;
						break;
					case 7:
						modules[p] ^= ((i + j) % 2 + i * j % 3) % 2 == 0;
						break;
					}
			}
		}
	}

	private static int computePenaltyScore(boolean[] modules, int size) {
		int score = 0;
		int dark = 0;
		for (int i = 0; i < size; i++) {
			boolean xcolor = modules[i * size];
			boolean ycolor = modules[i];
			int xsame = 1;
			int ysame = 1;
			int xbits = modules[i * size] ? 1 : 0;
			int ybits = modules[i] ? 1 : 0;
			dark += modules[i * size] ? 1 : 0;
			for (int j = 1; j < size; j++) {
				if (modules[i * size + j] != xcolor) {
					xcolor = modules[i * size + j];
					xsame = 1;
				} else {
					if (++xsame == 5)
						score += 3;
					else if (xsame > 5)
						score++;
				}
				if (modules[j * size + i] != ycolor) {
					ycolor = modules[j * size + i];
					ysame = 1;
				} else {
					if (++ysame == 5)
						score += 3;
					else if (ysame > 5)
						score++;
				}
				xbits = ((xbits << 1) & 0x7ff) | (modules[i * size + j] ? 1 : 0);
				ybits = ((ybits << 1) & 0x7ff) | (modules[j * size + i] ? 1 : 0);
				if (j >= 10) {
					if (xbits == 0x5d || xbits == 0x5d0)
						score += 40;
					if (ybits == 0x5d || ybits == 0x5d0)
						score += 40;
				}
				dark += modules[i * size + j] ? 1 : 0;
			}
		}
		for (int i = 0; i < size - 1; i++)
			for (int j = 0; j < size - 1; j++) {
				boolean c = modules[i * size + j];
				if (c == modules[i * size + j + 1] && c == modules[(i + 1) * size + j]
						&& c == modules[(i + 1) * size + j + 1])
					score += 3;
			}
		dark *= 20;
		for (int k = 0, total = size * size; dark < total * (9 - k) || dark > total * (11 + k); k++)
			score += 10;
		return score;
	}

	private static void placeErrorCorrectionCodewords(boolean[] modules, boolean[] funmask, int size,
			byte[] errorCorrectionCodewords) {
		for (int i = 0, bitLength = errorCorrectionCodewords.length << 3, x = size - 1, y = size
				- 1, dir = -1; x >= 1; x -= 2, y += (dir = -dir)) {
			if (x == 6)
				x = 5;
			for (; y >= 0 && y < size; y += dir)
				for (int j = 0; j < 2; j++) {
					int p = y * size + x - j;
					if (!funmask[p] && i < bitLength) {
						modules[p] = testBit(errorCorrectionCodewords[i >> 3], 7 - (i & 7));
						i++;
					}
				}
		}
	}

	private static byte[] readErrorCorrectionCodewords(boolean[] modules, boolean[] funmask, int size) {
		BitStream bs = new BitStream();
		for (int i = 0, bitLength = getTotalNumberOfCodewords((size - 17) / 4) << 3, x = size - 1, y = size
				- 1, dir = -1; x >= 1; x -= 2, y += (dir = -dir)) {
			if (x == 6)
				x = 5;
			for (; y >= 0 && y < size; y += dir)
				for (int j = 0; j < 2; j++) {
					int p = y * size + x - j;
					if (!funmask[p] && i < bitLength) {
						bs.append(modules[p] ? 1 : 0, 1);
						i++;
					}
				}
		}
		return bs.toByteArray();
	}

	private static void placeMask(boolean[] modules, boolean[] funmask, int size, int ecl, int mask) {
		int v = ecl << 3 | mask;
		int r = v;
		for (int i = 0; i < 10; i++)
			r = (r << 1) ^ ((r >> 9) * 0x537);
		v = ((v << 10) | r) ^ 0x5412;
		for (int i = 0; i < 6; i++)
			modules[i * size + 8] = testBit(v, i);
		modules[7 * size + 8] = testBit(v, 6);
		modules[8 * size + 8] = testBit(v, 7);
		modules[8 * size + 7] = testBit(v, 8);
		for (int i = 9; i < 15; i++)
			modules[8 * size + 14 - i] = testBit(v, i);
		for (int i = 0; i < 8; i++)
			modules[8 * size + size - 1 - i] = testBit(v, i);
		for (int i = 8; i < 15; i++)
			modules[(size - 15 + i) * size + 8] = testBit(v, i);
	}

	private static byte[] generateErrorCorrectionCodewords(byte[] codewords, int version, int ecl) {
		int totalNumberOfCodewords = getTotalNumberOfCodewords(version);
		int numberOfErrorCorrectionBlocks = getNumberOfErrorCorrectionBlocks(version, ecl);
		int numberOfErrorCorrectionCodewordsPerBlock = getNumberOfErrorCorrectionCodewordsPerBlock(version, ecl);
		int numberOfShortBlocks = numberOfErrorCorrectionBlocks
				- totalNumberOfCodewords % numberOfErrorCorrectionBlocks;
		int lengthOfShortBlock = totalNumberOfCodewords / numberOfErrorCorrectionBlocks;
		byte[] coef = new byte[numberOfErrorCorrectionCodewordsPerBlock];
		coef[coef.length - 1] = 1;
		for (int j, root = 1, i = 0; i < coef.length; i++) {
			for (j = 0; j < coef.length - 1; j++)
				coef[j] = (byte) (gf_mul(coef[j] & 0xff, root) ^ coef[j + 1]);
			coef[j] = (byte) gf_mul(coef[j] & 0xff, root);
			root = gf_mul(root, 2);
		}
		int errorCorrectionBase = lengthOfShortBlock + 1 - coef.length;
		byte[][] blocks = new byte[numberOfErrorCorrectionBlocks][lengthOfShortBlock + 1];
		for (int pos = 0, i = 0; i < numberOfErrorCorrectionBlocks; i++) {
			byte[] block = blocks[i];
			int len = lengthOfShortBlock + (i < numberOfShortBlocks ? 0 : 1) - coef.length;
			for (int j = 0, k; j < len; j++) {
				int factor = ((block[j] = codewords[pos + j]) ^ block[errorCorrectionBase]) & 0xff;
				for (k = 0; k < coef.length - 1; k++)
					block[errorCorrectionBase
							+ k] = (byte) (gf_mul(coef[k] & 0xff, factor) ^ block[errorCorrectionBase + k + 1]);
				block[errorCorrectionBase + k] = (byte) gf_mul(coef[k] & 0xff, factor);
			}
			pos += len;
		}
		byte[] r = new byte[totalNumberOfCodewords];
		for (int pos = 0, i = 0; i <= lengthOfShortBlock; i++)
			for (int j = 0; j < numberOfErrorCorrectionBlocks; j++)
				if (i != lengthOfShortBlock - numberOfErrorCorrectionCodewordsPerBlock || j >= numberOfShortBlocks)
					r[pos++] = blocks[j][i];
		return r;
	}

	private static byte[] extractCodewords(boolean[] modules, boolean[] funmask, int size, int version, int ecl) {
		int totalNumberOfCodewords = getTotalNumberOfCodewords(version);
		int numberOfErrorCorrectionBlocks = getNumberOfErrorCorrectionBlocks(version, ecl);
		int numberOfErrorCorrectionCodewordsPerBlock = getNumberOfErrorCorrectionCodewordsPerBlock(version, ecl);
		int numberOfShortBlocks = numberOfErrorCorrectionBlocks
				- totalNumberOfCodewords % numberOfErrorCorrectionBlocks;
		int lengthOfShortBlock = totalNumberOfCodewords / numberOfErrorCorrectionBlocks;
		byte[][] blocks = new byte[numberOfErrorCorrectionBlocks][lengthOfShortBlock + 1];
		byte[] codewords = readErrorCorrectionCodewords(modules, funmask, size);
		for (int pos = 0, i = 0; i <= lengthOfShortBlock; i++)
			for (int j = 0; j < numberOfErrorCorrectionBlocks; j++)
				if (i != lengthOfShortBlock - numberOfErrorCorrectionCodewordsPerBlock || j >= numberOfShortBlocks)
					blocks[j][i] = codewords[pos++];
		byte[] r = new byte[totalNumberOfCodewords
				- numberOfErrorCorrectionCodewordsPerBlock * numberOfErrorCorrectionBlocks];
		for (int n = 0, i = 0; i < numberOfErrorCorrectionBlocks; i++) {
			byte[] block = blocks[i];
			int len;
			if (i < numberOfShortBlocks) {
				len = lengthOfShortBlock;
				for (int j = len - numberOfErrorCorrectionCodewordsPerBlock; j < len; j++)
					block[j] = block[j + 1];
			} else {
				len = lengthOfShortBlock + 1;
			}
			if (!rs_correct_msg(block, len, numberOfErrorCorrectionCodewordsPerBlock))
				return null;
			len -= numberOfErrorCorrectionCodewordsPerBlock;
			for (int j = 0; j < len; j++)
				r[n++] = block[j];
		}
		return r;
	}

	private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

	public static QrCode encode(byte[] data, int ecl) {
		int i = 0, version, mode, len = data.length, nbits = 4, lbits = 0, pbits = -1, pbytes;
		for (; i < len && data[i] >= 48 && data[i] <= 57; i++)
			;
		if (i == len) {
			mode = 1;
			nbits += (len / 3) * 10;
			switch (len % 3) {
			case 2:
				nbits += 7;
				break;
			case 1:
				nbits += 4;
			}
		} else {
			for (; i < len && ALPHANUMERIC.indexOf(data[i]) != -1; i++)
				;
			if (i == len) {
				mode = 2;
				nbits += (len / 2) * 11 + (len & 1) * 6;
			} else {
				mode = 4;
				nbits += len * 8;
			}
			mode = i == len ? 2 : 4;
		}
		for (version = 0; pbits < 0 && ++version <= 40; pbits = getBitCapacity(version, ecl) - nbits - lbits) {
			if (version < 10)
				lbits = mode == 1 ? 10 : mode == 2 ? 9 : 8;
			else if (version < 27)
				lbits = mode == 1 ? 12 : mode == 2 ? 11 : 16;
			else
				lbits = mode == 1 ? 14 : mode == 2 ? 13 : 16;
		}
		if (pbits < 0)
			return null;
		BitStream bs = new BitStream();
		bs.append(mode, 4);
		bs.append(len, lbits);
		switch (mode) {
		case 1:
			for (i = 0; i <= len - 3; i += 3)
				bs.append((data[i] - 48) * 100 + (data[i + 1] - 48) * 10 + (data[i + 2] - 48), 10);
			switch (len - i) {
			case 2:
				bs.append((data[i] - 48) * 10 + (data[i + 1] - 48), 7);
				break;
			case 1:
				bs.append((data[i] - 48), 4);
				break;
			}
			break;
		case 2:
			for (i = 0; i <= len - 2; i += 2)
				bs.append(ALPHANUMERIC.indexOf(data[i]) * 45 + ALPHANUMERIC.indexOf(data[i + 1]), 11);
			if (i < len)
				bs.append(ALPHANUMERIC.indexOf(data[i]), 6);
			break;
		default:
			for (byte b : data)
				bs.append(b & 0xff, 8);
		}
		if (pbits >= 4) {
			bs.append(0, 4);
			pbits -= 4;
		}
		pbytes = pbits >> 3;
		pbits &= 7;
		if (pbits != 0)
			bs.append(0, 8 - pbits);
		for (; pbytes >= 2; pbytes -= 2)
			bs.append(0xec11, 16);
		if (pbytes > 0)
			bs.append(0xec, 8);
		data = bs.toByteArray();
		int size = version * 4 + 17;
		boolean[] modules = new boolean[size * size];
		boolean[] funmask = new boolean[size * size];
		initializeVersion(version, modules, funmask, size);
		placeErrorCorrectionCodewords(modules, funmask, size, generateErrorCorrectionCodewords(data, version, ecl));
		int pattern = selectMaskPattern(modules, funmask, size, ecl);
		maskPattern(modules, funmask, size, pattern);
		placeMask(modules, funmask, size, ecl, pattern);
		return new QrCode(modules, size);
	}

	private final boolean[] modules;
	private final int size;

	private QrCode(boolean[] modules, int size) {
		this.modules = modules;
		this.size = size;
	}

	public String toSvgXML() {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append(
				"<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n");
		sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 " + (size + 8) + " "
				+ (size + 8) + "\" stroke=\"none\">\n");
		sb.append("\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n");
		sb.append("\t<path d=\"");
		for (int p = 0, y = 0; y < size; y++)
			for (int x = 0; x < size; x++)
				if (modules[p++])
					sb.append(String.format("M%d,%dh1v1h-1z ", x + 4, y + 4));
		sb.setCharAt(sb.length() - 1, '"');
		sb.append(" fill=\"#000000\"/>\n");
		sb.append("</svg>\n");
		return sb.toString();
	}

	public boolean[] getModules() {
		return modules;
	}

	private static final int X = 0;
	private static final int Y = 1;
	private static final int W = 2;
	private static final int H = 3;

	private static final int TR = 0;
	private static final int TL = 1;
	private static final int BL = 2;

	private static boolean testFinderPattern(int w0, int w1, int w2, int w3, int w4) {
		float d, scale = (w0 + w1 + w2 + w3 + w4) / 14.0f;
		if (scale == 0)
			return false;
		d = w0 / scale;
		if (d < 1 || d > 3)
			return false;
		d = w1 / scale;
		if (d < 1 || d > 3)
			return false;
		d = w2 / scale;
		if (d < 5 || d > 7)
			return false;
		d = w3 / scale;
		if (d < 1 || d > 3)
			return false;
		d = w4 / scale;
		if (d < 1 || d > 3)
			return false;
		return true;
	}

	private static void swapFinder(float[][] finder, int a, int b) {
		float[] tmp = finder[a];
		finder[a] = finder[b];
		finder[b] = tmp;
	}

	private static int scanFinderYCenter(byte[] data, int width, int height, int step, int xc, int yc, int c) {
		int y0, y1, y2, y3, y4, y5;
		for (y2 = yc - step; y2 >= 0 && data[y2 * width + xc] == c; y2 -= step)
			;
		for (y1 = y2 - step; y1 >= 0 && data[y1 * width + xc] != c; y1 -= step)
			;
		for (y0 = y1 - step; y0 >= 0 && data[y0 * width + xc] == c; y0 -= step)
			;
		for (y3 = yc + step; y3 < height && data[y3 * width + xc] == c; y3 += step)
			;
		for (y4 = y3 + step; y4 < height && data[y4 * width + xc] != c; y4 += step)
			;
		for (y5 = y4 + step; y5 < height && data[y5 * width + xc] == c; y5 += step)
			;
		return testFinderPattern(y1 - y0, y2 - y1, y3 - y2 - step, y4 - y3, y5 - y4) ? (y2 + y3) / step / 2 * step : -1;
	}

	private static int scanFinderXCenter(byte[] data, int width, int height, int step, int xc, int yc, int c) {
		int x0, x1, x2, x3, x4, x5;
		for (x2 = xc - step; x2 >= 0 && data[yc * width + x2] == c; x2 -= step)
			;
		for (x1 = x2 - step; x1 >= 0 && data[yc * width + x1] != c; x1 -= step)
			;
		for (x0 = x1 - step; x0 >= 0 && data[yc * width + x0] == c; x0 -= step)
			;
		for (x3 = xc + step; x3 < width && data[yc * width + x3] == c; x3 += step)
			;
		for (x4 = x3 + step; x4 < width && data[yc * width + x4] != c; x4 += step)
			;
		for (x5 = x4 + step; x5 < width && data[yc * width + x5] == c; x5 += step)
			;
		return testFinderPattern(x1 - x0, x2 - x1, x3 - x2 - step, x4 - x3, x5 - x4) ? (x2 + x3) / step / 2 * step : -1;
	}

	private static int scanFinder(byte[] data, int width, int height, int step, float[][] finder) {
		long[] metrics = new long[5];
		int modifies[] = new int[1024], mcount = 0;
		for (int y = 0; y < height; y += step) {
			for (int x = 0, w0 = 0, w1 = 0, w2 = 0, w3 = 0, w4, c; x < width; w0 = w1, w1 = w2, w2 = w3, w3 = w4) {
				for (w4 = -x, c = data[y * width + x]; (x += step) < width && data[y * width + x] == c;)
					;
				if (testFinderPattern(w0, w1, w2, w3, w4 += x)) {
					int x3 = x - w4 - w3, x2 = x3 - w2;
					int xc = (x2 + x3) / step / 2 * step;
					int yc = y;
					yc = scanFinderYCenter(data, width, height, step, xc, yc, c);
					if (yc == -1)
						continue;
					xc = scanFinderXCenter(data, width, height, step, xc, yc, c);
					if (xc == -1)
						continue;
					yc = scanFinderYCenter(data, width, height, step, xc, yc, c);
					if (yc == -1)
						continue;
					int w = 0;
					for (int i = step; w == 0; i += step) {
						for (int j = yc - i; j <= yc + i && w == 0; j += step)
							for (int k = xc - i; k <= xc + i; k += step)
								if (j < 0 || j >= height || k < 0 || k >= height || data[j * width + k] != c) {
									w = i - step;
									break;
								}
					}
					int t, b, l, r;
					for (t = yc - step; t >= yc - w
							&& scanFinderXCenter(data, width, height, step, xc, t, c) != -1; t -= step)
						;
					for (b = yc + step; b <= yc + w
							&& scanFinderXCenter(data, width, height, step, xc, b, c) != -1; b += step)
						;
					for (l = xc - step; l >= xc - w
							&& scanFinderYCenter(data, width, height, step, l, yc, c) != -1; l -= step)
						;
					for (r = xc + step; r <= xc + w
							&& scanFinderYCenter(data, width, height, step, r, yc, c) != -1; r += step)
						;
					long metric = (((long) (b - t) * (r - l)) << 33) | (c == 0 ? 0x100000000l : 0)
							| ((yc & 0xffff) << 16) | (xc & 0xffff);
					for (int i = 0; i < 5; i++) {
						if (metric > metrics[i]) {
							for (int k = 4; k > i; k--)
								metrics[k] = metrics[k - 1];
							metrics[i] = metric;
							break;
						}
					}
					for (int i = yc - w; i <= yc + w; i += step) {
						int pos = i * width + xc;
						data[pos] = (byte) ~data[pos];
						modifies[mcount++] = pos;
						if (mcount == modifies.length)
							modifies = Arrays.copyOf(modifies, modifies.length * 2);
					}
					for (int i = xc - w; i <= xc + w; i += step) {
						int pos = yc * width + i;
						data[pos] = (byte) ~data[pos];
						modifies[mcount++] = pos;
						if (mcount == modifies.length)
							modifies = Arrays.copyOf(modifies, modifies.length * 2);
					}
				}
			}
		}
		for (int i = 0; i < mcount; i++)
			data[modifies[i]] = (byte) ~data[modifies[i]];
		int group0[] = new int[3], group1[] = new int[3], group[], c0 = 0, c1 = 0;
		for (int i = 0; i < 5 && metrics[i] != 0 && c0 < 3 && c1 < 3; i++) {
			if ((metrics[i] & 0x100000000l) != 0)
				group0[c0++] = i;
			else
				group1[c1++] = i;
		}
		if (c0 == 3)
			group = group0;
		else if (c1 == 3)
			group = group1;
		else
			return 0;
		for (int i = 0; i < 3; i++) {
			long v = metrics[group[i]];
			finder[i][X] = (float) (v & 0xffff);
			finder[i][Y] = (float) ((v >> 16) & 0xffff);
		}
		float[][] distance = new float[3][3];
		for (int i = 0; i < 2; i++)
			for (int j = i + 1; j < 3; j++) {
				float dx = finder[i][X] - finder[j][X];
				float dy = finder[i][Y] - finder[j][Y];
				distance[i][j] = dx * dx + dy * dy;
			}
		if (distance[0][1] > distance[0][2] && distance[0][1] > distance[1][2])
			swapFinder(finder, 2, 1);
		else if (distance[1][2] > distance[0][1] && distance[1][2] > distance[0][2])
			swapFinder(finder, 0, 1);
		if ((finder[0][X] - finder[1][X]) * (finder[2][Y] - finder[1][Y]) < (finder[2][X] - finder[1][X])
				* (finder[0][Y] - finder[1][Y]))
			swapFinder(finder, 0, 2);
		return c0 == 3 ? 1 : -1;
	}

	private static boolean scanFinderEdgePixel(float[] p0, float[] p1, float[] p2, byte[] data, boolean reverse,
			int width, int height, int[] r) {
		int x = (int) p2[X];
		int y = (int) p2[Y];
		int dx = (int) (p1[X] - p0[X]);
		int dy = (int) (p1[Y] - p0[Y]);
		int xdir, ydir;
		if (dx < 0) {
			xdir = -1;
			dx = -dx;
		} else {
			xdir = 1;
		}
		if (dy < 0) {
			ydir = -1;
			dy = -dy;
		} else {
			ydir = 1;
		}
		int t;
		if (dx > dy) {
			t = 0;
		} else {
			t = dx;
			dx = dy;
			dy = t;
			t = x;
			x = y;
			y = t;
			t = xdir;
			xdir = ydir;
			ydir = t;
			t = width;
			width = height;
			height = t;
			t = 1;
		}
		int e = -dx;
		int status = 0;
		for (dx <<= 1, dy <<= 1; x >= 0 && x < width && y >= 0 && y < height; x += xdir) {
			boolean dark = (t == 0 ? data[y * width + x] == 0 : data[x * height + y] == 0) ^ reverse;
			switch (status) {
			case 0:
				if (!dark)
					status = 1;
				break;
			case 1:
				if (dark)
					status = 2;
				break;
			case 2:
				if (!dark)
					status = 3;
				break;
			case 3:
				if (t == 0) {
					r[X] = x;
					r[Y] = y;
				} else {
					r[X] = y;
					r[Y] = x;
				}
				return true;
			}
			e += dy;
			if (e > 0) {
				y += ydir;
				e -= dx;
			}
		}
		return false;
	}

	private static int countDarkPixel(int x0, int y0, int x1, int y1, byte[] data, boolean reverse, int width,
			int threshold) {
		int x, y, t, dir;
		int dx = Math.abs(x1 - x0);
		int dy = Math.abs(y1 - y0);
		if (dy > dx) {
			t = dx;
			dx = dy;
			dy = t;
			t = x0;
			x0 = y0;
			y0 = t;
			t = x1;
			x1 = y1;
			y1 = t;
			t = 1;
		} else
			t = 0;
		int e = -dx;
		if (x0 < x1) {
			x = x0;
			y = y0;
			dir = y1 > y0 ? 1 : -1;
		} else {
			x = x1;
			y = y1;
			x1 = x0;
			dir = y0 > y1 ? 1 : -1;
		}
		int c = 0;
		for (dx <<= 1, dy <<= 1; x <= x1; x++) {
			if (t == 0) {
				if ((data[y * width + x] == 0) ^ reverse)
					if (++c >= threshold)
						break;
			} else {
				if ((data[x * width + y] == 0) ^ reverse)
					if (++c >= threshold)
						break;
			}
			e += dy;
			if (e > 0) {
				y += dir;
				e -= dx;
			}
		}
		return c;
	}

	private static boolean scanEdgePixel(float[] p0, float[] p1, float[] p2, int[] p3, byte[] data, boolean reverse,
			int width, int height, int threshold, int[] r) {
		int x = (int) p2[X];
		int y = (int) p2[Y];
		int dx = (int) (p1[X] - p0[X]);
		int dy = (int) (p1[Y] - p0[Y]);
		int xdir, ydir;
		if (dx < 0) {
			xdir = -1;
			dx = -dx;
		} else {
			xdir = 1;
		}
		if (dy < 0) {
			ydir = -1;
			dy = -dy;
		} else {
			ydir = 1;
		}
		int t;
		if (dx > dy) {
			t = 0;
		} else {
			t = dx;
			dx = dy;
			dy = t;
			t = x;
			x = y;
			y = t;
			t = xdir;
			xdir = ydir;
			ydir = t;
			t = width;
			width = height;
			height = t;
			t = 1;
		}
		int e = -dx;
		int c0 = 0;
		int c1 = 0;
		int c2 = Integer.MAX_VALUE >> 1;
		for (dx <<= 1, dy <<= 1; x >= 0 && x < width && y >= 0 && y < height; x += xdir) {
			int count = t == 0 ? countDarkPixel((int) p3[X], (int) p3[Y], x, y, data, reverse, width, threshold)
					: countDarkPixel((int) p3[X], (int) p3[Y], y, x, data, reverse, height, threshold);
			c0 = c1;
			c1 = c2;
			c2 = count;
			if (c0 < threshold && c1 < threshold && c2 < threshold) {
				if (t == 0) {
					r[X] = x;
					r[Y] = y;
				} else {
					r[X] = y;
					r[Y] = x;
				}
				return true;
			}
			e += dy;
			if (e > 0) {
				y += ydir;
				e -= dx;
			}
		}
		return false;
	}

	private static void cross(int[] p0, int[] p1, int[] p2, int[] p3, float[] rp) {
		if (p0[X] == p1[X]) {
			float k1 = ((float) p2[Y] - p3[Y]) / (p2[X] - p3[X]);
			float c1 = p2[Y] - k1 * p2[X];
			rp[X] = p1[X];
			rp[Y] = k1 * p1[X] + c1;
		} else if (p2[X] == p3[X]) {
			float k0 = ((float) p0[Y] - p1[Y]) / (p0[X] - p1[X]);
			float c0 = p0[Y] - k0 * p0[X];
			rp[X] = p2[X];
			rp[Y] = k0 * p2[X] + c0;
		} else {
			float k0 = ((float) p0[Y] - p1[Y]) / (p0[X] - p1[X]);
			float c0 = p0[Y] - k0 * p0[X];
			float k1 = ((float) p2[Y] - p3[Y]) / (p2[X] - p3[X]);
			float c1 = p2[Y] - k1 * p2[X];
			float x = (c1 - c0) / (k0 - k1);
			rp[X] = x;
			rp[Y] = k0 * x + c0;
		}
	}

	private static boolean scanBox(byte[] data, boolean reverse, int width, int height, float[][] finder,
			float[][] box) {
		int[] tll = new int[2];
		if (!scanFinderEdgePixel(finder[TR], finder[TL], finder[TL], data, reverse, width, height, tll))
			return false;
		int[] bll = new int[2];
		if (!scanFinderEdgePixel(finder[TR], finder[TL], finder[BL], data, reverse, width, height, bll))
			return false;
		int[] tlt = new int[2];
		if (!scanFinderEdgePixel(finder[BL], finder[TL], finder[TL], data, reverse, width, height, tlt))
			return false;
		int[] trt = new int[2];
		if (!scanFinderEdgePixel(finder[BL], finder[TL], finder[TR], data, reverse, width, height, trt))
			return false;
		int[] blb = new int[2];
		if (!scanFinderEdgePixel(finder[TL], finder[BL], finder[BL], data, reverse, width, height, blb))
			return false;
		int[] trr = new int[2];
		if (!scanFinderEdgePixel(finder[TL], finder[TR], finder[TR], data, reverse, width, height, trr))
			return false;
		float[] center = new float[] { (finder[TR][X] + finder[BL][X]) / 2, (finder[TR][Y] + finder[BL][Y]) / 2 };
		int[] bmd = new int[2];
		int[] rmd = new int[2];
		float dx = finder[BL][X] - bll[X];
		float dy = finder[BL][Y] - bll[Y];
		int threshold = (int) (Math.sqrt(dx * dx + dy * dy) / 3.5f);
		if (!scanEdgePixel(finder[TL], finder[BL], center, blb, data, reverse, width, height, threshold, bmd))
			return false;
		dx = finder[TR][X] - trt[X];
		dy = finder[TR][Y] - trt[Y];
		threshold = (int) (Math.sqrt(dx * dx + dy * dy) / 3.5f);
		if (!scanEdgePixel(finder[TL], finder[TR], center, trr, data, reverse, width, height, threshold, rmd))
			return false;
		cross(tll, bll, tlt, trt, box[0]);
		if (box[0][X] < 0 || box[0][X] >= width || box[0][Y] < 0 || box[0][Y] >= height)
			return false;
		cross(rmd, trr, tlt, trt, box[1]);
		if (box[1][X] < 0 || box[1][X] >= width || box[1][Y] < 0 || box[1][Y] >= height)
			return false;
		cross(tll, bll, blb, bmd, box[2]);
		if (box[2][X] < 0 || box[2][X] >= width || box[2][Y] < 0 || box[2][Y] >= height)
			return false;
		cross(blb, bmd, rmd, trr, box[3]);
		if (box[3][X] < 0 || box[3][X] >= width || box[3][Y] < 0 || box[3][Y] >= height)
			return false;
		return true;
	}

	private static boolean adjustFinder(ViewPort vp, float[] finder) {
		float x = finder[X];
		float y = finder[Y];
		float size = vp.size;
		int w0 = 0, w1 = 0, w2 = 0, w3 = 0, w4 = 0;
		for (; x >= 0 && vp.test(x, y); x--)
			w2++;
		for (; x >= 0 && !vp.test(x, y); x--)
			w1++;
		for (; x >= 0 && vp.test(x, y); x--)
			w0++;
		float xl = x;
		x = finder[X];
		for (; x < size && vp.test(x, y); x++)
			w2++;
		for (; x < size && !vp.test(x, y); x++)
			w3++;
		for (; x < size && vp.test(x, y); x++)
			w4++;
		if (!testFinderPattern(w0, w1, w2 - 1, w3, w4))
			return false;
		float xr = x;
		x = finder[X];
		w0 = w1 = w2 = w3 = w4 = 0;
		for (; y >= 0 && vp.test(x, y); y--)
			w2++;
		for (; y >= 0 && !vp.test(x, y); y--)
			w1++;
		for (; y >= 0 && vp.test(x, y); y--)
			w0++;
		float yt = y;
		y = finder[Y];
		for (; y < size && vp.test(x, y); y++)
			w2++;
		for (; y < size && !vp.test(x, y); y++)
			w3++;
		for (; y < size && vp.test(x, y); y++)
			w4++;
		if (!testFinderPattern(w0, w1, w2 - 1, w3, w4))
			return false;
		float yb = y;
		finder[X] = (xl + xr) / 2;
		finder[Y] = (yt + yb) / 2;
		finder[H] = (xr - xl - 1);
		finder[W] = (yb - yt - 1);
		return true;
	}

	private static class ViewPort {
		private final byte[] data;
		private final boolean reverse;
		private final int width;
		private final int height;
		private final float size;
		private final float[] coef;
		private boolean mirror = false;

		private ViewPort(byte[] data, boolean reverse, int width, int height, float size, float[] coef) {
			this.data = data;
			this.reverse = reverse;
			this.width = width;
			this.height = height;
			this.size = size;
			this.coef = coef;
		}

		public boolean test(float x, float y) {
			float[] point = mirror ? new float[] { y, x } : new float[] { x, y };
			transform(coef, point, 2);
			int tx = (int) point[X];
			int ty = (int) point[Y];
			return tx < 0 || tx >= width || ty < 0 || ty >= height ? false : (data[ty * width + tx] == 0) ^ reverse;
		}
	}

	private static ViewPort createViewPort(byte[] data, boolean reverse, int width, int height, float[][] box,
			float[][] finder) {
		float maxx = Math.max(Math.max(box[0][X], box[1][X]), Math.max(box[2][X], box[3][X]));
		float minx = Math.min(Math.min(box[0][X], box[1][X]), Math.min(box[2][X], box[3][X]));
		float maxy = Math.max(Math.max(box[0][Y], box[1][Y]), Math.max(box[2][Y], box[3][Y]));
		float miny = Math.min(Math.min(box[0][Y], box[1][Y]), Math.min(box[2][Y], box[3][Y]));
		float size = Math.max(maxx - minx, maxy - miny);
		float[] coef = new float[9];
		quadrilateralToQuadrilateral(box[0][X], box[0][Y], box[1][X], box[1][Y], box[2][X], box[2][Y], box[3][X],
				box[3][Y], 0, 0, size, 0, 0, size, size, size, coef);
		transform(coef, finder[TL], 2);
		if (finder[TL][X] <= 0 || finder[TL][X] >= size || finder[TL][Y] <= 0 || finder[TL][Y] >= size)
			return null;
		transform(coef, finder[TR], 2);
		if (finder[TR][X] <= 0 || finder[TR][X] >= size || finder[TR][Y] <= 0 || finder[TR][Y] >= size)
			return null;
		transform(coef, finder[BL], 2);
		if (finder[BL][X] <= 0 || finder[BL][X] >= size || finder[BL][Y] <= 0 || finder[BL][Y] >= size)
			return null;
		quadrilateralToQuadrilateral(0, 0, size, 0, 0, size, size, size, box[0][X], box[0][Y], box[1][X], box[1][Y],
				box[2][X], box[2][Y], box[3][X], box[3][Y], coef);
		ViewPort vp = new ViewPort(data, reverse, width, height, size, coef);
		if (!adjustFinder(vp, finder[TL]) || !adjustFinder(vp, finder[TR]) || !adjustFinder(vp, finder[BL]))
			return null;
		return vp;
	}

	private static boolean sample(ViewPort vp, float x, float y) {
		int dark = 0;
		for (int i = -1; i <= 1; i++)
			for (int j = -1; j <= 1; j++)
				if (vp.test(x + j, y + i))
					dark++;
		return dark > 4;
	}

	private static final int[] ECC_VERSION = new int[] { 0x7c94, 0x85bc, 0x9a99, 0xa4d3, 0xbbf6, 0xc762, 0xd847, 0xe60d,
			0xf928, 0x10b78, 0x1145d, 0x12a17, 0x13532, 0x149a6, 0x15683, 0x168c9, 0x177ec, 0x18ec4, 0x191e1, 0x1afab,
			0x1b08e, 0x1cc1a, 0x1d33f, 0x1ed75, 0x1f250, 0x209d5, 0x216f0, 0x228ba, 0x2379f, 0x24b0b, 0x2542e, 0x26a64,
			0x27541, 0x28c69, };

	private static int decodeVersion(int encodedVersion) {
		for (int i = 0; i < ECC_VERSION.length; i++)
			if (Integer.bitCount(ECC_VERSION[i] ^ encodedVersion) <= 3)
				return i + 7;
		return -1;
	}

	private static int scanVersion(ViewPort vp, float[][] finder) {
		float scale = (finder[TL][W] + finder[TR][W]) / 14.0f;
		int version = Math.round(((finder[TR][X] - finder[TL][X]) / scale - 10)) / 4;
		if (version < 7)
			return version;
		scale = finder[TR][W] / 7.0f;
		float bx = finder[TR][X] - scale * 7;
		float by = finder[TR][Y] - scale * 3;
		version = 0;
		for (int i = 0; i < 6; i++, by += scale)
			for (int j = 0; j < 3; j++)
				if (sample(vp, bx + scale * j, by))
					version |= (1 << (i * 3 + j));
		if ((version = decodeVersion(version)) != -1)
			return version;
		scale = finder[BL][H] / 7.0f;
		bx = finder[BL][X] - scale * 3;
		by = finder[BL][Y] - scale * 7;
		version = 0;
		for (int i = 0; i < 6; i++, bx += scale)
			for (int j = 0; j < 3; j++)
				if (sample(vp, bx, by + scale * j))
					version |= (1 << (i * 3 + j));
		return decodeVersion(version);
	}

	private static final int[] ECC_FORMAT = new int[] { 0x5412, 0x5125, 0x5e7c, 0x5b4b, 0x45f9, 0x40ce, 0x4f97, 0x4aa0,
			0x77f4, 0x72f3, 0x7daa, 0x789d, 0x662f, 0x6318, 0x6c41, 0x6976, 0x1689, 0x13be, 0x1ce7, 0x19d0, 0x0762,
			0x0255, 0x0d0c, 0x083b, 0x355f, 0x3068, 0x3f31, 0x3a06, 0x24b4, 0x2183, 0x2eda, 0x2bed };

	private static int decodeFormat(int encodedFormat) {
		for (int i = 0; i < ECC_FORMAT.length; i++)
			if (Integer.bitCount(ECC_FORMAT[i] ^ encodedFormat) <= 3)
				return i;
		return -1;
	}

	private static int scanFormat(ViewPort vp, float[][] finder) {
		float scale = finder[TL][W] / 7.0f;
		float bx = finder[TL][X] + scale * 5;
		float by = finder[TL][Y] + scale * 5;
		int format = 0;
		if (sample(vp, bx, by))
			format |= 1 << 7;
		if (sample(vp, bx, by - scale))
			format |= 1 << 6;
		if (sample(vp, bx - scale, by))
			format |= 1 << 8;
		for (int i = 3; i < 9; i++) {
			if (sample(vp, bx, by - scale * i))
				format |= 1 << (8 - i);
			if (sample(vp, bx - scale * i, by))
				format |= 1 << (6 + i);
		}
		if ((format = decodeFormat(format)) != -1)
			return format;
		scale = finder[TR][H] / 7.0f;
		bx = finder[TR][X] + scale * 3;
		by = finder[TR][Y] + scale * 5;
		format = 0;
		for (int i = 0; i < 8; i++, bx -= scale)
			if (sample(vp, bx, by))
				format |= 1 << i;
		scale = finder[BL][W] / 7.0f;
		bx = finder[BL][X] + scale * 5;
		by = finder[BL][Y] - scale * 3;
		for (int i = 0; i < 7; i++, by += scale)
			if (sample(vp, bx, by))
				format |= 1 << (8 + i);
		return decodeFormat(format);
	}

	private static boolean testAlignmentPattern(ViewPort vp, float x, float y, float e, float[] res) {
		float scale = e * 2;
		if (!vp.test(x, y) || x <= scale || x >= vp.size - scale || y <= scale || y >= vp.size - scale)
			return false;
		int c;
		float l = x - 1;
		for (c = (int) scale; vp.test(l, y); l--)
			if (--c == 0)
				return false;
		for (c = (int) scale; !vp.test(l, y); l--)
			if (--c == 0)
				return false;
		float r = x + 1;
		for (c = (int) scale; vp.test(r, y); r++)
			if (--c == 0)
				return false;
		for (c = (int) scale; !vp.test(r, y); r++)
			if (--c == 0)
				return false;
		float t = y - 1;
		for (c = (int) scale; vp.test(x, t); t--)
			if (--c == 0)
				return false;
		for (c = (int) scale; !vp.test(x, t); t--)
			if (--c == 0)
				return false;
		float b = y + 1;
		for (c = (int) scale; vp.test(x, b); b++)
			if (--c == 0)
				return false;
		for (c = (int) scale; !vp.test(x, b); b++)
			if (--c == 0)
				return false;
		float w = r - l - 1;
		float h = b - t - 1;
		if (w * 2 > Math.min(r - x, x - l) * 5)
			return false;
		if (h * 2 > Math.min(b - y, y - t) * 5)
			return false;
		float el = e * 2;
		float eh = e * 4;
		if (w < el || w > eh || h < el || h > eh)
			return false;
		res[X] = (l + r) / 2;
		res[Y] = (t + b) / 2;
		return true;
	}

	private static boolean scanAlignmentPattern(ViewPort vp, float x, float y, float e, float[] errata, float[] r) {
		x += errata[X];
		y += errata[Y];
		if (testAlignmentPattern(vp, x, y, e, r))
			return true;
		for (int k = 1; k <= e; k++)
			for (int l = 1; l <= e; l++) {
				if (testAlignmentPattern(vp, x + k, y + l, e, r)) {
					errata[X] += k;
					errata[Y] += l;
					return true;
				}
				if (testAlignmentPattern(vp, x + k, y - l, e, r)) {
					errata[X] += k;
					errata[Y] += -l;
					return true;
				}
				if (testAlignmentPattern(vp, x - k, y + l, e, r)) {
					errata[X] += -k;
					errata[Y] += l;
					return true;
				}
				if (testAlignmentPattern(vp, x - k, y - l, e, r)) {
					errata[X] += -k;
					errata[Y] += -l;
					return true;
				}
			}
		return false;
	}

	private static boolean scanAlignments(ViewPort vp, float[][] finder, int[] positions, float[][][] alignments) {
		float scale = finder[TL][W] / 7.0f;
		alignments[0][0][X] = finder[TL][X] + scale * 3;
		alignments[0][0][Y] = finder[TL][Y] + scale * 3;
		scale = finder[TR][W] / 7.0f;
		alignments[0][positions.length - 1][X] = finder[TR][X] - scale * 3;
		alignments[0][positions.length - 1][Y] = finder[TR][Y] + scale * 3;
		float errata[] = new float[2];
		for (int i = 1; i < positions.length - 1; i++) {
			float e = scale;
			float x = alignments[0][i - 1][X] + scale * (positions[i] - positions[i - 1]);
			float y = alignments[0][i - 1][Y];
			if (!scanAlignmentPattern(vp, x, y, e, errata, alignments[0][i]))
				return false;
		}
		scale = finder[BL][H] / 7.0f;
		alignments[positions.length - 1][0][X] = finder[BL][X] + scale * 3;
		alignments[positions.length - 1][0][Y] = finder[BL][Y] - scale * 3;
		errata[0] = errata[1] = 0;
		for (int i = 1; i < positions.length - 1; i++) {
			float e = scale;
			float x = alignments[0][0][X];
			float y = alignments[i - 1][0][Y] + scale * (positions[i] - positions[i - 1]);
			if (!scanAlignmentPattern(vp, x, y, e, errata, alignments[i][0]))
				return false;
		}
		errata[0] = errata[1] = 0;
		float max = Math.max(Math.max(finder[TL][W], finder[TL][H]), Math.max(finder[TR][W], finder[TR][H]));
		max = Math.max(max, Math.max(finder[BL][W], finder[BL][H])) / 7.0f;
		for (int i = 1; i < positions.length; i++) {
			for (int j = 1; j < positions.length; j++) {
				float e = max;
				float x = alignments[i][j - 1][X] - alignments[i - 1][j - 1][X] + alignments[i - 1][j][X];
				float y = alignments[i][j - 1][Y] - alignments[i - 1][j - 1][Y] + alignments[i - 1][j][Y];
				if (!scanAlignmentPattern(vp, x, y, e, errata, alignments[i][j])) {
					if (i != positions.length / 2 || j != positions.length / 2)
						return false;
					alignments[i][j][X] = x;
					alignments[i][j][Y] = y;
				}
			}
		}
		return true;
	}

	private static void scanDataV1(ViewPort vp, float[][] finder, boolean[] modules, boolean[] funmask, int size) {
		float scale = finder[TL][W] / 7.0f;
		float xl = finder[TL][X] + scale * 3;
		float yl = finder[TL][Y] + scale * 3;
		for (; vp.test(xl, yl); xl++)
			;
		scale = finder[TR][W] / 7.0f;
		float xr = finder[TR][X] - scale * 3;
		float yr = finder[TR][Y] + scale * 3;
		for (; vp.test(xr, yr); xr--)
			;
		float hscale = (float) (Math.sqrt((xr - xl) * (xr - xl) + (yr - yl) * (yr - yl)) / 7.0f);
		scale = finder[TL][H] / 7.0f;
		float xt = finder[TL][X] + scale * 3;
		float yt = finder[TL][Y] + scale * 3;
		for (; vp.test(xt, yt); yt++)
			;
		scale = finder[BL][H] / 7.0f;
		float xb = finder[BL][X] + scale * 3;
		float yb = finder[BL][Y] - scale * 3;
		for (; vp.test(xb, yb); yb--)
			;
		float vscale = (float) (Math.sqrt((xb - xt) * (xb - xt) + (yb - yt) * (yb - yt)) / 7.0f);
		float xbase = xl - hscale * 6.5f;
		float ybase = yt - vscale * 6.5f;
		for (int p = 0, i = 0; i < size; i++)
			for (int j = 0; j < size; j++, p++)
				if (!funmask[p])
					modules[p] = sample(vp, xbase + hscale * j, ybase + vscale * i);
	}

	private static void scanData(ViewPort vp, float[][] finder, int[] positions, float[][][] alignments,
			boolean[] modules, boolean[] funmask, int size) {
		for (int i = 0; i < positions.length - 1; i++) {
			for (int j = 0; j < positions.length - 1; j++) {
				int ys = positions[i];
				int ye = positions[i + 1];
				int xs = positions[j];
				int xe = positions[j + 1];
				int hcount = xe - xs;
				int vcount = ye - ys;
				float xbase = alignments[i][j][X];
				float ybase = alignments[i][j][Y];
				float dx = xbase - alignments[i][j + 1][X];
				float dy = ybase - alignments[i][j + 1][Y];
				float scale = (float) Math.sqrt(dx * dx + dy * dy) / hcount;
				for (int k = 1; k <= vcount; k++)
					for (int l = 1; l <= hcount; l++) {
						int pos = (ys + k) * size + xs + l;
						if (!funmask[pos])
							modules[pos] = sample(vp, xbase + scale * l, ybase + scale * k);
					}
			}
			{
				int ye = positions[0];
				int xs = positions[i];
				int xe = positions[i + 1];
				int hcount = xe - xs;
				int vcount = positions[0];
				float xbase = alignments[0][i][X];
				float ybase = alignments[0][i][Y];
				float dx = xbase - alignments[0][i + 1][X];
				float dy = ybase - alignments[0][i + 1][Y];
				float scale = (float) Math.sqrt(dx * dx + dy * dy) / hcount;
				for (int k = 1; k <= vcount; k++)
					for (int l = 1; l <= hcount; l++) {
						int pos = (ye - k) * size + xs + l;
						if (!funmask[pos])
							modules[pos] = sample(vp, xbase + scale * l, ybase - scale * k);
					}
			}
			{
				int ys = positions[positions.length - 1];
				int xs = positions[i];
				int xe = positions[i + 1];
				int hcount = xe - xs;
				int vcount = positions[0];
				float xbase = alignments[positions.length - 1][i][X];
				float ybase = alignments[positions.length - 1][i][Y];
				float dx = xbase - alignments[positions.length - 1][i + 1][X];
				float dy = ybase - alignments[positions.length - 1][i + 1][Y];
				float scale = (float) Math.sqrt(dx * dx + dy * dy) / hcount;
				for (int k = 1; k <= vcount; k++)
					for (int l = 1; l <= hcount; l++) {
						int pos = (ys + k) * size + xs + l;
						if (!funmask[pos])
							modules[pos] = sample(vp, xbase + scale * l, ybase + scale * k);
					}
			}
			{
				int ys = positions[i];
				int ye = positions[i + 1];
				int xe = positions[0];
				int vcount = ye - ys;
				int hcount = positions[0];
				float xbase = alignments[i][0][X];
				float ybase = alignments[i][0][Y];
				float dx = xbase - alignments[i + 1][0][X];
				float dy = ybase - alignments[i + 1][0][Y];
				float scale = (float) Math.sqrt(dx * dx + dy * dy) / vcount;
				for (int k = 1; k <= vcount; k++)
					for (int l = 1; l <= hcount; l++) {
						int pos = (ys + k) * size + xe - l;
						if (!funmask[pos])
							modules[pos] = sample(vp, xbase - scale * l, ybase + scale * k);
					}
			}
			{
				int ys = positions[i];
				int ye = positions[i + 1];
				int xs = positions[positions.length - 1];
				int vcount = ye - ys;
				int hcount = positions[0];
				float xbase = alignments[i][positions.length - 1][X];
				float ybase = alignments[i][positions.length - 1][Y];
				float dx = xbase - alignments[i + 1][positions.length - 1][X];
				float dy = ybase - alignments[i + 1][positions.length - 1][Y];
				float scale = (float) Math.sqrt(dx * dx + dy * dy) / vcount;
				for (int k = 1; k <= vcount; k++)
					for (int l = 1; l <= hcount; l++) {
						int pos = (ys + k) * size + xs + l;
						if (!funmask[pos])
							modules[pos] = sample(vp, xbase + scale * l, ybase + scale * k);
					}
			}
		}
		{
			{
				int ys = positions[positions.length - 1];
				int xs = ys;
				int hcount = positions[0];
				int vcount = hcount;
				float xbase = alignments[positions.length - 1][positions.length - 1][X];
				float ybase = alignments[positions.length - 1][positions.length - 1][Y];
				float dx = xbase - alignments[positions.length - 1][positions.length - 2][X];
				float dy = ybase - alignments[positions.length - 1][positions.length - 2][Y];
				float scale = (float) Math.sqrt(dx * dx + dy * dy)
						/ (positions[positions.length - 1] - positions[positions.length - 2]);
				for (int k = 1; k <= vcount; k++)
					for (int l = 1; l <= hcount; l++) {
						int pos = (ys + k) * size + xs + l;
						if (!funmask[pos])
							modules[pos] = sample(vp, xbase + scale * l, ybase + scale * k);
					}
			}
		}
	}

	public static class Info {
		public enum Status {
			SCAN_OK, ERR_POSITIONING, ERR_VERSION_INFO, ERR_ALIGNMENTS, ERR_FORMAT_INFO, ERR_UNRECOVERABLE_CODEWORDS, UNSUPPORTED_ENCODE_MODE
		}

		private Status status;
		private boolean reverse;
		private boolean mirror;
		private int version;
		private int format;
		private byte[] data;

		private Info() {
		}

		private Info setStatus(Status status) {
			this.status = status;
			return this;
		}

		public Status getStatus() {
			return status;
		}

		public boolean getReverse() {
			return reverse;
		}

		public boolean getMirror() {
			return mirror;
		}

		public int getVersion() {
			return version;
		}

		public int getEcl() {
			return format >> 3;
		}

		public int getMask() {
			return format & 7;
		}

		public byte[] getData() {
			return data;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("[status=").append(status).append(",");
			sb.append("reverse=").append(reverse).append(",");
			sb.append("mirror=").append(mirror).append(",");
			sb.append("version=").append(version).append(",");
			sb.append("ecl:");
			switch (format >> 3) {
			case ECL_M:
				sb.append("M");
				break;
			case ECL_L:
				sb.append("L");
				break;
			case ECL_H:
				sb.append("H");
				break;
			case ECL_Q:
				sb.append("Q");
				break;
			}
			sb.append(",");
			sb.append("mask=").append(format & 7);
			sb.append(",data=");
			sb.append(data == null ? "null" : new String(data, Charset.forName("UTF-8")));
			return sb.append("]").toString();
		}
	}

	private static BitStream eciTerminate(BitStream src, int eci) {
		BitStream dst = new BitStream();
		dst.append('J', 8);
		dst.append('Q', 8);
		dst.append('2', 8);
		dst.append('\\', 8);
		dst.append(eci % 1000000 / 100000 + 48, 8);
		dst.append(eci % 100000 / 10000 + 48, 8);
		dst.append(eci % 10000 / 1000 + 48, 8);
		dst.append(eci % 1000 / 100 + 48, 8);
		dst.append(eci % 100 / 10 + 48, 8);
		dst.append(eci % 10 / 1 + 48, 8);
		for (byte b : src.toByteArray()) {
			if (b == '\\')
				dst.append('\\', 8);
			dst.append(b & 0xff, 8);
		}
		return dst;
	}

	private static BitStream fnc1Start(BitStream dst, long appid, int eciadd) {
		dst.append('J', 8);
		dst.append('Q', 8);
		if (appid > 0) {
			dst.append('5' + eciadd, 8);
			if (appid < 100) {
				dst.append(appid / 10 + 48, 8);
				dst.append(appid % 10 + 48, 8);
			} else {
				dst.append(appid - 100, 8);
			}
		} else {
			dst.append('3' + eciadd, 8);
		}
		return dst;
	}

	private static byte[] decodeCodewords(byte[] codewords, int version) {
		BitStream src = BitStream.fromByteArray(codewords);
		BitStream dst = new BitStream();
		BitStream fnc1 = null;
		int eci = -1;
		while (true) {
			int nchar, mode = (int) src.get(4);
			switch (mode) {
			case 0:
				if (eci != -1)
					dst = eciTerminate(dst, eci);
				if (fnc1 != null) {
					boolean gs = false;
					for (byte b : dst.toByteArray()) {
						if (gs) {
							if (b != '%')
								fnc1.append(0x1d, 8);
							fnc1.append(b, 8);
							gs = false;
						} else {
							if (b == '%')
								gs = true;
							else
								fnc1.append(b, 8);
						}
					}
					dst = fnc1;
				}
				return dst.toByteArray();
			case 1:
				nchar = (int) src.get(version < 10 ? 10 : version < 27 ? 12 : 14);
				for (; nchar >= 3; nchar -= 3) {
					long v = src.get(10);
					dst.append(v / 100 + 48, 8);
					dst.append(v % 100 / 10 + 48, 8);
					dst.append(v % 10 + 48, 8);
				}
				if (nchar == 2) {
					long v = src.get(7);
					dst.append(v / 10 + 48, 8);
					dst.append(v % 10 + 48, 8);
				} else if (nchar == 1) {
					long v = src.get(4);
					dst.append(v % 10 + 48, 8);
				}
				break;
			case 2:
				nchar = (int) src.get(version < 10 ? 9 : version < 27 ? 11 : 13);
				for (; nchar >= 2; nchar -= 2) {
					int v = (int) src.get(11);
					dst.append(ALPHANUMERIC.charAt(v / 45), 8);
					dst.append(ALPHANUMERIC.charAt(v % 45), 8);
				}
				if (nchar == 1) {
					int v = (int) src.get(6);
					dst.append(ALPHANUMERIC.charAt(v % 45), 8);
				}
				break;
			case 4:
				nchar = (int) src.get(version < 10 ? 8 : 16);
				for (int i = 0; i < nchar; i++)
					dst.append(src.get(8) & 0xff, 8);
				break;
			case 5:
				if (fnc1 == null) {
					fnc1 = fnc1Start(dst, -1, eci == -1 ? 0 : 1);
					dst = new BitStream();
				}
				break;
			case 7:
				if (eci != -1)
					dst = eciTerminate(dst, eci);
				eci = (int) src.get(8);
				if ((eci & 0x80) == 0)
					;
				else if ((eci & 0xc0) == 0x80)
					eci = (int) (((eci & 0x3f) << 8) | src.get(8));
				else if ((eci & 0xe0) == 0xc0)
					eci = (int) (((eci & 0x1f) << 16) | src.get(16));
				else
					return null;
				break;
			case 8:
				nchar = (int) src.get(version < 10 ? 8 : version < 27 ? 10 : 12);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				for (int i = 0; i < nchar; i++) {
					int v = (int) src.get(13);
					v = ((v / 0xc0) << 8) | (v % 0xc0);
					v = v + (v >= 0x1f00 ? 0xc140 : 0x8140);
					os.write(v >> 8);
					os.write(v & 0xff);
				}
				try {
					for (byte b : new String(os.toByteArray(), "SJIS").replaceAll("\ufffd.{1}", "\u30fb")
							.getBytes("UTF-8"))
						dst.append(b & 0xff, 8);
				} catch (Exception e) {
					return null;
				}
				break;
			case 9:
				if (fnc1 == null) {
					fnc1 = fnc1Start(dst, src.get(8), eci == -1 ? 0 : 1);
					dst = new BitStream();
				}
				break;
			default:
				return null;
			}
		}
	}

	public static Info decode(byte[] image_1bit, int width, int height, int sample_granularity) {
		Info info = new Info();
		float[][] finder = new float[3][4];
		float[][] box = new float[4][2];
		switch (scanFinder(image_1bit, width, height, sample_granularity < 1 ? 1 : sample_granularity, finder)) {
		case -1:
			info.reverse = true;
			break;
		case 0:
			return info.setStatus(Info.Status.ERR_POSITIONING);
		case 1:
			info.reverse = false;
		}
		if (!scanBox(image_1bit, info.reverse, width, height, finder, box))
			return info.setStatus(Info.Status.ERR_POSITIONING);
		ViewPort vp = createViewPort(image_1bit, info.reverse, width, height, box, finder);
		if (vp == null)
			return info.setStatus(Info.Status.ERR_POSITIONING);
		int version = info.version = scanVersion(vp, finder);
		if (version < 1)
			return info.setStatus(Info.Status.ERR_VERSION_INFO);
		int size = 4 * version + 17;
		boolean[] modules = new boolean[size * size];
		boolean[] funmask = new boolean[size * size];
		initializeVersion(version, modules, funmask, size);
		if (version > 1) {
			int n = version / 7 + 2;
			int s = version == 32 ? 26 : (version * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
			int[] r = new int[n];
			for (int i = n, p = version * 4 + 10; --i > 0; p -= s)
				r[i] = p;
			r[0] = 6;
			float[][][] alignments = new float[r.length][r.length][2];
			if (!scanAlignments(vp, finder, r, alignments))
				return info.setStatus(Info.Status.ERR_ALIGNMENTS);
			scanData(vp, finder, r, alignments, modules, funmask, size);
		} else {
			scanDataV1(vp, finder, modules, funmask, size);
		}
		int format;
		byte[] codewords;
		for (;; vp.mirror = true) {
			if ((format = scanFormat(vp, finder)) == -1) {
				if (vp.mirror)
					return info.setStatus(Info.Status.ERR_FORMAT_INFO);
			} else {
				int ecl = format >> 3;
				int mask = format & 7;
				maskPattern(modules, funmask, size, mask);
				if ((codewords = extractCodewords(modules, funmask, size, version, ecl)) != null)
					break;
				if (vp.mirror)
					return info.setStatus(Info.Status.ERR_UNRECOVERABLE_CODEWORDS);
				maskPattern(modules, funmask, size, mask);
			}
			for (int i = 0; i < size - 1; i++)
				for (int j = i + 1; j < size; j++) {
					int a = i * size + j;
					int b = j * size + i;
					boolean t = modules[a];
					modules[a] = modules[b];
					modules[b] = t;
				}
		}
		info.format = format;
		info.mirror = vp.mirror;
		byte[] data = decodeCodewords(codewords, info.version);
		if (data == null) {
			info.data = codewords;
			return info.setStatus(Info.Status.UNSUPPORTED_ENCODE_MODE);
		}
		info.data = data;
		return info.setStatus(Info.Status.SCAN_OK);
	}
}
