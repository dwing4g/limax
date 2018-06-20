var util = require('util');
var EventEmitter = require('events');
var toBuffer = function(val) {
	return val instanceof Buffer ? val : new Buffer(val, 'utf8');
}
var processCallback = function(callback) {
	return function(obj) {
		callback.apply(null, Java.from(obj));
	}
}
function Instance(path) {
	if (!(this instanceof Instance))
		return new Instance(path);
	var ee = EventEmitter.call(this);
	var edb = java.createEdb(path);
	this.destroy = function() {
		edb.destroy(function() {
			ee.emit('close');
		});
	}
	this.addTable = function() {
		var args = [];
		for (var i = 0; i < arguments.length - 1; i++)
			args.push(arguments[i]);
		edb.addTable(Java.to(args), processCallback(arguments[arguments.length - 1]));
	}
	this.removeTable = function() {
		var args = [];
		for (var i = 0; i < arguments.length - 1; i++)
			args.push(arguments[i]);
		edb.removeTable(Java.to(args), processCallback(arguments[arguments.length - 1]));
	}
	this.insert = function(table, key, value, callback) {
		edb.insert(table, toBuffer(key), toBuffer(value), processCallback(callback));
	}
	this.replace = function(table, key, value, callback) {
		edb.replace(table, toBuffer(key), toBuffer(value), processCallback(callback));
	}
	this.remove = function(table, key, callback) {
		edb.remove(table, toBuffer(key), processCallback(callback));
	}
	this.find = function(table, key, callback) {
		edb.find(table, toBuffer(key), processCallback(callback))
	}
	this.exist = function(table, key, callback) {
		edb.exist(table, toBuffer(key), processCallback(callback));
	}
	this.walk = function(table) {
		var callback = arguments[arguments.length - 1];
		var key = arguments.length == 2 ? null : toBuffer(arguments[1]);
		edb.walk(table, key, processCallback(callback));
	}
}
util.inherits(Instance, EventEmitter);
exports.Instance = Instance;
exports.createEdb = function(path) {
	return new Instance(path);
}