var EOL = java.EOL;
var arch = function() {
	return java.arch();
}
var constants;
var cpus = function() {};
var endianness = function() {
	return java.endianness();
}
var freemem = function() {}
var homedir = function() {
	return java.homedir();
}
var hostname = function() {
	return java.hostname();
}
var loadavg = function() {}
var networkInterfaces = function() {
	var r = {};
	java.networkInterfaces().forEach(function(name, iinfo) {
		r[name] = [];
		iinfo.forEach(function(item) {
			var i = {};
			item.forEach(function(k, v) { i[k] = v; });
			r[name].push(i);
		})
	});
	return r;
}
var platform = function() {
	return java.platform();
}
var release = function() {
	return java.release();
}
var tmpdir = function() {
	return java.tmpdir();
}
var totalmem = function() {}
var type = function() {
	return java.type();
}
var uptime = function() {}
var userInfo = function(options) {
	var needbuffer = options && options.encoding === 'buffer';
	var list = Java.from(java.userInfo());
	return needbuffer ? {
		username : new Buffer(list[0], 'utf8'),
		homedir : new Buffer(list[1], 'utf8')
	} : {
		username : list[0],
		homedir : list[1]
	}
}
exports.EOL = EOL
exports.arch = arch
exports.constants = constants
exports.cpus = cpus
exports.endianness = endianness
exports.freemem = freemem
exports.homedir = homedir
exports.hostname = hostname
exports.loadavg = loadavg
exports.networkInterfaces = networkInterfaces
exports.platform = platform
exports.release = release
exports.tmpdir = tmpdir
exports.totalmem = totalmem
exports.type = type
exports.uptime = uptime
exports.userInfo = userInfo
