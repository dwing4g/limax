var IPAddressUtil = Java.type('sun.net.util.IPAddressUtil');
function resolve(hostname, rrtype, callback) {
	var cb;
	rrtype = rrtype.toUpperCase();
	switch (rrtype) {
	case 'A':
	case 'AAAA':
	case 'CNAME':
	case 'NS':
	case 'PTR':
	case 'TXT':
		cb = function(obj) {
			var list = Java.from(obj);
			var err = list.shift();
			if (err) {
				callback(err);
				return;
			}
			callback(err, list);
		}
		break;
	case 'MX':
		cb = function(obj) {
			var list = Java.from(obj);
			var err = list.shift();
			if (err) {
				callback(err);
				return;
			}
			for ( var i in list) {
				var e = list[i].split(' ');
				list[i] = {
					priority : e[0],
					exchange : e[1]
				};
			}
			callback(err, list);
		}
		break;
	case 'NAPTR':
		cb = function(obj) {
			var list = Java.from(obj);
			var err = list.shift();
			if (err) {
				callback(err);
				return;
			}
			for ( var i in list) {
				var e = list[i].split(' ');
				list[i] = {
					order : e[0],
					preference : e[1],
					flags : e[2],
					service : e[3],
					regexp : e[4],
					replacement : e[5]
				};
			}
			callback(err, list);
		}
		break;
	case 'SOA':
		cb = function(obj) {
			var list = Java.from(obj);
			var err = list.shift();
			if (err) {
				callback(err);
				return;
			}
			if (list.length == 1) {
				var e = list[0].split(' ');
				callback(err, {
					nsname : e[0],
					hostmaster : e[1],
					serial : e[2],
					refresh : e[3],
					retry : e[4],
					expire : e[5],
					minttl : e[6]
				});
			} else
				callback(err, {});
		}
		break;
	case 'SRV':
		cb = function(obj) {
			var list = Java.from(obj);
			var err = list.shift();
			if (err) {
				callback(err);
				return;
			}
			for ( var i in list) {
				var e = list[i].split(' ');
				list[i] = {
					priority : e[0],
					weight : e[1],
					port : e[2],
					name : e[3]
				};
			}
			callback(err, list);
		}
		break;
	default:
		throw new TypeError("Unsupported rrtype " + rrtype);
	}
	java.resolve(hostname, rrtype, cb);
}
exports.getServers = function() {
	var list = java.getServers().trim().split(' ');
	for ( var i in list)
		list[i] = list[i].substring(6);
	return list;
}
exports.setServers = function(list) {
	java.setServers(list);
}
exports.lookup = function() {
	var hostname = arguments[0];
	var options = arguments.length == 2 ? null : arguments[1];
	var callback = arguments[arguments.length - 1];
	var family;
	var hints;
	var all;
	if (options == null) {
		hints = java.ADDRCONFIG;
		all = false;
	} else {
		family = options.family;
		hints = options.hints;
		all = (options.all === true);
	}
	java.lookup(hostname, family, hints, all, function(obj) {
		var list = Java.from(obj);
		var err = list.shift();
		if (err) {
			callback(err);
			return;
		}
		for (var i = 0; i < list.length; i++) {
			list[i] = {
				'family' : list[i].getKey(),
				'address' : list[i].getValue()
			}
		}
		callback(err, list);
	});
}
exports.resolve = function() {
	resolve(arguments[0], arguments.length == 2 ? 'A' : arguments[1],
			arguments[arguments.length - 1]);
}
exports.resolve4 = function(hostname, callback) {
	resolve(hostname, 'A', callback);
}
exports.resolve6 = function(hostname, callback) {
	resolve(hostname, 'AAAA', callback);
}
exports.resolveCname = function(hostname, callback) {
	resolve(hostname, 'CNAME', callback);
}
exports.resolveMx = function(hostname, callback) {
	resolve(hostname, 'MX', callback);
}
exports.resolveNaptr = function(hostname, callback) {
	resolve(hostname, 'NAPTR', callback);
}
exports.resolveNs = function(hostname, callback) {
	resolve(hostname, 'NS', callback);
}
exports.resolveSoa = function(hostname, callback) {
	resolve(hostname, 'SOA', callback);
}
exports.resolveSrv = function(hostname, callback) {
	resolve(hostname, 'SRV', callback);
}
exports.resolvePtr = function(hostname, callback) {
	resolve(hostname, 'PTR', callback);
}
exports.resolveTxt = function(hostname, callback) {
	resolve(hostname, 'TXT', callback);
}
exports.reverse = function(ip, callback) {
	if (typeof(ip) === 'string') {
		var data = IPAddressUtil.textToNumericFormatV4(ip);
		if (!data)
			data = IPAddressUtil.textToNumericFormatV6(ip);
		if (data) {
			data = Java.from(data).reverse();
			var hostname = '';
			if (data.length == 4) {
				for each(var i in data)
					hostname += i + '.';
				hostname += 'in-addr.arpa.';
			} else {
				for each(var i in data)
					hostname += (i & 15).toString(16) + '.' + ((i >> 4) & 15).toString(16) + '.';
				hostname += 'ip6.arpa.';
			}
			resolve(hostname, 'PTR', callback);
			return;
		}
	}
	callback(new Error("Invalid ip format " + ip));
}
Object.defineProperty(exports, "ADDRCONFIG", {
	get : function() {
		return java.ADDRCONFIG;
	},
	enumerable : true
});
Object.defineProperty(exports, "V4MAPPED", {
	get : function() {
		return java.V4MAPPED;
	},
	enumerable : true
});
