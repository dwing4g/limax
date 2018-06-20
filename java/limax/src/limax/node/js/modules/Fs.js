java.setDateMethod(
	function(time) {
		return new Date(time)
	}
);
function translatePath(path) {
	return path instanceof Buffer ? path.toString('utf8') : path;
}
function processCallback(callback, transform) {
	return function(obj) {
		var r = Java.from(obj);
		callback.apply(null, r[0] || !transform ? r : transform(r));
	}
}
function toBuffer(buf, encoding) {
	return buf instanceof Buffer ? buf : new Buffer(buf, Buffer.isEncoding(encoding) ? encoding : 'utf8');
}
function timeToSecond(time) {
	return isNaN(time) || !isFinite(time) ? Date.now() / 1000 : time;
}
function range(fd, callback) {
	java.range(fd, processCallback(callback));
}
function writeBulk(fd, buffers, position, callback) {
	java.writeBulk(fd, buffers, position, processCallback(callback));
}
function Stats(stats) {
	if (!(this instanceof Stats))
		return new Stats(stats);
	this.size = stats.size;
	this.atime = stats.atime;
	this.ctime = stats.ctime;
	this.mtime = stats.mtime;
	this.birthtime = stats.birthtime;
	this.isValid = stats.isValid;
	this.isSymbolicLink = function() {
		return stats.isSymbolicLink();
	}
	this.isRegularFile = function() {
		return stats.isRegularFile();
	}
	this.isDirectory = function() {
		return stats.isDirectory();
	}
	this.isFile = function() {
		return stats.isFile();
	}
	this.isReadable = function() {
		return stats.isReadable();
	}
	this.isWritable = function() {
		return stats.isWritable();
	}
	this.isExecutable = function() {
		return stats.isExecutable();
	}
}
var EventEmitter = require('events');
var util = require('util');
function FSWatcher(impl) {
	EventEmitter.call(this);
	this.close = function() {
		impl.close();
	}
}
util.inherits(FSWatcher, EventEmitter);
var stream = require('stream');
var highWaterMark = 65536;
function ReadStream(path, options, fs) {
	if (!(this instanceof ReadStream))
		return new ReadStream(path, options);
	var flags = 'r';
	var encoding = null;
	var fd = null;
	var mode = 0666;
	var autoClose = true;
	var start = null;
	var end = null;
	if (options instanceof Object){
		if (typeof(options.flags) === 'string')
			flags = options.flags;
		if (Buffer.isEncoding(options.encoding))
			encoding = options.encoding;
		if (typeof(options.fd) === 'number')
			fd = parseInt(options.fd);
		if (typeof(options.mode) === 'number')
			mode = parseInt(options.mode);
		if (typeof(options.autoClose) === 'boolean')
			autoClose = options.autoClose;
		if (typeof(options.start) === 'number')
			if ((start = parseInt(options.start)) < 0)
				throw new Error('invalid start (< 0), start = ' + start);
		if (typeof(options.end) === 'number')
			if ((end = parseInt(options.end)) < 0)
				throw new Error('invalid end (< 0), end = ' + end);
		if (start != null && end != null && end < start)
			throw new Error('invalid range (end < start), start = ' + start + 'end = ' + end);
	} else if (Buffer.isEncoding(options)) {
		encoding = options;
	}
	var ee = this;
	var self = this;
	this.bytesRead = 0;
	this.path = path;
	if (end != null)
		end ++;
	var readdone = true;
	var read = function(size) {
		if (size === undefined)
			size = highWaterMark;
		if (fd === null) {
			exports.open(path, flags, mode, function(err, fdesc) {
				if (err) {
					ee.emit('error', err);
				} else {
					autoClose = true;
					ee.emit('open', fd = fdesc);
					read(size);
				}
			});
		} else if (start === null || end === null) {
			range(fd, function(err, _start, _end) {
				if (start === null)
					start = _start;
				if (end === null)
					end = _end;
				read(size);
			});
		} else {
			if (!readdone)
				return;
			readdone = false;
			var len = end - start;
			var min = size < len ? size : len;
			exports.read(fd, new Buffer(min), 0, min, start, function(err, bytesRead, buffer) {
				var close = autoClose;
				var capacity = 0;
				if (err) {
					ee.emit('error', ee);
				} else if (bytesRead <= 0) {
					readdone = true;
					self.push(null);
				} else {
					readdone = true;
					buffer.setLength(bytesRead);
					capacity = self.push(buffer);
					self.bytesRead += bytesRead;
					start += bytesRead;
					close = false;
				}
				if (capacity)
					read(capacity);
				if (close)
					exports.close(fd, function() {
						ee.emit('close');
					});
			});
		}
	}
	stream.Readable.call(this, {
		highWaterMark : highWaterMark, 
		encoding : encoding, 
		read : read 
	});
}
util.inherits(ReadStream, stream.Readable);
function WriteStream(path, options, fs) {
	if (!(this instanceof WriteStream))
		return new WriteStream(path, options);
	var flags = 'w';
	var defaultEncoding = null;
	var fd = null;
	var mode = 0666;
	var autoClose = true;
	var start = null;
	if (options instanceof Object){
		if (typeof(options.flags) === 'string')
			flags = options.flags;
		if (Buffer.isEncoding(options.defaultEncoding))
			defaultEncoding = options.encoding;
		if (typeof(options.fd) === 'number')
			fd = parseInt(options.fd);
		if (typeof(options.mode) === 'number')
			mode = parseInt(options.mode);
		if (typeof(options.autoClose) === 'boolean')
			autoClose = options.autoClose;
		if (typeof(options.start) === 'number')
			if ((start = parseInt(options.start)) < 0)
				throw new Error('invalid start (< 0), start = ' + start);
	} else if (Buffer.isEncoding(options)) {
		defaultEncoding = options;
	}
	var ee = this;
	var self = this;
	this.bytesWritten = 0;
	this.path = path;
	var checkfd = function(callback) {
		if (fd !== null)
			return true;
		exports.open(path, flags, mode, function(err, fdesc) {
			if (err) {
				ee.emit('error', err);
			} else {
				autoClose = true;
				ee.emit('open', fd = fdesc);
				callback();
			}
		});
		return false;
	}
	var checkclose = function() {
		if (!checkfd(function() { checkclose(); }))
			return;
		if (autoClose) {
			autoClose = false;
			exports.close(fd, function() {
				ee.emit('close');
			});
		}
	}
	var write = function(chunk, encoding, callback) {
		if (!checkfd(function() { write(chunk, encoding, callback); }))
			return;
		exports.write(fd, chunk, 0, chunk.length, start, function(e, bytesWritten) {
			if (e)
				checkclose();
			self.bytesWritten += bytesWritten;
			callback(e);
		});
		start = null;
	}
	var writev = function(chunks, callback) {
		if (!checkfd(function() { writev(chunks, callback); }))
			return;
		var buffers = [];
		for each(var chunk in chunks)
			buffers.push(chunk.chunk);
		writeBulk(fd, Java.to(buffers), start, function(e, bytesWritten) {
			if (e)
				checkclose();
			self.bytesWritten += bytesWritten;
			callback(e);
		});
	}
	stream.Writable.call(this, {
		highWaterMark : highWaterMark, 
		write : write,
		writev : writev
	}).setDefaultEncoding(defaultEncoding).once('finish', checkclose);
}
util.inherits(WriteStream, stream.Writable);
exports.FSWatcher = FSWatcher
exports.ReadStream = ReadStream
exports.WriteStream = WriteStream
exports.access = function() {
	var callback = arguments[arguments.length - 1];
	java.access(translatePath(arguments[0]), arguments[1] ? arguments[1] : 0, processCallback(callback));
}
exports.accessSync = function() {
	java.accessSync(translatePath(arguments[0]), arguments[1] ? arguments[1] : 0);
}
exports.appendFile = function() {
	var encoding = arguments.length == 4 ? arguments[2] : 'utf8';
	var callback = arguments[arguments.length - 1];
	java.appendFile(translatePath(arguments[0]), toBuffer(arguments[1], encoding), processCallback(callback));
}
exports.appendFileSync = function() {
	java.appendFileSync(translatePath(arguments[0]), toBuffer(arguments[1], arguments[2]));
}
exports.chmod = function(path, mode, callback) {
	java.chmod(translatePath(path), mode, processCallback(callback));
}
exports.chmodSync = function(path, mode) {
	java.chmodSync(translatePath(path), mode);
}
exports.chown = function(path, uid, gid, callback) {
	java.chown(translatePath(path), uid, gid, processCallback(callback));
}
exports.chownSync = function(path, uid, gid) {
	java.chownSync(translatePath(path), uid, gid);
}
exports.close = function() {
	var callback = arguments[1];
	java.close(arguments[0], processCallback(callback));
}
exports.closeSync = function() {
	java.closeSync(arguments[0]);
}
exports.createReadStream = function(path, options) {
	return ReadStream(path, options, this);
}
exports.createWriteStream = function(path, options) {
	return WriteStream(path, options, this);
}
exports.exists = function(path, callback) {
	java.exists(translatePath(path), function(obj) {
		var list = Java.from(obj);
		callback(list[0] ? false : true);
	});
}
exports.existsSync = function(path) {
	return java.existsSync(translatePath(path));
}
exports.fchmod = function(fd, mode, callback) {
	java.fchmod(fd, mode, processCallback(callback));
}
exports.fchmodSync = function(fd, mode) {
	java.fchmodSync(fd, mode);
}
exports.fchown = function(fd, uid, gid, callback) {
	java.fchown(fd, uid, gid, processCallback(callback));
}
exports.fchownSync = function(fd, uid, gid) {
	java.fchownSync(fd, uid, gid);
}
exports.fdatasync = function(fd, callback) {
	java.fdatasync(fd, processCallback(callback));
}
exports.fdatasyncSync = function(fd) {
	java.fdatasyncSync(fd);
}
exports.fstat = function(fd, callback) {
	java.fstat(fd, processCallback(callback, function(r) {
			list[1] = new Stats(list[1]);
			return list;
		}));
}
exports.fstatSync = function(fd) {
	return new Stats(java.fstatSync(fd));
}
exports.fsync = function(fd, callback) {
	java.fsync(fd, processCallback(callback));
}
exports.fsyncSync = function(fd) {
	java.fsyncSync(fd);
}
exports.ftruncate = function(fd, len, callback) {
	java.ftruncate(fd, len, processCallback(callback));
}
exports.ftruncateSync = function(fd, len) {
	java.ftruncateSync(fd, len);
}
exports.futimes = function(fd, atime, mtime, callback) {
	java.futimes(fd, timeToSecond(atime), timeToSecond(mtime), processCallback(callback));
}
exports.futimesSync = function(fd, atime, mtime) {
	java.futimesSync(fd, timeToSecond(atime), timeToSecond(mtime));
}
exports.lchmod = function(fd, mode, callback) {
	java.lchmod(fd, mode, processCallback(callback));
}
exports.lchmodSync = function(fd, mode) {
	java.lchmodSync(fd, mode);
}
exports.lchown = function(fd, uid, gid, callback) {
	java.lchown(fd, uid, gid, processCallback(callback));
}
exports.lchownSync = function(fd, uid, gid) {
	java.lchownSync(fd, uid, gid);
}
exports.lstat = function(path, callback) {
	java.lstat(translatePath(path), processCallback(callback, function(list) {
			list[1] = new Stats(list[1]);
			return list;
		}));
}
exports.lstatSync = function(path) {
	return new Stats(java.lstatSync(translatePath(path)));
}
exports.mkdir = function(path, callback) {
	java.mkdir(translatePath(path), processCallback(callback));
}
exports.mkdirSync = function(path) {
	java.mkdirSync(translatePath(path));
}
exports.mkdtemp = function(prefix) {
	java.mkdtemp(prefix, processCallback(arguments[arguments.length - 1]));
}
exports.mkdtempSync = function(prefix) {
	return java.mkdtempSync(prefix);
}
exports.open = function() {
	var mode = arguments.length == 4 ? arguments[2] : 0666;
	var callback = arguments[arguments.length - 1];
	java.open(translatePath(arguments[0]), arguments[1], mode, processCallback(callback));
}
exports.openSync = function() {
	var mode = arguments.length == 3 ? arguments[2] : 0666;
	return java.openSync(translatePath(arguments[0]), arguments[1], mode);
}
exports.read = function(fd, buffer, offset, length, position, callback) {
	java.read(fd, buffer, offset, length, position, processCallback(callback, function(r) {
			r.push(buffer);
			return r;
		}));
}
exports.readdir = function(path) {
	var callback = arguments[arguments.length - 1];
	java.readdir(translatePath(path), processCallback(callback, function(r) {
			r[1] = Java.from(r[1]);
			return r;
		}));
}
exports.readdirSync = function(path) {
	return Java.from(java.readdirSync(translatePath(path)));
}
exports.readFile = function() {
	var callback = arguments[arguments.length - 1];
	var encoding = arguments.length == 2 ? null : arguments[1]; 
	java.readFile(translatePath(arguments[0]), processCallback(callback, Buffer.isEncoding(encoding) ? function(r) {
					r[1] = r[1].toString(encoding);
					return r;
				} : null));
}
exports.readFileSync = function() {
	var content = java.readFileSync(translatePath(arguments[0]));
	return Buffer.isEncoding(arguments[1]) ? content.toString(arguments[1]) : content;
}
exports.readlink = function() {
	var needbuffer = arguments.length > 2 && arguments[1] === 'buffer';
	var callback = arguments[arguments.length - 1];
	java.readlink(translatePath(arguments[0]), processCallback(callback, needbuffer ? function(r) {
			r[1] = new Buffer(r[1]);
			return r;
		} : null));
}
exports.readlinkSync = function() {
	var link = java.readlinkSync(translatePath(arguments[0]));
	return arguments[1] === 'buffer' ? new Buffer(link) : link;
}
exports.readSync = function(fd, buffer, offset, length, position) {
	return java.readSync(fd, buffer, offset, length, position);
}
exports.realpath = function() {
	var needbuffer = arguments.length > 2 && arguments[1] === 'buffer';
	var callback = arguments[arguments.length - 1];
	java.realpath(translatePath(arguments[0]), processCallback(callback, needbuffer ? function(r) {
			r[1] = new Buffer(r[1]);
			return r;
		} : null));
}
exports.realpathSync = function() {
	var path = java.realpathSync(translatePath(arguments[0]));
	return arguments[1] === 'buffer' ? new Buffer(path) : path;
}
exports.rename = function(oldPath, newPath, callback) {
	java.rename(translatePath(oldPath), translatePath(newPath), processCallback(callback));
}
exports.renameSync = function(oldPath, newPath) {
	java.renameSync(translatePath(oldPath), translatePath(newPath));
}
exports.rmdir = function(path, callback) {
	java.rmdir(translatePath(path), processCallback(callback));
}
exports.rmdirSync = function(path) {
	java.rmdirSync(translatePath(path));
}
exports.stat = function(path, callback) {
	java.stat(translatePath(path), processCallback(callback, function(list) {
			list[1] = new Stats(list[1]);
			return list;
		}));
}
exports.statSync = function(path) {
	return java.statSync(translatePath(path));
}
exports.symlink = function(target, path, callback) {
	java.symlink(translatePath(target), translatePath(path), processCallback(callback));
}
exports.symlinkSync = function(target, path) {
	java.symlinkSync(translatePath(target), translatePath(path));
}
exports.truncate = function() {
	var len = arguments.length == 2 ? 0 : arguments[1];
	var callback = arguments[arguments.length - 1];
	java.truncate(translatePath(arguments[0]), len ? len : 0, processCallback(callback));
}
exports.truncateSync = function(path, len) {
	java.truncateSync(translatePath(path), len ? len : 0);
}
exports.unlink = function(path, callback) {
	java.unlink(translatePath(path), processCallback(callback));
}
exports.unlinkSync = function(path) {
	java.unlinkSync(translatePath(path));
}
exports.unwatchFile = function() {
	java.unwatchFile(translatePath(arguments[0]), arguments[1] ? arguments[1] : null);
}
exports.utimes = function(path, atime, mtime, callback) {
	java.utimes(translatePath(path), timeToSecond(atime), timeToSecond(mtime), processCallback(callback));
}
exports.utimesSync = function(path, atime, mtime) {
	java.utimesSync(translatePath(path), timeToSecond(atime), timeToSecond(mtime));
}
exports.watch = function() {
	var filename = translatePath(arguments[0]);
	var callback = arguments[arguments.length - 1];
	var persistent;
	var needbuffer;
	if (arguments.length == 2) {
		persistent = true;
		needbuffer = false;
	} else {
		var opts = arguments[1];
		persistent = opts.persistent;
		needbuffer = opts.encoding === 'buffer';
	}
	var watcher = new FSWatcher(java.watch(filename, persistent, processCallback(function(err, eventType, filename) {
			if (err)
				watcher.emit('error', err);
			else
				watcher.emit('change', eventType, needbuffer ? new Buffer(filename) : filename);
		})));
	return watcher.on('change', callback);
}
exports.watchFile = function() {
	var filename = translatePath(arguments[0]);
	var listener = arguments[arguments.length - 1];
	var persistent;
	var interval;
	if (arguments.length == 2) {
		persistent = true;
		interval = 5007;
	} else {
		persistent = arguments[1].persistent;
		interval = arguments[1].interval;
	}
	var prev;
	var curr = java.watchFileStats(filename);
	if (!curr.isValid)
		listener(curr, curr);
	var timeout = setInterval(function() {
		prev = curr;
		curr = java.watchFileStats(filename);
		if (curr.mtime.getTime() != prev.mtime.getTime())
			listener(curr, prev);
	}, interval);
	if (!persistent)
		timeout.unref();
	java.watchFile(filename, listener, timeout);
}
exports.write = function() {
	var args = [];
	for ( var i in arguments)
		args.push(arguments[i]);
	var callback = args.pop();
	if (args[1] instanceof Buffer) {
		java.write(args[0], args[1], args[2], args[3], args[4] ? args[4] : null, processCallback(callback, function(r) {
						r.push(args[1]);
						return r;
					}));
	} else {
		java.write(args[0], toBuffer(args[1], args[3]), args[2] ? args[2] : null, processCallback(callback, function(r) {
						r.push(args[1]);
						return r;
					}));
	}
}
exports.writeFile = function() {
	var encoding = arguments.length == 4 ? arguments[2] : 'utf8';
	var callback = arguments[arguments.length - 1];
	java.writeFile(translatePath(arguments[0]), toBuffer(arguments[1], encoding), processCallback(callback));
}
exports.writeFileSync = function() {
	java.writeFileSync(translatePath(arguments[0]),	toBuffer(arguments[1], arguments[2]));
}
exports.writeSync = function() {
	return arguments[1] instanceof Buffer 
			? java.writeSync(arguments[0], arguments[1], arguments[2], arguments[3], arguments[4] ? arguments[4] : null) 
			: java.writeSync(arguments[0], toBuffer(arguments[1], arguments[3]), arguments[2] ? arguments[2] : null);
}
Object.defineProperty(exports, 'constants', {
	get : function() {
		return java.constants;
	},
	enumerable : true
});