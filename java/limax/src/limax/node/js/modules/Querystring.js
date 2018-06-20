function update(r, key, val) {
	if (r[key]) {
		if (r[key] instanceof Array)
			r[key].push(val);
		else
			r[key] = [r[key], val];
	} else {
		r[key] = val;
	}
}
exports.escape = function(str) {
	return escape(str);
}
exports.parse = function(str, sep, eq, options) {
	sep = sep ? sep : '&';
	eq = eq ? eq : '=';
	var decode = options && options.decodeURIComponent ? options.decodeURIComponent : this.unescape;
	var state = 0;
	var key = '';
	var val = '';
	var r = {};
	for (var i = 0; i < str.length; i++) {
		var c = str.charAt(i);
		switch(state) {
		case 0:
			if (c == eq)
				state = 1;
			else
				key += c;
			break;
		case 1:
			if (c == sep) {
				update(r, decode(key), decode(val));
				state = 0;
				key = '';
				val = '';
			} else {
				val += c;
			}
		}
	}
	update(r, decode(key), decode(val));
	return r;
}
exports.stringify = function(obj, sep, eq, options) {
	sep = sep ? sep : '&';
	eq = eq ? eq : '=';
	var encode = options && options.encodeURIComponent ? options.encodeURIComponent : this.escape;
	var s = '';
	for (var key in obj) {
		var val = obj[key];
		if (val instanceof Array)
			for each(var i in val)
				s += encode(key) + eq + encode(i) + sep;
		else
			s += encode(key) + eq + encode(val) + sep;
	}
	return s.length > 0 ? s.substr(0, s.length - sep.length) : '';
}
exports.unescape = function(str) {
	return decodeURIComponent(str);
}
