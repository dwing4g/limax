var EventEmitter = require('events');
var util = require('util');
function Writable(options, ee) {
	if (!(this instanceof Writable) && !(this instanceof Duplex))
		return new Writable(options);
	var highWaterMark = 16384;
	var decodeStrings = true;
	var objectMode = false;
	if (options) {
		if (typeof(options.objectMode) === 'boolean')
			objectMode = options.objectMode;
		if (typeof(options.highWaterMark) === 'number')
			highWaterMark = options.highWaterMark;
		else if (objectMode)
			highWaterMark = 16;
		if (typeof(options.decodeStrings) === 'boolean')
			decodeStrings = options.decodeStrings;
		if (typeof(options.write) === 'function')
			this._write = options.write;
		if (typeof(options.writev) === 'function')
			this._writev = options.writev;
	}
	if (!(ee instanceof EventEmitter))
		ee = EventEmitter.call(this);
	var self = this;
	var defaultEncoding = 'utf8';
	var length = 0;
	var end = false;
	var cork = 0;
	var corkarray;
	var corklength;
	var corkcallback = [];
	var pending = [];
	var schedule = function(op) {
		if (op) {
			pending.push(op);
			if (pending.length === 1)
				op();
		} else {
			pending.shift();
			if (pending.length > 0)
				pending[0]();
		}
		if (end)
			end();
	}
	var writecallback = function(callback, writtenBytes) {
		return function(e) {
			if (callback)
				callback(e);
			if (e) {
				ee.emit('error', e);
			} else {
				length -= writtenBytes;
				if (!end && length < highWaterMark)
					ee.emit('drain');
				schedule();
			}
		}
	}
	var writechunk = function(chunk, encoding, callback) {
		var chunklength;
		if (objectMode) {
			chunklength = 1;
		} else if (chunk instanceof Buffer) {
			chunklength = chunk.length;
		} else if (typeof(chunk) === 'string') {
			if (decodeStrings) {
				if (encoding === undefined) {
					chunk = new Buffer(chunk, encoding = defaultEncoding);
				} else if (Buffer.isEncoding(encoding)) {
					chunk = new Buffer(chunk, encoding);
				} else {
					ee.emit('error', new Error('write with invalid encoding: ' + encoding));
					return;
				}
			}
			chunklength = chunk.length;
		} else {
			ee.emit('error', new Error('write object to none object stream'));
			return;
		}
		length += chunklength;
		if (cork > 0) {
			corkarray.push({chunk : chunk, encoding : encoding});
			corklength += chunklength;
			if (callback)
				corkcallback = callback;
		} else {
			schedule(function() { self._write(chunk, encoding, writecallback(callback, chunklength)); });
		}
		return length < highWaterMark;
	}
	this.cork = function() {
		if (cork++ > 0)
			return;
		corkarray = [];
		corklength = 0;
		corkcallback = null;
	}
	this.end = function() {
		if (end) {
			ee.emit('error', new Error('end twice'));
			return;
		}
		end = function() {
			ee.emit('finish');
		};
		var chunk;
		var encoding = defaultEncoding;
		var callback = null;
		switch(arguments.length) {
		case 0:
			if (!pending.length)
				end();
			return;
		case 1:
			if (typeof(arguments[0]) === 'function') {
				ee.once('finish', arguments[0]);
				if (!pending.length)
					end();
				return;
			}
			else
				chunk = arguments[0];
			break;
		case 2:
			chunk = arguments[0];
			if (Buffer.isEncoding(arguments[1]))
				encoding = arguments[1];
			else if (typeof(arguments[1]) === 'function')
				callback = arguments[1];
			break;
		case 3:
			chunk = arguments[0];
			if (Buffer.isEncoding(arguments[1]))
				encoding = arguments[1];
			if (typeof(arguments[2]) === 'function')
				callback = arguments[2];
		}
		writechunk(chunk, encoding, function(e) {
			if (callback)
				callback(e);
		});
		if (cork > 0)
			schedule(function() { self._writev(corkarray, writecallback(corkcallback, corklength)); });
	}
	this.setDefaultEncoding = function(encoding) {
		if (Buffer.isEncoding(encoding))
			defaultEncoding = encoding;
		return this;
	}
	this.uncork = function() {
		if (--cork > 0)
			return;
		schedule(function() { self._writev(corkarray, writecallback(corkcallback, corklength)); });
	}
	this.write = function() {
		if (end) {
			ee.emit('error', new Error('write to ended stream'));
			return;
		}
		var chunk = arguments[0];
		var encoding = defaultEncoding;
		var callback = null;
		switch(arguments.length) {
		case 2:
			if (Buffer.isEncoding(arguments[1]))
				encoding = arguments[1];
			else if (typeof(arguments[1]) === 'function')
				callback = arguments[1];
			break;
		case 3:
			if (Buffer.isEncoding(arguments[1]))
				encoding = arguments[1];
			if (typeof(arguments[2]) === 'function')
				callback = arguments[2];
		}
		return writechunk(chunk, encoding, callback);
	}
	return this;
}
util.inherits(Writable, EventEmitter);
function Readable(options, ee) {
	if (!(this instanceof Readable))
		return new Readable(options);
	var highWaterMark = 16384;
	var encoding = null;
	var objectMode = false;
	if (options) {
		if (typeof(options.objectMode) === 'boolean')
			objectMode = options.objectMode;
		if (typeof(options.highWaterMark) === 'number')
			highWaterMark = options.highWaterMark;
		else if (objectMode)
			highWaterMark = 16;
		if (Buffer.isEncoding(options.encoding))
			encoding = options.encoding;
		if (typeof(options.read) === 'function')
			this._read = options.read;
	}
	options = {
		encoding : encoding,
	};
	if (!(ee instanceof EventEmitter))
		ee = EventEmitter.call(this);
	var self = this;
	var encoding = null;
	var destinations = [];
	var eof = false;
	var pause = 4;
	var buffer = [];
	var accumulate = function() {
		if (objectMode)
			return buffer.length;
		var length = 0;
		for each(var b in buffer)
			length += b.length;
		return length;
	}
	var moredata = 0;
	var fill = function() {
		if (moredata)
			return;
		moredata = highWaterMark;
		var acc = accumulate();
		if (!eof && acc < highWaterMark)
			self._read(highWaterMark - acc);
	}
	var transform = function(chunk) {
		if (objectMode)
			return chunk;
		if (encoding != null)
			return chunk instanceof Buffer ? chunk.toString(encoding) : chunk;
		if (chunk instanceof Buffer)
			return chunk;
		throw new Error('FUCK!!! node.js specification defect, push string and need Buffer, how to encode?');
	}
	var flush = function() {
		var flowing = true;
		for (var chunk; chunk = buffer.shift();) {
			ee.emit('data', transform(chunk));
			for each(var dest in destinations)
				if (!dest.write(chunk))
					flowing = false;
			if (!flowing) {
				pause |= 2;
				return;
			}
		}
		if (!eof)
			return;
		ee.emit('end');
		for each(var dest in destinations)
			dest.end();
	}
	var drain = function() {
		if (pause & 1)
			return;
		for each(var dest in destinations)
			if (!dest.flowing)
				return;
		pause &= ~2;
		flush();
		fill();
	}
	this.isPaused = function() {
		return pause != 0;
	}
	this.pause = function() {
		pause |= 1;
		return this;
	}
	this.pipe = function(destination, options) {
		if (!destination.write) {
			this.emit('error', new Error('pipe to non Writable'));
			return;
		}
		var end = options && typeof(options.end) === 'boolean' ? options.end : true;
		if (eof) {
			if (end)
				destination.end();
			return;
		}
		destinations.push({
			write : function(chunk) {
				var self = this;
				if (self.flowing = destination.write(chunk))
					return true;
				destination.once('drain', function() {
					self.flowing = true;
					drain();
				});
				return false;
			}, 
			end : function() {
				if (end)
					destination.end();
			},
			flowing : true,
			destination : destination,
		});
		destination.emit('pipe', this);
		pause &= ~4;
		fill();
		return destination;
	}
	this.read = function(size) {
		if (buffer.length == 0) {
			if (eof && (pause & 4))
				setImmediate(ee.emit, 'end');
			return null;
		}
		setImmediate(fill);
		if (objectMode)
			return buffer.shift();
		if (encoding != null) {
			var s = '';
			for each(var chunk in buffer)
				s += transform(chunk);
			if (size == undefined || eof) {
				buffer = [];
				return s;
			}
			var size = parseInt(size);
			if (s.length < size) {
				buffer = [s];
				return null;
			}
			if (s.length == size) {
				buffer = [];
				return s;
			}
			buffer = [s.substring(size, s.length)];
			return s.substring(0, size);
		} else {
			var a = [];
			var b;
			var l = 0;
			for each(var chunk in buffer) {
				b = transform(chunk);
				l += b.length;
				a.push(b);
			}
			b = Buffer.concat(a, l);
			if (size == null || eof) {
				buffer = [];
				return b;
			}
			var size = parseInt(size);
			if (b.length < size) {
				buffer = [b];
				return null;
			}
			if (b.length == size) {
				buffer = [];
				return b;
			}
			buffer = [b.slice(size, b.length)];
			return b.slice(0, size);
		}
	}
	this.resume = function() {
		if (pause & 1) {
			pause &= ~1;
			flush();
		}
		return this;
	}
	this.setEncoding = function() {
		if (Buffer.isEncoding(arguments[0]))
			encoding = arguments[0];
		else
			ee.emit('error', new Error('set invalid encoding: ' + arguments[0]));
		return this;
	}
	this.unpipe = function() {
		if (arguments.length == 0) {
			for each(var dest in destinations)
				dest.emit('unpipe', this);
			destinations = [];
		} else {
			var list = [];
			for (var i in destinations)
				if (destinations[i].destination === arguments[0]) {
					arguments[0].emit('unpipe', this);
					list.push(i);
				}
			for (var i; i = list.pop(); destinations.splice(i, 1));
		}
		drain();
		return this;
	}
	this.unshift = function(chunk) {
		if (eof) {
			ee.emit('error', new Error('unshift on eof stream'));
			return;
		}
		buffer.unshift(chunk);
		return this;
	}
	this.push = function(chunk, encoding) {
		if (eof) {
			ee.emit('error', new Error('push chunk on eof stream ' + chunk));
			return;
		}
		if (chunk == null) {
			eof = true;
		} else if (objectMode || chunk instanceof Buffer) {
		} else if (typeof(chunk) === 'string') {
			if (encoding === undefined) {
				if (options.encoding)
					chunk = new Buffer(chunk, options.encoding);
			} else if (Buffer.isEncoding(encoding)) {
				chunk = new Buffer(chunk, encoding);
			} else {
				ee.emit('error', new Error('push with invalid encoding: ' + encoding));
				return;
			}
		} else {
			ee.emit('error', new Error('push object to none object stream'));
			return;
		}
		if (!eof)
			buffer.push(chunk);
		setImmediate(ee.emit, 'readable');
		if (!pause)
			flush();
		var acc = accumulate();
		return moredata = acc < highWaterMark ? highWaterMark - acc : 0;
	}
	this._readableState = {
		get flowing() {
			return pause & 4 ? null : pause == 0;
		}	
	};
	ee.on('newListener', function(event) {
		if (event === 'data') {
			pause &= ~4;
			fill();
		} else if (event === 'readable') {
			fill();
		}
	});
	return this;
}
util.inherits(Readable, EventEmitter);
function Duplex(options) {
	if (!(this instanceof Duplex))
		return new Duplex(options);
	var readable;
	var writable;
	var allowHalfOpen;
	var ee = EventEmitter.call(this);
	if (options) {
		allowHalfOpen = typeof(options.allowHalfOpen) === 'boolean' ? options.allowHalfOpen : true;
		options.objectMode = typeof(options.readableObjectMode) === 'boolean' ? options.readableObjectMode : false;
		readable = Readable.call(this, options, ee);
		options.objectMode = typeof(options.writableObjectMode) === 'boolean' ? options.writableObjectMode : false;
		writable = Writable.call(this, options, ee);
		delete options.objectMode;
	} else {
		readable = Readable.call(this, options, ee);
		writable = Writable.call(this, options, ee);
		allowHalfOpen = true;
	}
}
util.inherits(Duplex, Readable);
function Transform(options) {
	if (!(this instanceof Transform))
		return new Transform(options);
	var ee = this;
	if (options) {
		if (typeof(options.transform) === 'function')
			this._transform = options.transform;
		if (typeof(options.flush) === 'function')
			this._flush = options.flush;
	}
	var self = this;
	var overflow = false;
	var delayed = [];
	var checkcallback = function(callback) {
		return function() {
			if (!overflow)
				callback();
			else
				delayed.push(callback);
		}
	}
	this._write = function(chunk, encoding, callback) {
		this._transform(chunk, encoding, checkcallback(callback));
	}
	this._writev = function(chunks, callback) {
		var count = chunks.length;
		for each(var chunk in chunks)
			this._transform(chunk.chunk, chunk.encoding, function(cb) {
				if (--count == 0)
					checkcallback(callback);
			});
	}
	this._read = function(size) {
		for each(var callback in delayed)
			callback();
		overflow = false;
		delayed = [];
	}
	Duplex.call(this, options);
	var push = this.push;
	this.push = function(chunk, encoding) {
		if(!push.call(this, chunk, encoding))
			overflow = true;
	}
	this.once('finish', function() {
		if (self._flush)
			self._flush(function() {
				self.push(null);
			});
		else
			self.push(null);
	});
}
util.inherits(Transform, Duplex);
function PassThrough(options) {
	if (!(this instanceof PassThrough))
		return new PassThrough(options);
	this._transform = function(chunk, encoding, callback) {
		this.push(chunk, encoding);
		callback();
	}
	Transform.call(this, options);
}
util.inherits(PassThrough, Transform);
exports.Readable = Readable
exports.Writable = Writable
exports.Duplex = Duplex
exports.Transform = Transform
exports.PassThrough = PassThrough
