var util = require('util');
var Readable = require('stream').Readable;
var Writable = require('stream').Writable;
var EventEmitter = require('events');
var stream = require('stream');
function ChildProcess(command, options, callback) {
	if (!(this instanceof ChildProcess))
		return new ChildProcess(command, options, callback);
	var self = this;
	var ee = EventEmitter.call(this);
	var encoding = 'utf8';
	if (options.encoding)
		encoding = options.encoding;
	var impl = java.exec(command, options, function(obj) {
		var list = Java.from(obj);
		if (list[0]) {
			if (callback) {
				callback(list[0]);
				return;
			}
			else
				throw list[0];
		}
		var r = list[1];
		if (callback){
			if (Buffer.isEncoding(encoding)) {
				r.stdout = r.stdout.toString(encoding);
				r.stderr = r.stderr.toString(encoding);
			}
			callback(r.status ? r : null, r.stdout, r.stderr);
		}
		ee.emit('exit', r.status, r.signal);
	});
	var IN = function(outerr) {
		var ee;
		var self;
		var readdone = true;
		var read = function() {
			if (!readdone)
				return;
			readdone = false;
			impl.IN(outerr, function(list) {
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
	var OUT = function() {
		var write = function(chunk, encoding, callback) {
			impl.OUT(chunk, function(obj) {
				callback(Java.from(obj)[0]);
			});
		}
		var writev = function(chunks, callback) {
			var all = [];
			for each(var chunk in chunks)
				all.push(chunk.chunk);
			impl.OUT(Buffer.concat(all), function(obj) {
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
	if (impl.isPipe(0))
		this.stdin = OUT();
	if (impl.isPipe(1))
		this.stdout = IN(true).once('data', function(){});
	if (impl.isPipe(2))
		this.stderr = IN(false).once('data', function(){});
	this.stdio = [this.stdin, this.stdout, this.stderr];
	this.kill = function(name) {
		impl.kill(name);
	}
}
util.inherits(ChildProcess, EventEmitter);
var sync = function(command, options) {
	var encoding = 'buffer';
	if (options.encoding)
		encoding = options.encoding;
	var r = {}
	java.sync(command, options).forEach(function(k, v) { r[k] = v });
	if (Buffer.isEncoding(encoding)) {
		r.stdout = r.stdout.toString(encoding);
		r.stderr = r.stderr.toString(encoding);
	}
	r.output = [r.stdout, r.stderr];
	return r;
}
exports.ChildProcess = ChildProcess;
var updateEnv = function(options) {
	if (typeof(options.env) !== 'object' || !options.env)
		options.env = process.env;
}
var SHELL = process.platform.toLowerCase().startsWith('win') ? Java.type('java.lang.System').getenv('ComSpec') + ' /s /c' : '/bin/sh -c';
var createExecCommand = function(command, options) {
	var shell = typeof(options.shell) === 'string' ? options.shell : SHELL;
	var _args = shell.split(/\s+/);
	_args.push(command);
	updateEnv(options);
	return Java.to(_args);
}
var createExecFileCommand = function(file, args, options) {
	var _args = [file];
	for each(var i in args)
		_args.push(i);
	updateEnv(options);
	return Java.to(_args);
}
var createSpawnCommand = function(command, args, options) {
	var _args;
	if (options.shell) {
		var shell = typeof(options.shell) === 'string' ? options.shell : SHELL;
		_args = shell.split(/\s+/);
		_args.push(command);
	} else {
		_args = [command];
	}
	for each(var i in args)
		_args.push(i);
	updateEnv(options);
	return Java.to(_args);
}
exports.exec = function(command) {
	var options = typeof(arguments[1]) === 'object' && arguments[1] ? arguments[1] : {};
	var callback = typeof(arguments[arguments.length - 1]) === 'function' ? arguments[arguments.length - 1] : null;
	return new ChildProcess(createExecCommand(command, options), options, callback);
}
exports.execFile = function(file) {
	var args = [];
	var options = {};
	var callback = typeof(arguments[arguments.length - 1]) === 'function' ? arguments[arguments.length - 1] : null;
	if (arguments[1] instanceof Array) {
		args = arguments[1];
		if (typeof(arguments[2]) === 'object' && arguments[2])
			options = arguments[2];
	} else if (typeof(arguments[1]) === 'object' && arguments[1])
		options = arguments[1];
	return new ChildProcess(createExecFileCommand(file, args), options, callback)
}
exports.spawn = function(command) {
	var args = [];
	var options = {};
	if (arguments[1] instanceof Array) {
		args = arguments[1];
		if (typeof(arguments[2]) === 'object' && arguments[2])
			options = arguments[2];
	} else if (typeof (arguments[1]) === 'object' && arguments[1])
		options = arguments[1];
	return new ChildProcess(createSpawnCommand(command, args, options), options);
}
var check = function(r) {
	if (r.status || r.error) {
		var e = new Error('');
		for (var k in r)
			e[k] = r[k];
		throw e;
	}
	return r;
}
exports.execFileSync = function(file) {
	var args = [];
	var options = {};
	if (arguments[1] instanceof Array) {
		args = arguments[1];
		if (typeof(arguments[2]) === 'object' && arguments[2])
			options = arguments[2];
	} else if (typeof (arguments[1]) === 'object' && arguments[1])
		options = arguments[1];
	options.__force_stderr = true;
	return check(sync(createExecFileCommand(file, args), options)).stdout;
}
exports.execSync = function(command) {
	var options = typeof(arguments[1]) === 'object' && arguments[1] ? arguments[1] : {};
	options.__force_stderr = true;
	return check(sync(createExecCommand(command, options), options)).stdout;
}
exports.spawnSync = function(command) {
	var args = [];
	var options = {};
	if (arguments[1] instanceof Array) {
		args = arguments[1];
		if (typeof(arguments[2]) === 'object' && arguments[2])
			options = arguments[2];
	} else if (typeof (arguments[1]) === 'object' && arguments[1])
		options = arguments[1];
	return sync(createSpawnCommand(command, args, options), options);
}
Object.defineProperty(exports, 'maxBuffer', {
	get : function() {
		return java.getMaxBuffer();
	},
	set : function(val) {
		java.setMaxBuffer(val);
	},
	enumerable : true
});
java.setProcess(process);