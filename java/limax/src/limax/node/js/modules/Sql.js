var util = require('util');
var EventEmitter = require('events');
function Connection(url) {
	if (!(this instanceof Connection))
		return new Connection(url);
	var ee = EventEmitter.call(this);
	var c = java.createConnection(url);
	var fadeout = c.fadeout;
	var timestamp;
	var timeout = setInterval(function() {
		if (Date.now() - timestamp > fadeout)
			c.maintance();
	}, fadeout);
	this.execute = function() {
		timestamp = Date.now();
		var sql = arguments[0];
		var callback = arguments[arguments.length - 1];
		var args = [];
		for (var i = 1; i < arguments.length - 1; i++)
			args.push(arguments[i]);
		c.execute(sql, Java.to(args), function(obj) {
			var list = Java.from(obj);
			if (list[0])
				callback(list[0]);
			else
				callback.apply(null, list);
		});
	}
	this.destroy = function() {
		if (!timeout)
			return;
		clearInterval(timeout);
		timeout = null;
		c.destroy(function() {
			ee.emit('close');
		});
	}
}
util.inherits(Connection, EventEmitter);

exports.Connection = Connection

exports.createConnection = function(url) {
	return new Connection(url);
}
