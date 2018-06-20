var stream = require('stream');
var IN = function() {
	var ee;
	var self;
	var readdone = true;
	var read = function() {
		if (!readdone)
			return;
		readdone = false;
		java.IN(function(list) {
			var args = Java.from(list);
			var e = args[0];
			var data = args[1];
			var more = false;
			if (e) {
				ee.emit('error', e);
			} else if (data) {
				readdone = true;
				more = self.push(data);
			} else {
				readdone = true;
				self.push(null);
			}
			if (more)
				read();
		});
	}
	var instance = new stream.Readable({
		read : read
	});
	return ee = self = instance;
}
var OUT = function(outerr) {
	var write = function(chunk, encoding, callback) {
		java.OUT(outerr, chunk.toString(encoding), function(obj) {
			callback(Java.from(obj)[0]);
		});
	}
	var writev = function(chunks, callback) {
		var s = '';
		for each(var chunk in chunks)
			s += chunk.chunk.toString(chunk.encoding);
		java.OUT(outerr, s, function(obj) {
			callback(Java.from(obj)[0]);
		});
	}
	var instance = new stream.Writable({
		write : write,
		writev : writev
	});
	instance.end = function() {}
	return instance;
}
var System = Java.type("java.lang.System");
var env = {};
System.env.forEach(function(k, v) { env[k] = v });
process = {
	chdir : function(directory) {
		java.chdir(directory);
	},
	cwd : function() { 
		return java.cwd(); 
	},
	get env() {
	    return env;
	},
	exit : function() {
		if (arguments.length === 1)
			this.exitCode = parseInt(arguments[0]);
			System.exit(this.exitCode);
	},
	exitCode : 0,
	hrtime : function() {
		return Java.from(java.hrtime(arguments.length === 0 ? null : Java.to(arguments[0])));
	},
	memoryUsage : function() {
		var r = {}
		java.memoryUsage().forEach(function(k, v) { r[k] = v });
		return r;
	},
	nextTick : function() {
		var args = [];
		for (var i in arguments)
			args.push(arguments[i]);
		setImmediate.apply(null, args);
	},
	platform : java.platform,
	stderr : new OUT(false),
	stdin : new IN(),
	stdout : new OUT(true)
}

