function(impl) {
	var extensions = {
		'.js' : function(path, parameters) {
			return impl.loadJS(path, parameters);
		},
		'.json' : function(path) {
			return impl.loadJSON(path);
		},
		'.jar' : function(path, parameters) {
			return impl.loadJAR(path, parameters);
		}
	}
	function resolve(name) {
		return impl.resolve(name, extensions);
	}
	function require(name) {
		var args = [];
		for (var i = 1; i < arguments.length; i++)
			args.push(arguments[i]);
		return impl.require(name, extensions, args);
	}
	function reload(name) {
		var filename = resolve(name);
		if (filename)
			delete require.cache[filename];
		var args = [];
		for (var i = 1; i < arguments.length; i++)
			args.push(arguments[i]);
		return impl.require(name, extensions, args);
	}
	function launch(name) {
		var args = [];
		for (var i = 1; i < arguments.length; i++)
			args.push(arguments[i]);
		impl.launch(name, extensions, Java.to(args));
	}
	require.resolve = resolve
	require.reload = reload
	require.launch = launch
	require.extensions = extensions
	require.cache = {};
	Object.defineProperty(require, "__create_js_component", {
		value : function(type) {
			return type ? {} : [];
		}
	})
	Object.defineProperty(require, "__set_property", {
		value : function(obj, name, value) {
			Object.defineProperty(obj, name, {
				value : value
			});
		}
	})
	var JSONStringify = JSON.stringify
	var JSONStringifiable = Java.type('limax.node.js.JSONStringifiable')
	JSON.stringify = function() { 
		return arguments[0] instanceof JSONStringifiable ? arguments[0].toJSON() : JSONStringify(arguments[0], arguments[1], arguments[2]);
	}
	return require;
}