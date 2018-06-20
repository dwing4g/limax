Buffer = Java.type('limax.node.js.Buffer');
Buffer.alloc = function(size, fill, encoding) {
	var b = new Buffer(size);
	if (typeof(fill) !== "undefined")
		b.fill(fill, 0, b.length, encoding);
	return b;
}
Buffer.allocUnsafe = function(size) {
	return new Buffer(size);
}
Buffer.allocUnsafeSlow = function(size) {
	return new Buffer(size);
}
Buffer.byteLength = function(string, encoding) {
	return new Buffer(string, encoding).length;
}
Buffer.compare = function(a, b) {
	return a.compare(b);
}
Buffer.concat = function(list, totalLength) {
	var len;
	if (totalLength) {
		len = totalLength;
	} else {
		len = 0;
		for each(var v in list)
			len += v.length;
	}
	var dst = new Buffer(len);
	var off = 0;
	for each(var v in list)	{
		var ncp = v.length < len ? v.length : len;
		v.copy(dst, off, 0, ncp);
		len -= ncp;
		if (len == 0)
			break;
		off += ncp;
	}
	return dst;
}
Buffer.from = function(obj, encoding) {
	if (obj instanceof Array)
		return new Buffer(obj);
	if (obj instanceof Buffer)
		return new Buffer(obj);
	if (typeof(obj) === "string")
		return new Buffer(obj, encoding);
}
Buffer.isBuffer = function(obj) {
	return obj instanceof Buffer;
}
Buffer.isEncoding = function(encoding) {
	if (typeof(encoding) !== 'string')
		return false;
	switch(encoding.toLowerCase()) {
	case 'utf8':
	case 'utf16le':
	case 'ucs2':
	case 'ascii':
	case 'latin1':
	case 'binary':
	case 'hex':
	case 'base64':
		return true;
	}
	return false;
}
Buffer