var assert = require('assert');
var format = require('util').format;
var toString = function(parameters, noEOL) {
	var args = [];
	for (var i = 0; i < parameters.length; i++)
		args.push(parameters[i]);
	return format.apply(null, args) + (noEOL ? '' : '\n');
}
function Console(_stdout, _stderr) {
	if (!(this instanceof Console))
		return new Console(_stdout, _stderr);
	var stdout = _stdout;
	var stderr = _stderr === undefined ? _stdout : _stderr;
	this.assert = function() {
		var args = [];
		for (var i = 1; i < arguments.length; i++)
			args.push(arguments[i]);
		assert(arguments[0], format.apply(null, args));
	}
	this.dir = function() {
		stdout.write(toString(arguments));
	}
	this.log = function() {
		stdout.write(toString(arguments));
	}
	this.info = function() {
		stdout.write(toString(arguments));
	}
	this.error = function() {
		stderr.write(toString(arguments));
	}
	this.warn = function() {
		stderr.write(toString(arguments));
	}
	this.trace = function() {
		var stack = new Error(toString(arguments, true)).stack.split('\n');
		stack[0] = 'Trace' + stack.shift().substring(5);
		this.warn(stack.join('\n'));
	}
	this.time = function(label) {
		java.time(label);
	}
	this.timeEnd = function(label) {
		var result = java.timeEnd(label);
		if (result == null)
			this.error('label %s not found', label);
		else
			this.log('%s: %fms', label, result);
	} 
}
console = new Console(process.stdout, process.stderr);
console.Console = Console;
