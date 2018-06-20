function EventEmitter() {
	if (!(this instanceof EventEmitter))
		return new EventEmitter();
	var ee = java.newEventEmitter(this);
	this.addListener = function(eventName, listener) {
		return ee.addListener(eventName, listener);
	}
	this.emit = function() {
		var args = [];
		for ( var i in arguments)
			args.push(arguments[i]);
		return ee.emit(args.shift(), args);
	}
	this.eventNames = function() {
		return Java.from(ee.eventNames());
	}
	this.getMaxListeners = function() {
		return ee.getMaxListeners();
	}
	this.listenerCount = function(eventName) {
		return ee.listenerCount(eventName);
	}
	this.listeners = function(eventName) {
		return Java.from(ee.listeners(eventName));
	}
	this.on = function(eventName, listener) {
		return ee.on(eventName, listener);
	}
	this.once = function(eventName, listener) {
		return ee.once(eventName, listener);
	}
	this.prependListener = function(eventName, listener) {
		return ee.prependListener(eventName, listener);
	}
	this.prependOnceListener = function(eventName, listener) {
		return ee.prependOnceListener(eventName, listener);
	}
	this.removeAllListeners = function() {
		return ee.removeAllListeners(arguments[0] ? arguments[0] : null);
	}
	this.removeListener = function(eventName, listener) {
		return ee.removeListener(eventName, listener);
	}
	this.setMaxListeners = function(n) {
		return ee.setMaxListeners(n);
	}
	return this;
}
module.exports = EventEmitter;