var AsynchronousCloseException = Java
		.type('java.nio.channels.AsynchronousCloseException');
var EventEmitter = require('events');
var util = require('util');
function Socket(options) {
	if (!(this instanceof Socket))
		return new Socket(options);
	var udp6 = typeof (options.type) == 'string'
			&& options.type.toLowerCase() === 'udp6';
	var reuseAddr = options.type === true;
	var self = this;
	var ee = EventEmitter.call(this);
	var closed = false;
	var socket = java.createSocket(udp6, reuseAddr, function(obj) {
		var list = Java.from(obj);
		if (list[0]) {
			if (!(list[0] instanceof AsynchronousCloseException))
				ee.emit('error', list[0]);
		} else
			ee.emit('message', list[1], {
				port : list[2],
				family : list[3],
				address : list[4]
			});
	});
	this.addMembership = function() {
		var multicastAddress = arguments[0];
		var multicastInterface = arguments[1] ? arguments[1] : null;
		var err = socket.addMembership(multicastAddress, multicastInterface);
		if (err)
			ee.emit('error', err);
	}
	this.address = function() {
		var list = Java.from(socket.address());
		return {
			port : list[0],
			family : list[1],
			address : list[2]
		}
	}
	this.bind = function() {
		var port = 0;
		var address = null;
		var callback = typeof (arguments[arguments.length - 1]) === 'function' ? arguments[arguments.length - 1]
				: null;
		switch (typeof (arguments[0])) {
		case 'number':
			port = arguments[0];
			if (typeof (arguments[1]) === 'string')
				address = arguments[1];
			break;
		case 'string':
			address = arguments[0];
			break;
		case 'object':
			if (arguments[0]) {
				if (typeof (arguments[0].port) === 'number')
					port = arguments[0].port;
				if (typeof (arguments[0].address) === 'address')
					address = arguments[0].address;
			}
		}
		var err = socket.bind(port, address);
		if (err)
			ee.emit('error', err);
		else
			ee.emit('listening');
		if (callback)
			callback();
	}
	this.close = function() {
		if (closed)
			return;
		closed = true;
		var callback = typeof (arguments[0]) === 'function' ? arguments[0]
				: null;
		if (callback)
			ee.once('close', callback);
		socket.close();
		setImmediate(ee.emit, 'close');
	}
	this.dropMembership = function() {
		var multicastAddress = arguments[0];
		var multicastInterface = arguments[1] ? arguments[1] : null;
		var err = socket.dropMembership(multicastAddress, multicastInterface);
		if (err)
			ee.emit('error', ee);
	}
	this.send = function() {
		var msg = arguments[0];
		var offset;
		var length;
		var port;
		var address;
		var callback = typeof (arguments[arguments.length - 1]) === 'function' ? arguments[arguments.length - 1]
				: null;
		if (typeof (msg) === 'string')
			msg = new Buffer(msg, 'utf8');
		else if (msg instanceof Array) {
			msg = Buffer.concat(msg);
		}
		switch (arguments.length) {
		case 3:
		case 4:
			offset = 0;
			length = msg.length;
			port = arguments[1];
			address = arguments[2];
			break;
		default:
			offset = arguments[1];
			length = arguments[2];
			port = arguments[3];
			address = arguments[4];
		}
		socket.send(msg, offset, length, port, address, function(obj) {
			var err = Java.from(obj)[0];
			if (callback)
				callback(err);
			else
				ee.emit('error', err);
		});
	}
	this.setBroadcast = function() {
		var flag = arguments[0] === true;
		socket.setBroadcast(flag);
		return this;
	}
	this.setMulticastLoopback = function() {
		var flag = arguments[0] === true;
		socket.setBroadcast(flag);
		return this;
	}
	this.setMulticastTTL = function() {
		var ttl = typeof (arguments[0]) === 'number' ? arguments[0] : 1;
		socket.setMulticastTTL(ttl);
		return this;
	}
	this.setTTL = function() {
		var ttl = typeof (arguments[0]) === 'number' ? arguments[0] : 1;
		socket.setTTL(ttl);
		return this;
	}
	this.ref = function() {
		socket.ref();
		return this;
	}
	this.unref = function() {
		socket.unref();
		return this;
	}
}
util.inherits(Socket, EventEmitter);
var createSocket = function(options, callback) {
	if (typeof(options) !== 'object' || !options)
		options = {
			type : options
		};
	var socket = new Socket(options);
	if (typeof(callback) === 'function')
		socket.on('message', callback);
	return socket;
}
exports.Socket = Socket
exports.createSocket = createSocket
