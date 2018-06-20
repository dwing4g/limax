var util = require('util')
function AssertionError(message) {
	this.name = 'AssertionError';
	this.message = message;
	var err = Error.call(this, message);
	var stack = err.stack;
	var p0 = stack.indexOf(':');
	var p1 = stack.indexOf('\n');
	var p2 = stack.indexOf('\n', p1 + 1);
	this.stack = this.name + stack.slice(p0, p1) + stack.slice(p2);
}
util.inherits(AssertionError, Error);
var assert = function(value, message) {
	if (value)
		return;
	if (!message)
		message = value + ' == true';
	throw new AssertionError(message);
}
function deepEqual(a, b, strict) {
	if (strict && a === b || !strict && a == b)
		return true;
	var ta = typeof (a);
	var tb = typeof (b);
	if (ta != tb)
		return false;
	if (ta instanceof Array) {
		if (a.length != b.length)
			return false;
		for (var i = 0; i < a.length; i++)
			if (!deepEqual(a[i], b[i], strict))
				return false;
		return true;
	}
	if (ta == 'object') {
		if (a == null || b == null)
			return a == b;
		if (Object.keys(a).length != Object.keys(b).length)
			return false;
		for ( var i in a)
			if (!deepEqual(a[i], b[i], strict))
				return false;
		return true;
	}
	return false;
}
assert.AssertionError = AssertionError;
assert.deepEqual = function(actual, expected, message) {
	if (deepEqual(actual, expected, false))
		return;
	if (!message)
		message = util.inspect(actual) + ' deepEqual ' + util.inspect(expected);
	throw new AssertionError(message);
}
assert.deepStrictEqual = function(actual, expected, message) {
	if (deepEqual(actual, expected, true))
		return;
	if (!message)
		message = util.inspect(actual) + ' deepStrictEqual '
				+ util.inspect(expected);
	throw new AssertionError(message);
}
function matchError(actual, expected) {
	if (expected instanceof RegExp)
		return expected.test(actual);
	if (typeof (expected) === 'function')
		return actual instanceof expected || expected(actual);
	return false;
}
assert.doesNotThrow = function(block, error, message) {
	try {
		block();
	} catch (e) {
		if (!matchError(e, error))
			throw e;
		var m = 'Got unwanted exception (' + error + ')..';
		if (message)
			m += message;
		throw new AssertionError(m);
	}
}
assert.equal = function(actual, expected, message) {
	if (actual == expected)
		return;
	if (!message)
		message = util.inspect(actual) + ' == ' + util.inspect(expected);
	throw new AssertionError(message);
}
assert.fail = function(actual, expected, message, operator) {
	if (eval(JSON.stringify(actual) + operator + JSON.stringify(expected)))
		return;
	if (!message)
		message = util.inspect(actual) + ' ' + operator + ' '
				+ util.inspect(expected);
	throw new AssertionError(message);
}
assert.ifError = function(value) {
	if (value)
		throw value;
}
assert.notDeepEqual = function(actual, expected, message) {
	if (!deepEqual(actual, expected, false))
		return;
	if (!message)
		message = util.inspect(actual) + ' notDeepEqual '
				+ util.inspect(expected);
	throw new AssertionError(message);
}
assert.notDeepStrictEqual = function(actual, expected, message) {
	if (deepEqual(actual, expected, true))
		return;
	if (!message)
		message = util.inspect(actual) + ' notDeepStrictEqual '
				+ util.inspect(expected);
	throw new AssertionError(message);
}
assert.notEqual = function(actual, expected, message) {
	if (actual != expected)
		return;
	if (!message)
		message = util.inspect(actual) + ' != ' + util.inspect(expected);
	throw new AssertionError(message);
}
assert.notStrictEqual = function(actual, expected, message) {
	if (actual !== expected)
		return;
	if (!message)
		message = util.inspect(actual) + ' !== ' + util.inspect(expected);
	throw new AssertionError(message);
}
assert.ok = function(value, message) {
	assert.equal(!!value, true, message);
}
assert.strictEqual = function(actual, expected, message) {
	if (actual === expected)
		return;
	if (!message)
		message = util.inspect(actual) + ' === ' + util.inspect(expected);
	throw new AssertionError(message);
}
assert.throws = function(block, error, message) {
	try {
		block();
	} catch (e) {
		if (!matchError(e, error)) {
			if (message)
				e += ' ' + message;
			throw new AssertionError(e);
		}
	}
}
module.exports = assert;
