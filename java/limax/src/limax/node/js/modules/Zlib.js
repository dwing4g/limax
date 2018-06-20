var unpackOptions = function(options) {
	var o = {
		flush : java.Z_NO_FLUSH,
		finishFlush : java.Z_FINISH,
		level : null,
		strategy : null,
		dictionary : null,
	}
	if (options) {
		if (options.flush)
			o.flush = options.flush;
		if (options.finishFlush)
			o.finishFlush = options.finishFlush;
		if (options.level)
			o.level = options.level;
		if (options.strategy)
			o.strategy = options.strategy;
		if (options.dictionary)
			o.dictionary = options.dictionary;
	}	
	return o;
}
var processCallback = function(callback) {
	return function(obj) {
		var list = Java.from(obj);
		if (list[0])
			callback(list[0]);
		else
			callback(list[0], list[1]);
	}
}
var processPush = function(self, callback) {
	return function(obj) {
		var list = Java.from(obj);
		if (list[0]) {
			self.emit('error', list[0]);
			self.push(null);
		}
		else if (list[1] && list[1].length > 0) {
			self.push(list[1]);
		}
		if (callback)
			callback();
	}
}
var toBuffer = function(buf, encoding) {
	return buf instanceof Buffer ? buf : new Buffer(buf, encoding ? encoding : 'utf8');
}
var deflate = function(buf, options, nowrap, callback) {
	var o = unpackOptions(options);
	java.deflate(toBuffer(buf), nowrap, o.level, o.strategy, o.dictionary, processCallback(callback));
}
var deflateSync = function(buf, options, nowrap) {
	var o = unpackOptions(options);
	return java.deflateSync(toBuffer(buf), nowrap, o.level, o.strategy, o.dictionary);
}
var inflate = function(buf, options, nowrap, callback) {
	java.inflate(toBuffer(buf), nowrap, unpackOptions(options).dictionary, processCallback(callback));
}
var inflateSync = function(buf, options, nowrap) {
	return java.inflateSync(toBuffer(buf), nowrap, unpackOptions(options).dictionary);
}
var Transform = require('stream').Transform;
var util = require('util');
function Zlib(options, impl) {
	this._transform = function(chunk, encoding, callback) {
		impl.write(toBuffer(chunk, encoding), processPush(this, callback));
	}
	this._flush = function(callback) {
		impl.flush(processPush(this, callback));
	}
	this.flush = function() {
		var kind = arguments.length == 2 ? arguments[0] : java.Z_FULL_FLUSH;
		var callback = arguments[arguments.length - 1];
		impl.flush(kind, processPush(this, callback));
	}
	this.params = function(level, strategy, callback) {
		impl.params(level, strategy, function(obj) {
			callback(Java.from(obj)[0]);
		});
	}
	this.reset = function() {
		impl.reset();
	}
	Transform.call(this, options);
}
util.inherits(Zlib, Transform);
function Deflate(options) {
	if (!(this instanceof Deflate))
		return new Deflate(options);
	var o = unpackOptions(options);
	Zlib.call(this, options, java.createDeflate(false, o.flush, o.finishFlush, o.level, o.strategy, o.dictionary));
}
util.inherits(Deflate, Zlib);
function DeflateRaw(options) {
	if (!(this instanceof DeflateRaw))
		return new DeflateRaw(options);
	var o = unpackOptions(options);
	Zlib.call(this, options, java.createDeflate(true, o.flush, o.finishFlush, o.level, o.strategy, o.dictionary));
}
util.inherits(DeflateRaw, Zlib);
function Gunzip(options) {
	if (!(this instanceof Gunzip))
		return new Gunzip(options);
	Zlib.call(this, options, java.createGunzip());
}
util.inherits(Gunzip, Zlib);
function Gzip(options) {
	if (!(this instanceof Gzip))
		return new Gzip(options);
	Zlib.call(this, options, java.createGzip(unpackOptions(options).flush));
}
util.inherits(Gzip, Zlib);
function Inflate(options) {
	if (!(this instanceof Inflate))
		return new Inflate(options);
	Zlib.call(this, options, java.createInflate(false, unpackOptions(options).dictionary));
}
util.inherits(Inflate, Zlib);
function InflateRaw(options) {
	if (!(this instanceof InflateRaw))
		return new InflateRaw(options);
	Zlib.call(this, options, java.createInflate(true, unpackOptions(options).dictionary));
}
util.inherits(InflateRaw, Zlib);
function Unzip(options) {
	if (!(this instanceof Unzip))
		return new Unzip(options);
	Zlib.call(this, options, java.createUnzip());
}
util.inherits(Unzip, Zlib);
exports.Deflate = Deflate
exports.DeflateRaw = DeflateRaw
exports.Gunzip = Gunzip
exports.Gzip = Gzip
exports.Inflate = Inflate
exports.InflateRaw = InflateRaw
exports.Unzip = Unzip
exports.createDeflate = function(options) {
	return new Deflate(options);
}
exports.createDeflateRaw = function(options) {
	return new DeflateRaw(options);
}
exports.createGunzip = function(options) {
	return new Gunzip(options);
}
exports.createGzip = function(options) {
	return new Gzip(options);
}
exports.createInflate = function(options) {
	return new Inflate(options);
}
exports.createInflateRaw = function(options) {
	return new InflateRaw(options);
}
exports.createUnzip = function(options) {
	return new Unzip(options);
}
exports.deflate = function(buf) {
	deflate(buf, arguments.length == 3 ? arguments[1] : null, false, arguments[arguments.length - 1])
}
exports.deflateSync = function(buf, options) {
	return deflateSync(buf, options, false);
}
exports.deflateRaw = function(buf) {
	deflate(buf, arguments.length == 3 ? arguments[1] : null, true, arguments[arguments.length - 1])
}
exports.deflateRawSync = function(buf, options) {
	return deflateSync(buf, options, true);
}
exports.gunzip = function(buf) {
	java.gunzip(toBuffer(buf), processCallback(arguments[arguments.length - 1]));
}
exports.gunzipSync = function(buf) {
	return java.gunzipSync(toBuffer(buf));
}
exports.gzip = function(buf) {
	java.gzip(toBuffer(buf), processCallback(arguments[arguments.length - 1]));
}
exports.gzipSync = function(buf) {
	return java.gzipSync(toBuffer(buf));
}
exports.inflate = function(buf) {
	inflate(buf, arguments.length == 3 ? arguments[1] : null, false, arguments[arguments.length - 1])
}
exports.inflateSync = function(buf, options) {
	return inflateSync(buf, options, false);
}
exports.inflateRaw = function(buf) {
	inflate(buf, arguments.length == 3 ? arguments[1] : null, true, arguments[arguments.length - 1])
}
exports.inflateRawSync = function(buf, options) {
	return inflateSync(buf, options, true);
}
exports.unzip = function(buf) {
	java.unzip(toBuffer(buf), processCallback(arguments[arguments.length - 1]));
}
exports.unzipSync = function(buf) {
	return java.unzipSync(toBuffer(buf));
}
exports.Z_NO_FLUSH = java.Z_NO_FLUSH
exports.Z_PARTIAL_FLUSH = java.Z_PARTIAL_FLUSH
exports.Z_SYNC_FLUSH = java.Z_SYNC_FLUSH
exports.Z_FULL_FLUSH = java.Z_FULL_FLUSH
exports.Z_FINISH = java.Z_FINISH
exports.Z_BLOCK = java.Z_BLOCK
exports.Z_TREES = java.Z_TREES
exports.Z_NO_COMPRESSION = java.Z_NO_COMPRESSION
exports.Z_BEST_SPEED = java.Z_BEST_SPEED
exports.Z_BEST_COMPRESSION = java.Z_BEST_COMPRESSION
exports.Z_DEFAULT_COMPRESSION = java.Z_DEFAULT_COMPRESSION
exports.Z_FILTERED = java.Z_FILTERED
exports.Z_HUFFMAN_ONLY = java.Z_HUFFMAN_ONLY
exports.Z_RLE = java.Z_RLE
exports.Z_FIXED = java.Z_FIXED
exports.Z_DEFAULT_STRATEGY = java.Z_DEFAULT_STRATEGY
exports.Z_DEFLATED = java.Z_DEFLATED
