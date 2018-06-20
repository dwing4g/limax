var inspect = require('util').inspect;
var basename = function(path, ext) {
	if (typeof(path) !== 'string')
		throw new TypeError("'path' argument must be a string");
	if (ext) {
		if (typeof(ext) !== 'string')
			throw new TypeError("'ext' argument must be a string");
	} else {
		ext = null;
	}
	return java.basename(path, ext);
}
var delimiter = java.delimiter;
var dirname = function(path) {
	if (typeof(path) !== 'string')
		throw new TypeError("'path' argument must be a string");
	return java.dirname(path);
}
var extname = function(path) {
	var base = basename(path);
	var pos = base.lastIndexOf('.');
	return pos <= 0 ? '' : base.substring(pos);
}
var format = function(pathObj) {
	var dir = pathObj.dir ? pathObj.dir : null;
	var root = pathObj.root ? pathObj.root : null;
	var base = pathObj.base ? pathObj.base : null;
	var name = pathObj.name ? pathObj.name : null;
	var ext = pathObj.ext ? pathObj.ext : null;
	var r = java.format(dir, root, base, name, ext);
	if (!r)
		throw new Error('format parameter error ' + inspect(pathObj));
	return r;
}
var isAbsolute = function(path) {
	if (typeof(path) !== 'string')
		throw new TypeError("'path' argument must be a string");
	return java.isAbsolute(path);
}
var join = function() {
	var args = [];
	for (var i = 0; i < arguments.length; i++)
		if (typeof(arguments[i]) !== 'string')
			throw new TypeError('Path must be a string. Received ' + inspect(arguments));
		else
			args.push(arguments[i]);
	return java.join(Java.to(args));
}
var normalize = function(path) {
	if (typeof(path) !== 'string')
		throw new TypeError("'path' argument must be a string");
	return java.normalize(path);
}
var parse = function(path) {
	if (typeof(path) !== 'string')
		throw new TypeError("'path' argument must be a string");
	var r = {};
	java.parse(path).forEach(function(k, v) { r[k] = v; });
	return r;
}
var relative = function(from, to) {
	if (typeof(from) !== 'string')
		throw new TypeError("'from' argument must be a string");
	if (typeof(to) !== 'string')
		throw new TypeError("'to' argument must be a string");
	return java.relative(from, to);
}
var resolve = function() {
	var args = [];
	for (var i = 0; i < arguments.length; i++)
		if (typeof(arguments[i]) !== 'string')
			throw new TypeError('Path must be a string. Received ' + inspect(arguments));
		else
			args.push(arguments[i]);
	return java.resolve(Java.to(args));
}
var sep = java.sep;
exports.basename = basename
exports.delimiter = delimiter
exports.dirname = dirname
exports.extname = extname
exports.format = format
exports.isAbsolute = isAbsolute
exports.join = join
exports.normalize = normalize
exports.parse = parse
exports.relative = relative
exports.resolve = resolve
exports.sep = sep
exports.posix = exports;
exports.win32 = exports;
