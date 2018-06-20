package limax.codec;

import java.util.Arrays;

public final class QrCode {
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
		private long[] data = new long[16];
		private int nbits;

		public byte[] toByteArray() {
			byte[] r = new byte[(nbits >> 3) + ((nbits & 7) != 0 ? 1 : 0)];
			int n = nbits >> 6;
			int j = 0;
			for (int i = 0; i < n; i++) {
				r[j++] = (byte) (data[i] >> 56);
				r[j++] = (byte) (data[i] >> 48);
				r[j++] = (byte) (data[i] >> 40);
				r[j++] = (byte) (data[i] >> 32);
				r[j++] = (byte) (data[i] >> 24);
				r[j++] = (byte) (data[i] >> 16);
				r[j++] = (byte) (data[i] >> 8);
				r[j++] = (byte) data[i];
			}
			int m = nbits & 63;
			m = (m >> 3) + ((m & 7) != 0 ? 1 : 0);
			for (int i = 0; i < m; i++)
				r[j++] = (byte) (data[n] >> (56 - (i << 3)));
			return r;
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

		public int bitLength() {
			return nbits;
		}
	}

	private static boolean testBit(int x, int i) {
		return (x & (1 << i)) != 0;
	}

	private static void initializeVersion(int version, boolean[][] modules, boolean[][] funmask, int size) {
		int n = size - 7;
		for (int i = 0, j = size - 1; i < 7; i++) {
			int k = n + i;
			modules[0][i] = modules[6][i] = modules[i][0] = modules[i][6] = true;
			modules[n][i] = modules[j][i] = modules[i][n] = modules[i][j] = true;
			modules[0][k] = modules[6][k] = modules[k][0] = modules[k][6] = true;
		}
		for (int i = 2; i < 5; i++)
			for (int j = 2; j < 5; j++)
				modules[j][i] = modules[n + j][i] = modules[j][n + i] = true;
		n--;
		for (int i = 0; i < 8; i++)
			for (int j = 0; j < 8; j++)
				funmask[j][i] = funmask[n + j][i] = funmask[j][n + i] = true;
		for (int i = 8; i < n; i++) {
			modules[6][i] = modules[i][6] = (i & 1) == 0;
			funmask[6][i] = funmask[i][6] = true;
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
							funmask[y + j][x + i] = true;
						modules[y - 2][x
								+ i] = modules[y + 2][x + i] = modules[y + i][x - 2] = modules[y + i][x + 2] = true;
					}
					modules[y][x] = true;
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
				funmask[y][x] = funmask[x][y] = true;
				if (testBit(v, i))
					modules[y][x] = modules[x][y] = true;
			}
		}
		for (int i = 0; i < 9; i++)
			funmask[i][8] = funmask[8][i] = true;
		for (int i = 0; i < 8; i++)
			funmask[8][size - i - 1] = funmask[size - 8 + i][8] = true;
		modules[size - 8][8] = true;
	}

	private static int selectMaskPattern(boolean[][] modules, boolean[][] funmask, int size, int ecl) {
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

	private static void maskPattern(boolean[][] modules, boolean[][] funmask, int size, int pattern) {
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				if (!funmask[i][j])
					switch (pattern) {
					case 0:
						modules[i][j] ^= (i + j) % 2 == 0;
						break;
					case 1:
						modules[i][j] ^= i % 2 == 0;
						break;
					case 2:
						modules[i][j] ^= j % 3 == 0;
						break;
					case 3:
						modules[i][j] ^= (i + j) % 3 == 0;
						break;
					case 4:
						modules[i][j] ^= (i / 2 + j / 3) % 2 == 0;
						break;
					case 5:
						modules[i][j] ^= i * j % 2 + i * j % 3 == 0;
						break;
					case 6:
						modules[i][j] ^= (i * j % 2 + i * j % 3) % 2 == 0;
						break;
					case 7:
						modules[i][j] ^= ((i + j) % 2 + i * j % 3) % 2 == 0;
						break;
					}
			}
		}
	}

	private static int computePenaltyScore(boolean[][] modules, int size) {
		int score = 0;
		int black = 0;
		for (int i = 0; i < size; i++) {
			boolean xcolor = modules[i][0];
			boolean ycolor = modules[0][i];
			int xsame = 1;
			int ysame = 1;
			int xbits = modules[i][0] ? 1 : 0;
			int ybits = modules[0][i] ? 1 : 0;
			black += modules[i][0] ? 1 : 0;
			for (int j = 1; j < size; j++) {
				if (modules[i][j] != xcolor) {
					xcolor = modules[i][j];
					xsame = 1;
				} else {
					if (++xsame == 5)
						score += 3;
					else if (xsame > 5)
						score++;
				}
				if (modules[j][i] != ycolor) {
					ycolor = modules[j][i];
					ysame = 1;
				} else {
					if (++ysame == 5)
						score += 3;
					else if (ysame > 5)
						score++;
				}
				xbits = ((xbits << 1) & 0x7ff) | (modules[i][j] ? 1 : 0);
				ybits = ((ybits << 1) & 0x7ff) | (modules[j][i] ? 1 : 0);
				if (j >= 10) {
					if (xbits == 0x5d || xbits == 0x5d0)
						score += 40;
					if (ybits == 0x5d || ybits == 0x5d0)
						score += 40;
				}
				black += modules[i][j] ? 1 : 0;
			}
		}
		for (int i = 0; i < size - 1; i++)
			for (int j = 0; j < size - 1; j++) {
				boolean c = modules[i][j];
				if (c == modules[i][j + 1] && c == modules[i + 1][j] && c == modules[i + 1][j + 1])
					score += 3;
			}
		black *= 20;
		for (int k = 0, total = size * size; black < total * (9 - k) || black > total * (11 + k); k++)
			score += 10;
		return score;
	}

	private static void placeErrorCorrectionCodewords(boolean[][] modules, boolean[][] funmask, int size,
			byte[] errorCorrectionCodewords) {
		for (int i = 0, bitLength = errorCorrectionCodewords.length << 3, x = size - 1, y = size
				- 1, dir = -1; x >= 1; x -= 2, y += (dir = -dir)) {
			if (x == 6)
				x = 5;
			for (; y >= 0 && y < size; y += dir)
				for (int j = 0; j < 2; j++)
					if (!funmask[y][x - j] && i < bitLength) {
						modules[y][x - j] = testBit(errorCorrectionCodewords[i >> 3], 7 - (i & 7));
						i++;
					}
		}
	}

	private static void placeMask(boolean[][] modules, boolean[][] funmask, int size, int ecl, int mask) {
		int v = ecl << 3 | mask;
		int r = v;
		for (int i = 0; i < 10; i++)
			r = (r << 1) ^ ((r >> 9) * 0x537);
		v = ((v << 10) | r) ^ 0x5412;
		for (int i = 0; i < 6; i++)
			modules[i][8] = testBit(v, i);
		modules[7][8] = testBit(v, 6);
		modules[8][8] = testBit(v, 7);
		modules[8][7] = testBit(v, 8);
		for (int i = 9; i < 15; i++)
			modules[8][14 - i] = testBit(v, i);
		for (int i = 0; i < 8; i++)
			modules[8][size - 1 - i] = testBit(v, i);
		for (int i = 8; i < 15; i++)
			modules[size - 15 + i][8] = testBit(v, i);
	}

	private static int reedSolomonMultiply(int x, int y) {
		int z = 0;
		for (int i = 7; i >= 0; i--) {
			z = (z << 1) ^ ((z >> 7) * 0x11d);
			if (((y >> i) & 1) != 0)
				z ^= x;
		}
		return z;
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
				coef[j] = (byte) (reedSolomonMultiply(coef[j] & 0xff, root) ^ coef[j + 1]);
			coef[j] = (byte) reedSolomonMultiply(coef[j] & 0xff, root);
			root = reedSolomonMultiply(root, 2);
		}
		int errorCorrectionBase = lengthOfShortBlock + 1 - coef.length;
		byte[][] blocks = new byte[numberOfErrorCorrectionBlocks][lengthOfShortBlock + 1];
		for (int pos = 0, i = 0; i < numberOfErrorCorrectionBlocks; i++) {
			byte[] block = blocks[i];
			int len = lengthOfShortBlock + (i < numberOfShortBlocks ? 0 : 1) - coef.length;
			for (int j = 0, k; j < len; j++) {
				int factor = ((block[j] = codewords[pos + j]) ^ block[errorCorrectionBase]) & 0xff;
				for (k = 0; k < coef.length - 1; k++)
					block[errorCorrectionBase + k] = (byte) (reedSolomonMultiply(coef[k] & 0xff, factor)
							^ block[errorCorrectionBase + k + 1]);
				block[errorCorrectionBase + k] = (byte) reedSolomonMultiply(coef[k] & 0xff, factor);
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

	public static QrCode encode(byte[] data, int ecl) {
		int version = 0;
		for (int i = 1; i <= 40; i++) {
			int bitCapacity = getBitCapacity(i, ecl);
			if (((i < 10 ? 12 : 20) + data.length * 8) <= bitCapacity) {
				BitStream bs = new BitStream();
				bs.append(4, 4);
				bs.append(data.length, i < 10 ? 8 : 16);
				for (byte b : data)
					bs.append(b & 0xff, 8);
				int padBits = bitCapacity - bs.bitLength();
				int padBytes = padBits >> 3;
				padBits &= 7;
				if (padBits != 0)
					bs.append(0, 8 - padBits);
				for (; padBytes >= 2; padBytes -= 2)
					bs.append(0xec11, 16);
				if (padBytes > 0)
					bs.append(0xec, 8);
				data = bs.toByteArray();
				version = i;
				break;
			}
		}
		if (version == 0)
			throw new IllegalArgumentException("size exceed");
		int size = version * 4 + 17;
		boolean[][] modules = new boolean[size][size];
		boolean[][] funmask = new boolean[size][size];
		initializeVersion(version, modules, funmask, size);
		placeErrorCorrectionCodewords(modules, funmask, size, generateErrorCorrectionCodewords(data, version, ecl));
		int pattern = selectMaskPattern(modules, funmask, size, ecl);
		maskPattern(modules, funmask, size, pattern);
		placeMask(modules, funmask, size, ecl, pattern);
		return new QrCode(modules, size);
	}

	private final boolean[][] modules;
	private final int size;

	private QrCode(boolean[][] modules, int size) {
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
		for (int y = 0; y < size; y++)
			for (int x = 0; x < size; x++)
				if (modules[y][x])
					sb.append(String.format("M%d,%dh1v1h-1z ", x + 4, y + 4));
		sb.setCharAt(sb.length() - 1, '"');
		sb.append(" fill=\"#000000\"/>\n");
		sb.append("</svg>\n");
		return sb.toString();
	}

	public byte[] toByteArray() {
		BitStream bs = new BitStream();
		for (int i = 0; i < size; i++)
			for (int j = 0; j < size; j++)
				bs.append(modules[i][j] ? 1 : 0, 1);
		return bs.toByteArray();
	}

	public static QrCode fromByteArray(byte[] array) {
		int size = (int) Math.sqrt(array.length * 8);
		boolean[][] modules = new boolean[size][size];
		for (int p = 0, i = 0; i < size; i++)
			for (int j = 0; j < size; j++, p++)
				modules[i][j] = (array[p >> 3] & (1 << (7 - (p & 7)))) != 0;
		return new QrCode(modules, size);
	}
}
