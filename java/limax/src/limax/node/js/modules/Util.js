var Exception = Java.type('java.lang.Exception')
var inspect = function(obj) {
	var l = java.createIdentityHashSet();
	var impl = function(obj, t) {
		if (obj === undefined)
			return 'undefined';
		if (obj === null)
			return 'null';
		if (obj instanceof Buffer)
			return obj.toStringRaw();
		if (obj instanceof Array) {
			if (!l.add(obj))
				return '<loop!>';
			var s = '[';
			for each(var v in obj)
				s += impl(v, t + '\t') + ",";
			l.remove(obj);
			return (s.length === 1 ? s : s.substr(0, s.length - 1)) + ']';
		}
		if (obj instanceof Number || obj instanceof Boolean || obj instanceof Date)
			return obj.toString();
		if (obj instanceof Function) {
			var hasProperty = false;
			for (var key in obj) {
				hasProperty = true;
				break;
			}
			if (!hasProperty)
				return obj.name ? '[Function: ' + obj.name + ']' : '[Function]'
		}
		if (obj instanceof Exception) {
			return obj.toString();
		}
		if (obj instanceof Object || typeof(obj) === 'object') {
			if (!l.add(obj))
				return '<loop!>';
			var s = t + '{';
			if (obj instanceof Function)
				s += obj.name ? '[Function: ' + obj.name + ']' : '[Function]'
					s += '\n';
			for (var key in obj)
				s += t + "\t'" + key + "':" + impl(obj[key], t + '\t') + ',\n';
			l.remove(obj);
			return s + t + '}';
		}
		if (typeof(obj) === 'string')
			return t ? "'" + obj.replace(/('|\\)/g, '\\$1') + "'" : obj;
		return typeof(obj.toString) === 'function' ? obj.toString() : obj;
	}
	return impl(obj, "");
}
var format = function() {
	if (arguments.length == 0)
		return '';
	if (typeof(arguments[0]) == 'string') {
		var args = [];
		for (var i = 1; i < arguments.length; i++)
			args.push(arguments[i]);
		if (arguments[0].indexOf('%') != -1) {
			var r = java.format(arguments[0], args);
			if (r)
				return r;
		}
	}
	var args = [];
	for (var i = 0; i < arguments.length; i++)
		args.push(inspect(arguments[i]));
	return args.join(' ');
}
exports.format = format;
exports.inherits = function(ctor, superCtor) {
		ctor.super_ = superCtor;
		ctor.prototype = Object.create(superCtor.prototype, {
		constructor: {
			value: ctor,
			enumerable: false,
			writable: true,
			configurable: true
		}
		});
};
exports.inspect = inspect

