var util = require('util');
var net = require('net');
var url = require('url');
var EventEmitter = require("events");
var Readable = require('stream').Readable;
var Writable = require('stream').Writable;
var Transform = require('stream').Transform;
var PassThrough = require('stream').PassThrough;
var METHODS = [
        'ACL',
        'BIND',
        'CHECKOUT',
        'CONNECT',
        'COPY',
        'DELETE',
        'GET',
        'HEAD',
        'LINK',
        'LOCK',
        'M-SEARCH',
        'MERGE',
        'MKACTIVITY',
        'MKCALENDAR',
        'MKCOL',
        'MOVE',
        'NOTIFY',
        'OPTIONS',
        'PATCH',
        'POST',
        'PROPFIND',
        'PROPPATCH',
        'PURGE',
        'PUT',
        'REBIND',
        'REPORT',
        'SEARCH',
        'SUBSCRIBE',
        'TRACE',
        'UNBIND',
        'UNLINK',
        'UNLOCK',
        'UNSUBSCRIBE'
];
var STATUS_CODES = {
		'100' : 'Continue',
		'101' : 'Switching Protocols',
		'102' : 'Processing',
		'200' : 'OK',
		'201' : 'Created',
		'202' : 'Accepted',
		'203' : 'Non-Authoritative Information',
		'204' : 'No Content',
		'205' : 'Reset Content',
		'206' : 'Partial Content',
		'207' : 'Multi-Status',
		'208' : 'Already Reported',
		'226' : 'IM Used',
		'300' : 'Multiple Choices',
		'301' : 'Moved Permanently',
		'302' : 'Found',
		'303' : 'See Other',
		'304' : 'Not Modified',
		'305' : 'Use Proxy',
		'307' : 'Temporary Redirect',
		'308' : 'Permanent Redirect',
		'400' : 'Bad Request',
		'401' : 'Unauthorized',
		'402' : 'Payment Required',
		'403' : 'Forbidden',
		'404' : 'Not Found',
		'405' : 'Method Not Allowed',
		'406' : 'Not Acceptable',
		'407' : 'Proxy Authentication Required',
		'408' : 'Request Timeout',
		'409' : 'Conflict',
		'410' : 'Gone',
		'411' : 'Length Required',
		'412' : 'Precondition Failed',
		'413' : 'Payload Too Large',
		'414' : 'URI Too Long',
		'415' : 'Unsupported Media Type',
		'416' : 'Range Not Satisfiable',
		'417' : 'Expectation Failed',
		'418' : 'I\'m a teapot',
		'421' : 'Misdirected Request',
		'422' : 'Unprocessable Entity',
		'423' : 'Locked',
		'424' : 'Failed Dependency',
		'425' : 'Unordered Collection',
		'426' : 'Upgrade Required',
		'428' : 'Precondition Required',
		'429' : 'Too Many Requests',
		'431' : 'Request Header Fields Too Large',
		'451' : 'Unavailable For Legal Reasons',
		'500' : 'Internal Server Error',
		'501' : 'Not Implemented',
		'502' : 'Bad Gateway',
		'503' : 'Service Unavailable',
		'504' : 'Gateway Timeout',
		'505' : 'HTTP Version Not Supported',
		'506' : 'Variant Also Negotiates',
		'507' : 'Insufficient Storage',
		'508' : 'Loop Detected',
		'509' : 'Bandwidth Limit Exceeded',
		'510' : 'Not Extended',
		'511' : 'Network Authentication Required' }
