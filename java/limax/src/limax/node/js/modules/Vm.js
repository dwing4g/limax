function Script(code) {
	if (!(this instanceof Script))
		return new Script(code);
	var script = java.createScript(code);
	this.runInContext = function(contextifiedSandbox) {
		return script.runInContext(contextifiedSandbox);
	}
	this.runInNewContext = function(sandbox) {
		return script.runInNewContext(sandbox);
	}
	this.runInThisContext = function() {
		return script.runInThisContext();
	}
}

exports.Script = Script;
exports.createContext = function(sandbox) {
	return java.createContext(sandbox ? sandbox : null);
}
exports.isContext = function(sandbox) {
	return java.isContext(sandbox);
}
exports.runInContext = function(code, contextifiedSandbox) {
	return new Script(code).runInContext(contextifiedSandbox);
}
exports.runInDebugContext = function(code) {
	return exports.runInNewContext(code);
}
exports.runInNewContext = function (code, sandbox) {
	return new Script(code).runInNewContext(sandbox ? sandbox : null);
}
exports.runInThisContext = function(code) {
	return new Script(code).runInThisContext();
}
