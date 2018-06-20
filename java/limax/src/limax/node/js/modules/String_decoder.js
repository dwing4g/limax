function StringDecoder(encoding) {
	if (!(this instanceof StringDecoder))
		return new StringDecoder(encoding);
	var decoder = java.createDecoder(encoding === 'utf16le'	|| encoding === 'ucs2' ? encoding : 'utf8');
	this.write = function(buffer) {
		return decoder.write(buffer.bb);
	}
	this.end = function(buffer) {
		return decoder.end(buffer ? buffer.bb : null);
	}
	return this;
}
exports.StringDecoder = StringDecoder
