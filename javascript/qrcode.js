if (!Array.prototype.fill) {
	Array.prototype.fill = function(val, start) {
		if (typeof(start) != 'number')
			start = 0;
		for (var i = start; i < this.length; i++)
			this[i] = val;
	}
}

function QrCode(modules, size) {
	var errorCorrectionCharacteristics = [ 
		[ 26, 1, 1, 1, 1, 10, 7, 17, 13 ], [ 44, 1, 1, 1, 1, 16, 10, 28, 22 ],
		[ 70, 1, 1, 2, 2, 26, 15, 22, 18 ], [ 100, 2, 1, 4, 2, 18, 20, 16, 26 ],
		[ 134, 2, 1, 4, 4, 24, 26, 22, 18 ], [ 172, 4, 2, 4, 4, 16, 18, 28, 24 ],
		[ 196, 4, 2, 5, 6, 18, 20, 26, 18 ], [ 242, 4, 2, 6, 6, 22, 24, 26, 22 ],
		[ 292, 5, 2, 8, 8, 22, 30, 24, 20 ], [ 346, 5, 4, 8, 8, 26, 18, 28, 24 ],
		[ 404, 5, 4, 11, 8, 30, 20, 24, 28 ], [ 466, 8, 4, 11, 10, 22, 24, 28, 26 ],
		[ 532, 9, 4, 16, 12, 22, 26, 22, 24 ], [ 581, 9, 4, 16, 16, 24, 30, 24, 20 ],
		[ 655, 10, 6, 18, 12, 24, 22, 24, 30 ], [ 733, 10, 6, 16, 17, 28, 24, 30, 24 ],
		[ 815, 11, 6, 19, 16, 28, 28, 28, 28 ], [ 901, 13, 6, 21, 18, 26, 30, 28, 28 ],
		[ 991, 14, 7, 25, 21, 26, 28, 26, 26 ], [ 1085, 16, 8, 25, 20, 26, 28, 28, 30 ],
		[ 1156, 17, 8, 25, 23, 26, 28, 30, 28 ], [ 1258, 17, 9, 34, 23, 28, 28, 24, 30 ],
		[ 1364, 18, 9, 30, 25, 28, 30, 30, 30 ], [ 1474, 20, 10, 32, 27, 28, 30, 30, 30 ],
		[ 1588, 21, 12, 35, 29, 28, 26, 30, 30 ], [ 1706, 23, 12, 37, 34, 28, 28, 30, 28 ],
		[ 1828, 25, 12, 40, 34, 28, 30, 30, 30 ], [ 1921, 26, 13, 42, 35, 28, 30, 30, 30 ],
		[ 2051, 28, 14, 45, 38, 28, 30, 30, 30 ], [ 2185, 29, 15, 48, 40, 28, 30, 30, 30 ],
		[ 2323, 31, 16, 51, 43, 28, 30, 30, 30 ], [ 2465, 33, 17, 54, 45, 28, 30, 30, 30 ],
		[ 2611, 35, 18, 57, 48, 28, 30, 30, 30 ], [ 2761, 37, 19, 60, 51, 28, 30, 30, 30 ],
		[ 2876, 38, 19, 63, 53, 28, 30, 30, 30 ], [ 3034, 40, 20, 66, 56, 28, 30, 30, 30 ],
		[ 3196, 43, 21, 70, 59, 28, 30, 30, 30 ], [ 3362, 45, 22, 74, 62, 28, 30, 30, 30 ],
		[ 3532, 47, 24, 77, 65, 28, 30, 30, 30 ], [ 3706, 49, 25, 81, 68, 28, 30, 30, 30 ]];

	var getTotalNumberOfCodewords = function(version) {
		return errorCorrectionCharacteristics[version - 1][0];
	}

	var getNumberOfErrorCorrectionBlocks = function(version, ecl) {
		return errorCorrectionCharacteristics[version - 1][ecl + 1];
	}

	var getNumberOfErrorCorrectionCodewordsPerBlock = function(version, ecl) {
		return errorCorrectionCharacteristics[version - 1][ecl + 5];
	}

	var getBitCapacity = function(version, ecl) {
		return (getTotalNumberOfCodewords(version) - getNumberOfErrorCorrectionBlocks(version, ecl)	* getNumberOfErrorCorrectionCodewordsPerBlock(version, ecl)) * 8;
	}

	function BitStream() {
		this.data = [];
		this.nbits = 0;
		
		this.toByteArray = function() {
			var r = [];
			var n = this.nbits >> 5;
			var j = 0;
			for (var i = 0; i < n; i++) {
				r.push((this.data[i] >> 24) & 0xff);
				r.push((this.data[i] >> 16) & 0xff);
				r.push((this.data[i] >> 8) & 0xff);
				r.push(this.data[i] & 0xff);
			}
			var m = this.nbits & 31;
			m = (m >> 3) + ((m & 7) != 0 ? 1 : 0);
			for (var i = 0; i < m; i++)
				r.push((this.data[n] >> (24 - (i << 3))) & 0xff);
			return r;
		}

		this.append = function(val, len) {
			var shift = 32 - (this.nbits & 31) - len;
			if (shift >= 0) {
				this.data[this.nbits >> 5] |= val << shift;
			} else {
				this.data[this.nbits >> 5] |= val >>> -shift;
				this.data[(this.nbits >> 5) + 1] = val << (32 + shift);
			}
			this.nbits += len;
		}
	}

	var testBit = function(x, i) {
		return (x & (1 << i)) != 0;
	}

	var initializeVersion = function(version, modules, funmask, size) {
		var n = size - 7;
		for (var i = 0, j = size - 1; i < 7; i++) {
			var k = n + i;
			modules[i] = modules[6 * size + i] = modules[i * size] = modules[i * size + 6] = true;
			modules[n * size + i] = modules[j * size + i] = modules[i * size + n] = modules[i * size + j] = true;
			modules[k] = modules[6 * size + k] = modules[k * size] = modules[k * size + 6] = true;
		}
		for (var i = 2; i < 5; i++)
			for (var j = 2; j < 5; j++)
				modules[j * size + i] = modules[(n + j) * size + i] = modules[j * size + n + i] = true;
		n--;
		for (var i = 0; i < 8; i++)
			for (var j = 0; j < 8; j++)
				funmask[j * size + i] = funmask[(n + j) * size + i] = funmask[j * size + n + i] = true;
		for (var i = 8; i < n; i++) {
			modules[6 * size + i] = modules[i * size + 6] = (i & 1) == 0;
			funmask[6 * size + i] = funmask[i * size + 6] = true;
		}
		if (version > 1) {
			n = Math.floor(version / 7 + 2);
			var s = version == 32 ? 26 : Math.floor((version * 4 + n * 2 + 1) / (n * 2 - 2)) * 2;
			var r = new Array(n);
			for (var i = n, p = version * 4 + 10; --i > 0; p -= s)
				r[i] = p;
			r[0] = 6;
			for (var a = 0; a < n; a++)
				for (var b = 0; b < n; b++) {
					if (a == 0 && b == 0 || a == 0 && b == n - 1 || a == n - 1 && b == 0)
						continue;
					var x = r[b];
					var y = r[a];
					for (var i = -2; i <= 2; i++) {
						for (var j = -2; j <= 2; j++)
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
			var r = version;
			for (var i = 0; i < 12; i++)
				r = (r << 1) ^ ((r >> 11) * 0x1f25);
			var v = version << 12 | r;
			for (var i = 0; i < 18; i++) {
				var	x = Math.floor(i / 3);
				var	y = size - 11 + Math.floor(i % 3);
				funmask[y * size + x] = funmask[x * size + y] = true;
				if (testBit(v, i))
					modules[y * size + x] = modules[x * size + y] = true;
			}
		}
		for (var i = 0; i < 9; i++)
			funmask[i * size + 8] = funmask[8 * size + i] = true;
		for (var i = 0; i < 8; i++)
			funmask[8 * size + size - i - 1] = funmask[(size - 8 + i) * size + 8] = true;
		modules[(size - 8) * size + 8] = true;
	}

	var selectMaskPattern = function(modules, funmask, size, ecl) {
		var pattern = 0;
		var minPenaltyScore = Number.MAX_VALUE;
		for (var i = 0; i < 8; i++) {
			placeMask(modules, funmask, size, ecl, i);
			maskPattern(modules, funmask, size, i);
			var penaltyScore = computePenaltyScore(modules, size);
			if (penaltyScore < minPenaltyScore) {
				minPenaltyScore = penaltyScore;
				pattern = i;
			}
			maskPattern(modules, funmask, size, i);
		}
		return pattern;
	}

	var maskPattern = function(modules, funmask, size, pattern) {
		for (var p = 0, i = 0; i < size; i++) {
			for (var j = 0; j < size; j++, p++) {
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
						modules[p] ^= (Math.floor(i / 2) + Math.floor(j / 3)) % 2 == 0;
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

	var computePenaltyScore = function(modules, size) {
		var score = 0;
		var dark = 0;
		for (var i = 0; i < size; i++) {
			var xcolor = modules[i * size];
			var ycolor = modules[i];
			var xsame = 1;
			var ysame = 1;
			var xbits = modules[i * size] ? 1 : 0;
			var ybits = modules[i] ? 1 : 0;
			dark += modules[i * size] ? 1 : 0;
			for (var j = 1; j < size; j++) {
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
		for (var i = 0; i < size - 1; i++)
			for (var j = 0; j < size - 1; j++) {
				var c = modules[i * size + j];
				if (c == modules[i * size + j + 1] && c == modules[(i + 1) * size + j] && c == modules[(i + 1) * size + j + 1])
					score += 3;
			}
		dark *= 20;
		for (var k = 0, total = size * size; dark < total * (9 - k) || dark > total * (11 + k); k++)
			score += 10;
		return score;
	}

	var placeErrorCorrectionCodewords = function(modules, funmask, size, errorCorrectionCodewords) {
		for (var i = 0, bitLength = errorCorrectionCodewords.length << 3, x = size - 1, y = size - 1, dir = -1; x >= 1; x -= 2, y += (dir = -dir)) {
			if (x == 6)
				x = 5;
			for (; y >= 0 && y < size; y += dir)
				for (var j = 0; j < 2; j++) {
					var p = y * size + x - j;
					if (!funmask[p] && i < bitLength) {
						modules[p] = testBit(errorCorrectionCodewords[i >> 3], 7 - (i & 7));
						i++;
					}
				}
		}
	}

	var placeMask = function(modules, funmask, size, ecl, mask) {
		var v = ecl << 3 | mask;
		var r = v;
		for (var i = 0; i < 10; i++)
			r = (r << 1) ^ ((r >> 9) * 0x537);
		v = ((v << 10) | r) ^ 0x5412;
		for (var i = 0; i < 6; i++)
			modules[i * size + 8] = testBit(v, i);
		modules[7 * size + 8] = testBit(v, 6);
		modules[8 * size + 8] = testBit(v, 7);
		modules[8 * size + 7] = testBit(v, 8);
		for (var i = 9; i < 15; i++)
			modules[8 * size + 14 - i] = testBit(v, i);
		for (var i = 0; i < 8; i++)
			modules[8 * size + size - 1 - i] = testBit(v, i);
		for (var i = 8; i < 15; i++)
			modules[(size - 15 + i) * size + 8] = testBit(v, i);
	}

	var gf_mul = function(x, y) {
		var z = 0;
		for (var i = 7; i >= 0; i--) {
			z = (z << 1) ^ ((z >> 7) * 0x11d);
			if (((y >> i) & 1) != 0)
				z ^= x;
		}
		return z;
	}

	var generateErrorCorrectionCodewords = function(codewords, version, ecl) {
		var totalNumberOfCodewords = getTotalNumberOfCodewords(version);
		var numberOfErrorCorrectionBlocks = getNumberOfErrorCorrectionBlocks(version, ecl);
		var numberOfErrorCorrectionCodewordsPerBlock = getNumberOfErrorCorrectionCodewordsPerBlock(version, ecl);
		var numberOfShortBlocks = numberOfErrorCorrectionBlocks	- Math.floor(totalNumberOfCodewords % numberOfErrorCorrectionBlocks);
		var lengthOfShortBlock = Math.floor(totalNumberOfCodewords / numberOfErrorCorrectionBlocks);
		var coef = new Array(numberOfErrorCorrectionCodewordsPerBlock);
		coef.fill(0);
		coef[coef.length - 1] = 1;
		for (var j, root = 1, i = 0; i < coef.length; i++) {
			for (j = 0; j < coef.length - 1; j++)
				coef[j] = gf_mul(coef[j], root) ^ coef[j + 1];
			coef[j] = gf_mul(coef[j], root);
			root = gf_mul(root, 2);
		}
		var errorCorrectionBase = lengthOfShortBlock + 1 - coef.length;
		var blocks = new Array(numberOfErrorCorrectionBlocks);
		for (var pos = 0, i = 0; i < numberOfErrorCorrectionBlocks; i++) {
			var block = new Array(lengthOfShortBlock + 1);
			block.fill(0, errorCorrectionBase);
			blocks[i] = block;
			var len = lengthOfShortBlock + (i < numberOfShortBlocks ? 0 : 1) - coef.length;
			for (var j = 0, k; j < len; j++) {
				var factor = (block[j] = codewords[pos + j]) ^ block[errorCorrectionBase];
				for (k = 0; k < coef.length - 1; k++)
					block[errorCorrectionBase + k] = gf_mul(coef[k], factor) ^ block[errorCorrectionBase + k + 1];
				block[errorCorrectionBase + k] = gf_mul(coef[k], factor);
			}
			pos += len;
		}
		var r = [];
		for (var i = 0; i <= lengthOfShortBlock; i++)
			for (var j = 0; j < numberOfErrorCorrectionBlocks; j++)
				if (i != lengthOfShortBlock - numberOfErrorCorrectionCodewordsPerBlock || j >= numberOfShortBlocks)
					r.push(blocks[j][i]);
		return r;
	}

	var tmp = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";
	var ALPHANUMERIC = {};
	for (var i in tmp)
		ALPHANUMERIC[tmp.charCodeAt(i)] = Number(i);

	var encode = function(data, ecl) {
		var i = 0, version, mode, len = data.length, nbits = 4, lbits = 0, pbits = -1, pbytes;
		for (; i < len && data[i] >= 48 && data[i] <= 57; i++)
			;
		if (i == len) {
			mode = 1;
			nbits += Math.floor(len / 3) * 10;
			switch (len % 3) {
			case 2:
				nbits += 7;
				break;
			case 1:
				nbits += 4;
			}
		} else {
			for (; i < len && ALPHANUMERIC[data[i]]; i++)
				;
			if (i == len) {
				mode = 2;
				nbits += (len >> 1) * 11 + (len & 1) * 6;
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
		var bs = new BitStream();
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
				bs.append(ALPHANUMERIC[data[i]] * 45 + ALPHANUMERIC[data[i + 1]], 11);
			if (i < len)
				bs.append(ALPHANUMERIC[data[i]], 6);
			break;
		default:
			for (i = 0; i < data.length; i++)
				bs.append(data[i] & 0xff, 8);
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
		var size = version * 4 + 17;
		var modules = new Array(size * size);
		var funmask = new Array(size * size);
		initializeVersion(version, modules, funmask, size);
		placeErrorCorrectionCodewords(modules, funmask, size, generateErrorCorrectionCodewords(data, version, ecl));
		var pattern = selectMaskPattern(modules, funmask, size, ecl);
		maskPattern(modules, funmask, size, pattern);
		placeMask(modules, funmask, size, ecl, pattern);
		return new QrCode(modules, size);
	}

	var toSvgXML = function() {
		var sb = [];
		sb.push("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.push(
				"<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n");
		sb.push("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 " + (this.size + 8) + " "
				+ (this.size + 8) + "\" stroke=\"none\">\n");
		sb.push("\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n");
		sb.push("\t<path d=\"");
		for (var p = 0, y = 0; y < this.size; y++)
			for (var x = 0; x < this.size; x++)
				if (this.modules[p++])
					sb.push("M" + (x + 4) + "," + (y + 4)+ "h1v1h-1z ");
		sb.push("\" fill=\"#000000\"/>\n");
		sb.push("</svg>\n");
		return sb.join("");
	}
	
	var toUTF8 = function(text) {
		var pos = 0;
		var len = text.length;
		var r = [];
		do 
		{
			var cp = text.charCodeAt(pos++);
			if (cp < 0x80)
				r.push(cp);
			else if (cp >= 0x80 && cp <= 0x7ff)
				r.push(((cp >> 6) & 0x1f | 0xc0, cp & 0x3f | 0x80));
			else if (cp >= 0xd800 && cp <= 0xdbff) {
	            cp = (((cp - 0xd800) << 10) | (text.charCodeAt(pos++) - 0xdc00)) + 0x10000;
	            r.push((cp >> 18) | 0xf0, (cp >> 12) & 0x3f | 0x80, (cp >> 6) & 0x3f | 0x80, cp & 0x3f | 0x80);
			} else
				r.push((cp >> 12) & 0x0f | 0xe0, (cp >> 6) & 0x3f | 0x80, cp & 0x3f | 0x80);
		} while(pos < len);
		return r;
	}
	
	QrCode = function(modules, size) {
		this.modules = modules;
		this.size = size;
	}
	QrCode.prototype.toSvgXML = toSvgXML;
	QrCode.ECL_M = 0;
	QrCode.ECL_L = 1;
	QrCode.ECL_H = 2;
	QrCode.ECL_Q = 3;
	QrCode.encode = encode;
	QrCode.toUTF8 = toUTF8;
}
QrCode();