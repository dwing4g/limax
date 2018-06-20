var InterruptedByTimeoutException = Java.type('java.nio.channels.InterruptedByTimeoutException');
var AsynchronousCloseException = Java.type('java.nio.channels.AsynchronousCloseException');
var IPAddressUtil = Java.type('sun.net.util.IPAddressUtil');
var EventEmitter = require('events');
var Duplex = require('stream').Duplex;
var util = require('util');
var dns = require('dns');
function Server(options, connectionListener) {
	if (!(this instanceof Server))
		return new Server(options, connectionListener);
	var allowHalfOpen = false;
	var pauseOnConnect = false;
	var tls = null;
	if (options) {
		if (typeof(options.allowHalfOpen) === 'boolean')
			allowHalfOpen = options.allowHalfOpen;
		if (typeof(options.pauseOnConnect) === 'boolean')
			pauseOnConnect = options.pauseOnConnect;
		if (options.tls)
			tls = options.tls;
	}
	var ee = EventEmitter.call(this);
	var server = java.createServer(this);
	var close = false;
	if (typeof(connectionListener) === 'function')
		ee.on('connection', connectionListener);
	this.address = function() {
		var list = Java.from(server.address());
		return {
			port : list[0],
			family : list[1],
			address : list[2]
		}
	}
	this.close = function() {
		if (!close)
			close = true;
		if (typeof(arguments[0]) === 'function')
			ee.once('close', arguments[0]);
		if (server.isListening())
			server.close();
		else
			ee.emit('error', new Error('server was not open'));
	}
	this.getConnections = function(callback) {
		server.getConnections(function(obj) {
			var list = Java.from(obj);
			callback(list[0], list[1]);
		})
	}
	var accept = function(obj) {
		var list = Java.from(obj);
		if (!list[0]) {
			ee.emit('connection', new Socket({
				owner : server,
				socket : list[1],
				allowHalfOpen : allowHalfOpen,
				pauseOnConnect : pauseOnConnect,
				tls : tls
			}));
		}
	}
	this.listen = function() {
		var port = 0;
		var host = null;
		var backlog = 128;
		if (typeof(arguments[arguments.length - 1]) === 'function') {
			ee.once('listening', arguments[arguments.length - 1])
		}
		if (typeof(arguments[0]) === 'number') {
			port = arguments[0];
			switch(typeof(arguments[1])) {
			case 'string':
				host = arguments[1];
				if (typeof(arguments[2]) === 'number')
					backlog = arguments[2];
				break;
			case 'number':
				backlog = arguments[2];
			}
		} else if (typeof(arguments[0]) === 'object' && arguments[0]){
			var o = arguments[0];
			if (typeof(o.port) === 'number')
				port = o.port;
			if (typeof(o.host) === 'string')
				host = o.host;
			if (typeof(o.backlog) === 'number')
				backlog = o.backlog;
		}
		server.listen(port, host, backlog, accept);
	}
	Object.defineProperty(this, 'listening', {
		get : function() {
			return server.isListening();
		}
	});
	Object.defineProperty(this, 'maxConnections', {
		get : function() {
			return server.getMaxConnections();
		},
		set : function(maxConnections) {
			server.setMaxConnections(maxConnections);
		}
	});
	this.ref = function() {
		server.ref();
		return this;
	}
	this.unref = function() {
		server.unref();
		return this;
	}
}
util.inherits(Server, EventEmitter);
function Socket(options) {
	if (!(this instanceof Socket))
		return new Socket(options);
	var ee = this;
	var socket;
	var allowHalfOpen = false;
	var pauseOnConnect = false;
	var tls = null;
	if (options) {
		if (options.socket)
			socket = java.createSocket(options.socket, options.owner);
		if (typeof(options.allowHalfOpen) === 'boolean')
			allowHalfOpen = options.allowHalfOpen;
		if (typeof(options.pauseOnConnect) === 'boolean')
			pauseOnConnect = options.pauseOnConnect;
		if (options.tls)
			tls = options.tls;
	}
	var self = this;
	this.bytesWritten = 0;
	this.bytesRead = 0;
	var pendingread;
	this._read = function(size) {
		if (typeof(size) !== 'number')
			size = null;
		if (socket == null) {
			pendingread = size;
		} else {
			socket.read(size, function(obj) {
				setImmediate(function() {
					var list = Java.from(obj);
					if (list[0]) {
						self.push(null);
						self.destroy(list[0]);
					} else {
						var bytesRead = list[1];
						if (bytesRead != -1) {
							self.bytesRead += bytesRead;
							var capacity = self.push(list[2]);
							if (capacity)
								self._read(capacity);
						} else {
							self.push(null);
							if (allowHalfOpen) {
								socket.shutdownInput();
							} else {
								self.destroy();
							}
						}
					}
				});
			});
		}
	}
	this._write = function(chunk, encoding, callback) {
		var length = chunk.length;
		socket.write(chunk, function(obj) {
			var err = Java.from(obj)[0];
			if (err) {
				self.destroy(err);
				callback(err);
			} else {
				self.bytesWritten += length;
				callback();
			}
		});
	}
	this._writev = function(chunks, callback) {
		var buffers = [];
		var length = 0;
		for each(var chunk in chunks) {
			buffers.push(chunk.chunk);
			length += chunk.length;
		}
		socket.writev(Java.to(buffers), function(obj) {
			var err = Java.from(obj)[0];
			if (err) {
				self.destroy(err);
				callback(err);
			} else {
				self.bytesWritten += length;
				callback();
			}
		});
	}
	this.address = function() {
		var list = Java.from(socket.address());
		return {
			port : list[0],
			family : list[1],
			address : list[2]
		}
	}
	var connect = function(port, host, localAddress, localPort, tls) {
		self.connecting = true;
		java.connect(port, host, localAddress, localPort, function(obj) {
			var list = Java.from(obj);
			if (list[0]) {
				setImmediate(ee.emit, 'error', list[0]);
			} else {
				socket = list[1];
				if (tls)
					self.starttls(tls);
				ee.emit('connect');
			}
			self.connecting = false;
			if (pendingread != undefined) {
				self._read(pendingread);
			}
		});
	}
	this.connect = function(options, connectListener) {
		var options;
		var connectListener;
		if (typeof(arguments[0]) === 'object' && arguments[0]) {
			options = arguments[0];
			connectListener = arguments[1];
		} else if (typeof(arguments[0]) === 'number') {
			options = { port : arguments[0] };
			switch(arguments.length) {
			case 2:
				if (typeof(arguments[1]) === 'string')
					options.host = arguments[1];
				else
					connectListener = arguments[1];
				break;
			default:
				options.host = arguments[1];
				connectListener = arguments[2];
				break;
			}
		}
		var port;
		var host = null;
		var localAddress = null;
		var localPort = null;
		var tls = null;
		var lookup = dns.lookup;
		if (options.port)
			port = options.port;
		if (options.host)
			host = options.host;
		if (options.localAddress)
			localAddress = options.localAddress;
		if (options.localPort)
			localPort = options.localPort;
		if (options.tls)
			tls = options.tls;
		if (host == null || exports.isIP(host)) {
			connect(port, host, localAddress, localPort, tls);
		} else {
			var dnsopt = {};
			if (options.family)
				dnsopt.family = options.family;
			if (options.hints)
				dnsopt.hints = options.hints;
			if (options.lookup)
				lookup = options.lookup;
			lookup(host, dnsopt, function(err, address, family) {
				ee.emit('lookup', err, address, family, host);
				connect(port, host, localAddress, localPort, tls);
			});
		}
		if (typeof(connectListener) === 'function') {
			ee.once('connect', connectListener);
		}
	}
	this.connecting = false;
	this.destroy = function(exception) {
		if (self.destroyed)
			return;
		self.destroyed = true;
		if (exception) {
			if (exception instanceof InterruptedByTimeoutException)
				ee.emit('timeout');
			else if (!(exception instanceof AsynchronousCloseException))
				ee.emit('error', exception);
		}
		if (socket) {
			socket.destroy();
			ee.emit('close', had_error);
		}
	}
	this.destroyed = false;
	Object.defineProperty(this, 'localAddress', {
		get : function() {
			return socket.getLocalAddress();
		}
	});
	Object.defineProperty(this, 'localPort', {
		get : function() {
			return socket.getLocalPort();
		}
	});
	Object.defineProperty(this, 'remoteAddress', {
		get : function() {
			return socket.getRemoteAddress();
		}
	});
	Object.defineProperty(this, 'remoteFamily', {
		get : function() {
			return socket.getRemoteFamily();
		}
	});
	Object.defineProperty(this, 'remotePort', {
		get : function() {
			return socket.getRemotePort();
		}
	});
	this.setKeepAlive = function(enable) {
		socket.setKeepAlive(typeof(enable) === 'boolean' && enable);
		return self;
	}
	this.setNoDelay = function(nodelay) {
		socket.setNoDelay(typeof(nodelay) === 'boolean' ? nodelay : true);
		return self;
	}
	var lasttimeoutcallback;
	this.setTimeout = function(timeout, callback) {
		if (typeof(timeout) === 'number')
			socket.setTimeout(timeout);
		if (typeof(callback) === 'function') {
			if (lasttimeoutcallback)
				ee.removeListener('timeout', lasttimeoutcallback);
			ee.on('timeout', lasttimeoutcallback = callback);
		}
		return self;
	}
	Duplex.call(this, options);
	var had_error = false;
	ee.once('error', function(e) {
		had_error = true;
		self.destroy();
	});
	ee.once('finish', function() {
		if (allowHalfOpen) {
			socket.shutdownOutput();
		} else {
			self.destroy();
		}
	});
	if (pauseOnConnect) {
		this.pause();
	}
	this.starttls = function(tls) {
		socket.starttls(tls, function(obj) {
			var args = Java.from(obj);
			args.unshift('tlsDown');
			ee.emit.apply(ee, args);
		}, function(obj) {
			var args = Java.from(obj);
			args.unshift('tlsHandshaked');
			if (args.length > 0) {
				self.tlsPeerPrincipal = args[0];
				self.tlsPeerCertificateChain = [];
				for (var i = 1; i < args.length; i++)
					self.tlsPeerCertificateChain.push(args[i]);
			}
			ee.emit.apply(ee, args);
		});
		ee.once('data', function() { });
	}
	this.stoptls = function(callback) {
		if (callback)
			ee.once('tlsDown', callback);
		socket.stoptls();
	}
	this.tlsrenegotiate = function() {
		socket.tlsrenegotiate();
	}
	this.ref = function() {
		socket.ref();
		return this;
	}
	this.unref = function() {
		socket.unref();
		return this;
	}
	if (tls && socket)
		this.starttls(tls);
}
util.inherits(Socket, Duplex);
var createConnection = function() {
	var args = [];
	for ( var i in arguments)
		args.push(arguments[i]);
	var socket = new Socket();
	socket.connect.apply(socket, args);
	return socket;
}
exports.Server = Server
exports.Socket = Socket
exports.connect = createConnection
exports.createConnection = createConnection
exports.createServer = function() {
	var options = null;
	var connectionListener = null;
	switch (arguments.length) {
	case 0:
		break;
	case 1:
		if (typeof(arguments[0]) === 'function') {
			connectionListener = arguments[0];
		} else if (typeof(arguments[0]) === 'object' && arguments[0]) {
			options = arguments[0];
		} 
		break;
	default:
		options = arguments[0];
		connectionListener = arguments[1];
	}
	return new Server(options, connectionListener);
}
exports.createTLS = function() {
	return java.createTLS(arguments[0]);
}
exports.isIP = function(input) {
	return typeof(input) === 'string' && exports.isIPv4(input) || exports.isIPv6(input);
}
exports.isIPv4 = function(input) {
	return typeof(input) === 'string' && IPAddressUtil.isIPv4LiteralAddress(input);
}
exports.isIPv6 = function(input) {
	return typeof(input) === 'string' && IPAddressUtil.isIPv6LiteralAddress(input);
}
