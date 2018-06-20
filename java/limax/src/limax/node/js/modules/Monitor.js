var util = require('util');
var EventEmitter = require('events');
function Host(impl) {
	if (!(this instanceof Host))
		return new Host(impl);
	var ee = EventEmitter.call(this);
	this.destroy = function() {
		impl.destroy(function() {
			ee.emit('close');
		});
	}
	this.query = function(objectName, callback) {
		impl.query(objectName, function(obj) {
			var list = Java.from(obj);
			if (list[0]) {
				callback(list[0]);
				return;
			}
			var r = {};
			list[1].forEach(function(k, v) { r[k] = v });
			callback(list[0], r);
		})
	}
	this.ref = function() {
		impl.ref();
		return this;
	}
	this.unref = function() {
		impl.unref();
		return this;
	}
}
util.inherits(Host, EventEmitter)
exports.Host = Host
exports.connect = function(host, serverPort, rmiPort, username, password) {
	if (!username)
		username = null;
	if (!password)
		password = null;
	return new Host(java.connect(host, serverPort, rmiPort, username, password));
}