function Agent(options) {
	if (!(this instanceof Agent))
		return new Agent(options);
	var self = this;
	var impl = java.createAgent();
	this.impl = impl;
	this.keepAlive = false;
	var keepAliveMsecs = 60000;
	this.maxSockets = Infinity;
	this.maxFreeSockets= 256;
	if (typeof(options) === 'object' && options) {
		if (typeof(options.keepAlive) === 'boolean')
			this.keepAlive = options.keepAlive;
		if (typeof(options.keepAliveMsecs) === 'number')
			keepAliveMsecs = options.keepAliveMsecs;
		if (typeof(options.maxSockets) === 'number')
			this.maxSockets = options.maxSockets;
		if (typeof(options.maxFreeSockets) === 'number')
			this.maxFreeSockets = options.maxFreeSockets;
	}
	this.createConnection = function(options, callback) {
		var s = new net.Socket();
		s.once('error', function(e) {
			if (callback)
				callback(e);
		})
		s.once('connect', function() {
			if (callback)
				callback(null, s);
		})
		if (options.tls) {
			s.once('tlsDown', function(e) {
				if (e)
					s.destroy(e);
			})
		}
		s.connect(options);
		return s;
	}
	this.destroy = function() {
		for (var socket in Java.from(impl.getFreeSockets()))
			socket.destroy();
		for (var socket in Java.from(impl.getSockets()))
			socket.destroy();
		for (var request in Java.from(impl.getRequests()))
			request.abort();
		impl.reset();
	}
	var timeout;
	var updateTimer = function() {
		if (timeout)
			clearInterval(timeout);
		if (keepAliveMsecs <= 0)
			return;
		timeout = setInterval(function() {
			for (var socket in Java.from(impl.removeTimeoutFreeSockets(keepAliveMsecs)))
				socket.destroy();
		}, keepAliveMsecs);
		timeout.unref();
	}
	updateTimer();
	Object.defineProperty(this, 'keepAliveMsecs', {
		get : function() {
			return keepAliveMsecs;
		},
		set : function(msecs) {
			keepAliveMsecs = msecs;
			updateTimer();
		}
	});
	Object.defineProperty(this, 'maxFreeSockets', {
		get : function() {
			return impl.getMaxFreeSockets();
		},
		set : function(max) {
			impl.setMaxFreeSockets(max);
		}
	});
	Object.defineProperty(this, 'freeSockets', {
		get : function() {
			return Java.from(impl.getFreeSockets());
		}
	});
	this.getName = function(options) {
		var host = 'localhost';
		var port = '';
		var localAddress = '';
		var tlsname = '';
		if (options.host)
			host = options.host;
		if (options.port)
			port = options.port;
		if (options.localAddress)
			localAddress = options.localAddress;
		if (options.tlsname)
			tlsname = options.tlsname;
		return host + ':' + port + ':' + localAddress + ':' + tlsname;
	}
	Object.defineProperty(this, 'requests', {
		get : function() {
			return Java.from(impl.getRequests());
		}
	});
	Object.defineProperty(this, 'sockets', {
		get : function() {
			return Java.from(impl.getSockets());
		}
	});
}
var globalAgent = new Agent();
function ClientRequest(options, autoend) {
	if (!(this instanceof ClientRequest))
		return new ClientRequest(options, autoend);
	var self = this;
	var header = java.createHeader();
	var incomingMessage = null;
	var socket = null;
	var protocol = "http:";
	var hostname = 'localhost';
	var family = null;
	var port = 80;
	var localAddress = null;
	var socketPath;
	var method = 'GET';
	var path = '/';
	var headers = {};
	var agent = globalAgent;
	var createConnection = null;
	var timeout = 0;
	var persistent = true;
	var tls = null;
	var tlsname = '';
	if (typeof(options.protocol) === 'string')
		protocol = options.protocol;
	if (typeof(options.host) === 'string')
		header.set('Host', options.host)
	if (typeof(options.hostname) === 'string')
		hostname = options.hostname;
	if (options.family === 4 || options.family === 6)
		family = options.family;
	if (typeof(options.port) === 'number')
		port = options.port;
	if (typeof(options.localAddress) === 'string')
		localAddress = options.localAddress;
	if (typeof(options.socketPath) === 'string')
		socketPath = options.socketPath;
	if (typeof(options.method) === 'string')
		method = options.method.toUpperCase();
	if (typeof(options.path) === 'string')
		path = options.path;
	if (typeof(options.headers) === 'object' && options.headers) {
		for (var k in options.headers)
			header.set(k, options.headers[k]);
	}
	if (typeof(options.auth) === 'string')
		header.set('Authorization', 'Basic ' + new Buffer(options.auth, 'ascii').toString('base64'));
	if (options.agent === false) {
		createConnection = agent.createConnection;
		header.set('Connection', 'close');
		persistent = false;
	} else {
		if (options.agent instanceof Agent)
			agent = options.agent;
		createConnection = agent.createConnection;
	}
	header.setIfAbsent('Connection', 'keep-alive')
	if (options.agent === false && typeof(options.createConnection) === 'function')
		createConnection = options.createConnection;
	if (typeof(options.timeout) === 'number' && options.timeout < Infinity)
		timeout = options.timeout;
	if (options.tls) {
		tls = options.tls;
		tlsname = hostname;
	} else if (protocol.toLowerCase() === 'https:'){
		tls = net.createTLS(function(c) {
			c.addAllCA();
			c.setSNIHostName(hostname);
		});
		tlsname = hostname;
		port = 443;
	}
	var name = agent.getName({host : hostname, port : port, localAddress : localAddress, tlsname : tlsname});
	var delayed;
	var send = function(data, encoding, callback) {
		if (socket) {
			socket.write(data, encoding, callback);
		} else {
			if (delayed == null)
				delayed = [];
			delayed.push(function() { send(data, encoding, callback); } );
		}
	}
	this._write = function(chunk, encoding, callback) {
		send(chunk, encoding, callback);
	}
	this._writev = function(chunks, callback) {
		var count = chunks.length;
		for each(var chunk in chunks)
			this._write(chunk.chunk, chunk.encoding, function(cb) {
				if (--count == 0)
					callback();
			});
	}
	var ee = Writable.call(this);
	var aborted = false;
	this.abort = function() {
		if (aborted)
			return;
		aborted = true;
		ee.emit('abort');
		removeObject(agent.requests, name, this);
		if (incomingMessage)
			incomingMessage.emit('aborted');
		if (socket)
			socket.destroy();
	}
	var flushHeaders = false;
	this.flushHeaders = function() {
		if (flushHeaders)
			return;
		flushHeaders = true;
		var reqheader = header.format(method + ' ' + path + ' HTTP/1.1\r\n');
		if (tls) {
			send(reqheader.slice(0, reqheader.length - 2));
			send(new Buffer('\r\n'));
		} else {
			send(reqheader);
		}
	}
	this.setNoDelay = function() {
		if (socket)
			socket.setNoDelay(typeof(arguments[0]) === 'boolean' ? arguments[0] : true);
	}
	this.setSocketKeepAlive = function(enable) {
		if (socket)
			socket.setKeepAlive(enable)
	}
	this.setTimeout = function(timeout, callback) {
		if (socket)
			socket.setTimeout(timeout, callback);
	}
	var end = this.end;
	this.end = function() {
		var args = [];
		for (var i = 0; i < arguments.length; i++)
			args.push(arguments[i]);
		this.flushHeaders();
		end.apply(self, args);
	}
	ee.once('finish', function() {
		autoend = false;
	});
	this.__write = this.write;
	this.write = function() {
		this.flushHeaders();
		var data = arguments[0];
		var encoding = 'utf8';
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
		return this.__write(data, encoding, callback);
	}
	agent.impl.addRequest(name, this);
	if (socket = agent.impl.allocFreeSocket(name)) {
		socket.ref();
		ee.emit('socket', socket);
	} else {
		createConnection({
			port : port,
			host : hostname,
			localAddress : localAddress,
			family : family,
			tls : tls
		}, function(err, s) {
			if (err) {
				ee.emit('error', err);
				return;
			}
			socket = s;
			socket._agentRemove = false;
			socket.once('agentRemove', function() {
				socket._agentRemove = true;
			});
			socket.once('close', function() {
				agent.impl.removeFreeSocket(name, socket);
				agent.impl.removeSocket(name, socket);
			});
			ee.emit('socket', socket);
		});
	}
	var incomingHandle = function(incomingMessage, head) {
		if (incomingMessage.statusCode == 100) {
			ee.emit('continue');
		} else if (method === 'CONNECT' && incomingMessage.statusCode == 200) {
			socket.unpipe(socket._incomingfilter);
			socket._agentRemove = true;
			ee.emit('connect', incomingMessage, socket, head);
		} else if (method === 'GET' && incomingMessage.statusCode == 101){
			socket.unpipe(socket._incomingfilter);
			socket._agentRemove = true;
			ee.emit('upgrade', incomingMessage, socket, head);
		} else {
			ee.emit('response', incomingMessage);
		}
	};
	ee.once('socket', function(s) {
		socket = s;
		agent.impl.removeRequest(name, self);
		agent.impl.addSocket(name, socket);
		if (socket._incomingfilter) {
			socket._incomingfilter.callback = incomingHandle;
		} else {
			socket.pipe(socket._incomingfilter = new IncomingFilter(socket, incomingHandle, false));
		}
		socket.setTimeout(timeout);
		if (delayed) {
			for each(var op in delayed)
				op();
			delayed = null;
		} 
		if (autoend)
			self.end();
	})
	ee.once('response', function(res) {
		incomingMessage = res;
		res.once('end', function() {
			setImmediate(function() {
				incomingMessage = null;
				if (socket.destroyed)
					return;
				if (persistent && !socket._agentRemove) {
					agent.impl.removeSocket(name, socket);
					agent.impl.addFreeSocket(name, socket);
					socket.unref();
				} else {
					socket.destroy();
				}
			});
		});
	});
}
util.inherits(ClientRequest, Writable);
function Server(tls) {
	if (!(this instanceof Server))
		return new Server(tls);
	var ee = EventEmitter.call(this);
	var self = this;
	var server = net.createServer( {tls : tls} );
	server.once('close', function() { ee.emit('close') });
	server.on('connection', function(socket) {
		ee.emit('connection', socket);
		socket.once('error', function(e) {
			ee.emit('clientError', e, socket);
		});
		if (tls)
			socket.once('tlsDown', function(e) {
				if (e)
					socket.destroy(e);
			})
		var filter;
		socket.pipe(filter = new IncomingFilter(socket, function(incomingMessage, head) {
			switch(incomingMessage.method) {
			case 'CONNECT':
				if (ee.listenerCount('connect') > 0) {
					socket.unpipe(filter);
					ee.emit('connect', incomingMessage, socket, head);
				}
				else
					socket.destroy();
				break;
			case 'GET':
				if (java.ignoreCaseIncludes(incomingMessage.headers['connection'], 'upgrade')) {
					if (ee.listenerCount('upgrade') > 0) {
						socket.unpipe(filter);
						ee.emit('upgrade', incomingMessage, socket, head);
					}
					else
						socket.destroy();
					break;
				}
			case 'POST':
				var serverResponse = new ServerResponse(incomingMessage);
				var expect = incomingMessage.headers['Expect'];
				if (expect) {
					if (expect.toLowerCase() === '100-continue') {
						if (ee.listenerCount('checkContinue') > 0) {
							ee.emit('checkContinue', incomingMessage, serverResponse);
						} else {
							serverResponse.writeContinue();
							serverResponse.end();
						}
					} else {
						if (ee.listenerCount('checkExpectation') > 0) {
							ee.emit('checkExpectation', incomingMessage, serverResponse);
						} else {
							serverResponse.writeHead(417, { Connection : 'close' });
							serverResponse.end();
						}
					}
				} else {
					ee.emit('request', incomingMessage, serverResponse);
				}
				break;
			default:
				socket.destroy(new Error('unsupported method ' + im.method));
			}
		}, true));
	});
	this.close = function(callback) {
		server.close(callback);
	}
	this.listen = function() {
		var port = 80;
		var hostname = 'localhost';
		var backlog = 511;
		var callback = null;
		if (arguments.length > 0) {
			port = arguments[0];
			switch (arguments.length) {
			case 2:
				switch(typeof(arguments[1]))
				{
				case 'string':
					hostname = arguments[1];
					break;
				case 'number':
					backlog = arguments[1];
					break;
				case 'function':
					callback = arguments[1];
				}
				break;
			case 3:
				switch(typeof(arguments[1]))
				{
				case 'string':
					hostname = arguments[1];
					if (typeof(arguments[2]) === 'number')
						backlog = arguments[2];
					else if(typeof(arguments[2]) === 'function')
						callback = arguments[2];
					break;
				case 'number':
					backlog = arguments[1];
					if (typeof(arguments[2]) === 'function')
						callback = arguments[2];
				}
				break;
			case 4:
				if (typeof(arguments[1]) === 'string')
					hostname = arguments[1];
				if (typeof(arguments[2]) === 'number')
					backlog = arguments[2];
				if (typeof(arguments[3]) === 'function')
					callback = arguments[3];
			}
		}
		server.listen(port, hostname, backlog, callback);
	}
	Object.defineProperty(this, 'listening', {
		get : function() {
			return server.listening;
		}
	});
	this.maxHeadersCount = 1000;
	this.setTimeout = function(msecs, callback) {
		server.setTimeout(msecs, callback);
		return self;
	}
	this.timeout = 120000;
}
util.inherits(Server, EventEmitter);
function ServerResponse(request) {
	if (!(this instanceof ServerResponse))
		return new ServerResponse(request);
	var self = this;
	var contentLength = null;
	var persistent = request.httpVersion > '1.1';
	var header = java.createHeader();
	var trailers;
	var socket = request.socket;
	var send = function(data, encoding, callback) {
		socket.write(data, encoding, callback);
	}
	this._write = function(chunk, encoding, callback) {
		if (persistent) {
			if (contentLength === null) {
				send(new Buffer(chunk.length.toString(16) + '\r\n', 'ascii'), null, null);
				send(chunk, encoding, callback);
				send(new Buffer('\r\n', 'ascii'), null, null);
			} else {
				contentLength -= chunk.length;
				send(chunk, encoding, callback);
			}
		} else {
			send(chunk, encoding, callback);
		}
	}
	this._writev = function(chunks, callback) {
		var count = chunks.length;
		for each(var chunk in chunks)
			this._write(chunk.chunk, chunk.encoding, function(cb) {
				if (--count == 0)
					callback();
			});
	}
	var ee = Writable.call(this);
	socket.once('close', function() {
		if (!this.finished)
			ee.emit('close');
	})
	this.addTrailers = function(headers) {
		if (request.httpVersion <= '1.0')
			return;
		if (!trailers)
			trailers = {};
		for (var k in headers)
			trailers[k.toLowerCase()] = [k, headers[k]];
	}
	var end = this.end;
	this.end = function() {
		var args = [];
		for (var i = 0; i < arguments.length; i++)
			args.push(arguments[i]);
		trysendheader();
		end.apply(null, args);
	}
	ee.once('finish', function() {
		if (persistent) {
			if (contentLength === null) {
				var trailer = '0\r\n';
				for each(var item in Java.from(header.getArray('Trailer'))) {
					var val = trailers[item.toLowerCase()];
					if (!val)
						val = [item, 'missing'];
					trailer += val[0] + ': ' + val[1] + '\r\n';
				}
				trailer += '\r\n';
				send(new Buffer(trailer, 'ascii'), null, null);
			} else if (contentLength) {
				socket.destroy();
			}
		} else {
			socket.destroy();
		}
		self.finished = true;
	});
	this.finished = false;
	this.getHeader = function(name) {
		if (!this.headersSent)
			return header.get(name);
	}
	this.headersSent = false;
	this.removeHeader = function(name) {
		if (!this.headersSent)
			header.remove(name);
	}
	this.sendDate = true;
	this.setHeader = function(name, value) {
		if (!this.headersSent)
			if (value instanceof Array) {
				for each(var v in value)
					headers.set(name, v);
			} else
				header.set(name, value);
	}
	this.setTimeout = function(msecs, callback) {
		socket.setTimeout(msecs, callback);
	}
	this.statusCode = 200;
	this.statusMessage = 'OK';
	var trysendheader = function() {
		if (self.headersSent)
			return;
		self.writeHead(self.statusCode, self.statusMessage, {
			'Content-Type' : 'text/plain',
		});
	}
	this.__write = this.write;
	this.write = function() {
		trysendheader();
		var data = arguments[0];
		var encoding = 'utf8';
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
		return this.__write(data, encoding, callback);
	}
	this.writeContinue = function() {
		if (this.finished) {
			ee.emit('error', new Error('write to ended stream'));
			return;
		}
		persistent = true;
		this.writeHead(100, { 'Content-Length' : 0 });
	}
	this.writeHead = function() {
		if (this.finished) {
			ee.emit('error', new Error('write to ended stream'));
			return;
		}
		var statusCode = arguments[0];
		var statusMessage = STATUS_CODES[statusCode];
		if (!statusMessage)
			statusMessage = self.statusMessage;
		var headers = {};
		switch(arguments.length) {
		case 2:
			if (typeof(arguments[1]) === 'string')
				statusMessage = arguments[1];
			else if(typeof(arguments[1]) === 'object'  && arguments[1])
				headers = arguments[1];
			break;
		case 3:
			if (typeof(arguments[1]) === 'string')
				statusMessage = arguments[1];
			if(typeof(arguments[2]) === 'object' && arguments[2])
				headers = arguments[2];
		}
		for (var k in headers)
			this.setHeader(k, headers[k]);
		if (this.sendDate)
			header.updateDate();
		if (java.ignoreCaseIncludes(header.get('Connection'), 'close')) {
			persistent = false;
		} else if (persistent) {
			if (java.ignoreCaseIncludes(request.headers['connection'], 'close'))
				persistent = false;
		} else {
			if (java.ignoreCaseIncludes(request.headers['connection'], 'keep-alive'))
				persistent = true;
		}
		if (persistent) {
			contentLength = header.get('Content-Length');
			if (contentLength === null)
				header.set('Transfer-Encoding', 'chunked');
		}
		send(header.format('HTTP/' + request.httpVersion + ' ' + statusCode + ' ' + statusMessage + '\r\n'), null, null);
		this.headersSent = true;
	}
}
util.inherits(ServerResponse, Writable);
function IncomingMessage(socket, message, server) {
	if (!(this instanceof IncomingMessage))
		return new IncomingMessage(socket, message, server);
	var ee = this;
	var self = this;
	socket.once('close', function() { ee.emit('close') });
	this.destroy = function(error) {
		socket.destroy(error);
	}
	this.httpVersion = message.getHttpVersion();
	this.headers = {};
	this.trailers = {};
	this.rawHeaders = Java.from(message.getHeader().getRaw());
	this.rawTrailers = [];
	message.getHeader().get().forEach(function(k, v) { self.headers[k] = v });
	Object.defineProperty(this, 'method', {
		get : function() {
			return message.getRequestLine().getMethod();
		}
	});
	this.setTimeout = function(msecs, callback) {
		socket.setTimeout(msecs, callback);
	}
	Object.defineProperty(this, 'statusCode', {
		get : function() {
			return message.getStatusLine().getStatusCode();
		}
	});
	Object.defineProperty(this, 'statusMessage', {
		get : function() {
			return message.getStatusLine().getStatusMessage();
		}
	});
	this.socket = socket;
	if (server)
		this.url = message.getRequestLine().getTarget().toString();
	PassThrough.call(this);
}
util.inherits(IncomingMessage, PassThrough);
function IncomingFilter(socket, callback, server) {
	var self = this;
	var incomingMessage;
	this.callback = callback;
	var session = java.createSession(socket, server, function(message, r) {
		self.callback(incomingMessage = new IncomingMessage(socket, message, server), r);
	})
	this._transform = function(chunk, encoding, callback) {
		var list = Java.from(session.onData(chunk));
		if (list.length === 1) {
			if (typeof(list[0]) === 'number') {
				var response = new ServerResponse({ socket : socket, httpVersion : '1.0' });
				response.writeHead(list[0], { Connection : 'close' });
				response.end();
				socket.destroy();
			} else {
				socket.destroy(list[0]);
			}
			callback();
		} else {
			if (list[0])
				incomingMessage.write(list[0], callback);
			if (list[1])
				incomingMessage.end(callback);
			if (list[2]) {
				incomingMessage.rawTrailers = Java.from(list[2].getRaw());
				list[2].get().forEach(function(k, v) { incomingMessage.trailers[k] = v });
			}
			if (!list[0] && !list[1])
				callback();
		}
	}
	this._flush = function(callback) {
		if (incomingMessage)
			incomingMessage.end(callback);
	}
	Transform.call(this);
}
util.inherits(IncomingFilter, Transform);
function translateOptions(options) {
	if (typeof(options) === 'string') {
		var p = url.parse(options);
		return {
			protocol : p.protocol,
			host : p.hostname,
			hostname : p.hostname,
			port : p.port,
			socketPath : p.host,
			path : p.path,
			headers : {},
			auth : p.auth,
		}
	}
	return options;
}
exports.Agent = Agent
exports.ClientRequest = ClientRequest
exports.Server = Server
exports.ServerResponse = ServerResponse
exports.IncomingMessage = IncomingMessage
exports.createServer = function() {
	var requestListener = null;
	var tls = null;
	switch (arguments.length) {
	case 1:
		if (typeof(arguments[0]) == 'function')
			requestListener = arguments[0];
		else
			tls = arguments[0];
		break;
	case 2:
		requestListener = arguments[0];
		tls = arguments[1];
	}
	var server = new Server(tls);
	if (requestListener)
		server.on('request', requestListener);
	return server;
}
exports.get = function(options, callback) {
	options = translateOptions(options);
	options.method = 'GET';
	var req = new ClientRequest(options, true);
	if (typeof(callback) === 'function')
		req.once('response', callback);
	return req;
}
exports.globalAgent = globalAgent
exports.request = function(options, callback) {
	var req = new ClientRequest(translateOptions(options));
	if (typeof(callback) === 'function')
		req.once('response', callback);
	return req;
}
exports.METHODS = METHODS 
exports.STATUS_CODES = STATUS_CODES
