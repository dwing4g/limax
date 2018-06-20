function parse(args) {
	var v = [];
	for ( var i in args)
		v.push(args[i]);
	var callback = v.shift();
	if (typeof (callback) !== "function")
		throw new TypeError(callback + " is not function");
	return [ callback, v ];
}
setImmediate = function() {
	var args = parse(arguments);
	return java.setImmediate(function() {
		args[0].apply(null, args[1]);
	})
}
setTimeout = function() {
	var args = parse(arguments);
	return java.setTimeout(function() {
		args[0].apply(null, args[1]);
	}, args[1].shift());
}
setInterval = function() {
	var args = parse(arguments);
	return java.setInterval(function() {
		args[0].apply(null, args[1]);
	}, args[1].shift());
}
clearImmediate = clearTimeout = clearInterval = function(obj) {
	java.clear(obj);
}
