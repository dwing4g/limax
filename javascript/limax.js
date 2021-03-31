function Limax(initializer, cache, ontunnel) {
	function Parser() {
		var s, p, d, _ = {};
		_.I = function() {
			var q = s.indexOf(":", p);
			var r = parseInt(s.substring(p, q), 36);
			p = q + 1;
			return r;
		}
		_.J = function() {
			var q = s.indexOf(":", p);
			var r = s.substring(p, q);
			p = q + 1;
			return r;
		}
		_.F = function() {
			var q = s.indexOf(":", p);
			var r = parseFloat(s.substring(p, q));
			p = q + 1;
			return r;
		}
		_.B = function() {
			return s.charAt(p++) == "T";
		}
		_.D = function() {
			return _.U;
		}
		_.S = function() {
			var l = _.I();
			return s.substring(p, p += l);
		}
		_.O = function() {
			var l = _.I();
			var q = p;
			var r = new Array(l);
			for (var i = 0; i < l; i++)
				r[i] = parseInt(s.substring(q, q += 2), 16);
			p = q;
			return r;
		}
		_.P = function() {
			var r = [];
			while (s.charAt(p) != ":")
				r.push(_.R());
			p++;
			return r;
		}
		_.L = function() {
			if (s.charAt(p) != "?")
				return _.P()
			var r = {}
			while (s.charAt(p) != ":") {
				var o = _.R()
				for ( var i in o)
					r[i] = o[i];
			}
			p++;
			return r;
		}
		_.W = function() {
			var r = _.P()
			return function() {
				return r;
			}
		}
		_.X = function() {
			var r = {};
			r.f = d[_.I()];
			r.v = _.R();
			return r;
		}
		_.Y = function() {
			var r = {};
			r.f = d[_.I()];
			r.a = _.R();
			r.r = _.R();
			return r;
		}
		_.Z = function() {
			var r = {};
			r.f = d[_.I()];
			r.c = _.R();
			r.r = _.R();
			return r;
		}
		_.M = function() {
			var r = new Map();
			while (s.charAt(p) != ":")
				r.set(_.R(), _.R());
			p++;
			return r;
		}
		_.U = function() {
		}
		_['?'] = function() {
			var q = s.indexOf("?", p);
			var k = d[parseInt(s.substring(p, q), 36)];
			p = q + 1;
			var r = {};
			r[k] = _.R();
			return r;
		}
		return _.R = function() {
			if (arguments.length == 0)
				return _[s.charAt(p++)]();
			var arg = arguments[0];
			if (typeof arg == 'string') {
				s = arg;
				p = 0;
			} else {
				d = arg;
			}
			return _.R();
		}
	}
	var p = Parser(), c = {}, d = {}, t = {};
	function EQ(a, b) {
		if (a === b)
			return true;
		if ((typeof a == "object" && a != null) && (typeof b == "object" && b != null)) {
			if (Object.keys(a).length != Object.keys(b).length)
				return false;
			for (var p in a)
				if (!b.hasOwnProperty(p) || !EQ(a[p], b[p]))
					return false;
			return true;
		}
		return false;
	}
	function R(r, l, m) {
		function f(r, s, v, o, b) {
			var e = {
				view : r,
				sessionid : s,
				fieldname : v,
				value : o,
				type : b
			};
			if (r.onchange instanceof Function)
				r.onchange(e);
			if (typeof r.__e__ != "undefined")
				if (r.__e__[v] instanceof Function)
					r.__e__[v](e);
				else
					for ( var i in r.__e__[v])
						r.__e__[v][i](e);
		}
		function u(r, s, w, i, v) {
			var b = typeof v;
			var o = w[i];
			if (b == "undefined") {
				b = 2;
			} else if (b == "function") {
				var z = v();
				if (typeof z == "undefined") {
					delete w[i];
					b = 3;
				} else {
					if (typeof o == "undefined") {
						o = w[i] = {};
						b = 0;
					} else
						b = 1;
					for ( var j in z) {
						v = z[j];
						if (typeof v.v != "undefined")
							o[v.f] = v.v;
						else if (typeof v.c == "undefined") {
							if (typeof o[v.f] == "undefined")
								o[v.f] = []
							var n = o[v.f]
							for ( var x in v.r)
								for ( var y in n)
									if (EQ(v.r[x], n[y])) {
										n.splice(y, 1);
										break;
									}
							for ( var x in v.a)
								n.push(v.a[x]);
						} else {
							if (typeof o[v.f] == "undefined")
								o[v.f] = new Map();
							var n = o[v.f];
							var l = [];
							n.forEach(function(y, k) {
								for ( var x in v.r)
									if (EQ(v.r[x], k)) {
										l.push(k);
										break;
									}
							})
							for ( var k in l)
								n["delete"](l[k]);
							v.c.forEach(function(v, k) {
								n.set(k, v);
							})
						}
					}
				}
			} else {
				b = typeof o == "undefined" ? 0 : 1;
				o = w[i] = v;
			}
			f(r, s, i, o, b);
		}
		for ( var i in l)
			for ( var j in l[i])
				u(r, c.i, r, j, l[i][j]);
		for (var i = 0; i < m.length; i += 2)
			if (typeof m[i + 1] != "undefined")
				for ( var j in m[i + 1]) {
					if (typeof r[m[i]] == "undefined")
						r[m[i]] = {};
					u(r, m[i], r[m[i]], j, m[i + 1][j]);
				}
	}
	var h = {
		0 : function(r, s, l, m) {
			R(r, l, m);
		},
		1 : function(r, s, l, m) {
			r[s] = {
				__p__ : r.__p__,
				__c__ : r.__c__,
				__n__ : r.__n__,
				__i__ : s
			};
			if (r.onopen instanceof Function) {
				var k = [];
				for (var i = 0; i < m.length; i += 2)
					k.push(m[i]);
				r.onopen(s, k);
			} else
				onerror(r.__n__ + " onopen not defined");
			R(r[s], l, m);
		},
		2 : function(r, s, l, m) {
			R(r[s], l, m);
		},
		3 : function(r, s, l, m) {
			if (r.onattach instanceof Function)
				r.onattach(s, m[0]);
			else
				onerror(r.__n__ + " onattach not defined");
			R(r[s], l, m);
		},
		4 : function(r, s, l, m) {
			if (r.ondetach instanceof Function)
				r.ondetach(s, m[0], m[1]);
			else
				onerror(r.__n__ + " ondetach not defined");
			delete r[s][m[0]];
		},
		5 : function(r, s, l, m) {
			if (r.onclose instanceof Function)
				r.onclose(s);
			else
				onerror(r.__n__ + " onclose not defined");
			delete r[s];
		}
	};
	if (!cache)
		cache = {
			put : function(key, value) {
			},
			get : function(key) {
			},
			keys : function() {
			}
		};
	function init(s) {
		var r = [ p(s), p() ];
		if (r[1] != 0)
			return r[1];
		c.i = p();
		c.f = p();
		var l = p();
		var lmkdata = p();
		for (var i = 0; i < l.length; i += 3) {
			var ck = (d[l[i]] = l[i + 1].split(",")).pop();
			var cv = cache.get(ck);
			if (cv) {
				d[l[i]] = p(cv).split(",");
				l[i + 2] = p();
			} else {
				cv = d[l[i]].join(",");
				cv = "S" + cv.length.toString(36) + ":" + cv + "M";
				l[i + 2].forEach(function(v, k) {
					cv += 'I' + k.toString(36) + ':L';
					for ( var j in v)
						cv += 'I' + v[j].toString(36) + ':';
					cv += ":"
				});
				cache.put(ck, cv + ":");
			}
			t[l[i]] = {};
			var s = c[l[i]] = {};
			l[i + 2].forEach(function(v, k) {
				var r = s;
				var m = "";
				for ( var j in v) {
					var n = d[l[i]][v[j]];
					m = m + n + ".";
					if (!(n in r))
						r[n] = {};
					r = r[n];
				}
				t[l[i]][k] = r;
				r.__p__ = l[i];
				r.__c__ = k;
				r.__n__ = m.substring(0, m.length - 1);
			});
		}
		if (ontunnel)
			ontunnel(1, 0, lmkdata);
	}
	function update(s) {
		var v = p(s);
		if (typeof v == 'string') {
			if (ontunnel)
				ontunnel(p(), p(), v);
		} else {
			var r = t[v][p(d[v])];
			var i = p();
			h[p()](r, i, p(), p());
		}
	}
	var z = false, g = init;
	function onerror(e) {
		try {
			c.onerror(e);
		} catch (e) {
		}
	}
	function onclose(p) {
		z = true;
		try {
			c.onclose(p);
		} catch (e) {
		}
		return 3;
	}
	var login;
	var tunnel;
	var logindatas = {};
	function onmessage(s) {
		if (g == init) {
			if (s.charAt(0) == '$') {
				login(parseInt(s.substr(1), 36));
				return 0;
			}
			var r = g(s);
			if (r)
				return onclose(r);
			if (c.onopen instanceof Function)
				c.onopen();
			else
				onerror("context onopen not defined");
			g = update;
			return 2;
		}
		g(s);
		return 0;
	}
	initializer(c);
	return function(t, p) {
		if (z)
			return 3;
		var argslen = arguments.length;
		if (argslen == 0)
			return cache.keys();
		if (argslen == 1) {
			logindatas = t;
			return;
		}
		if (argslen == 3) {
			if (tunnel)
				tunnel(arguments[0], arguments[1], arguments[2]);
			return;
		}
		if (t == 0) {
			login = function(v) {
				if (!z) {
					var s = v.toString(36) + ",";
					var logindata = logindatas[v];
					if (logindata) {
						if (typeof logindata.label == 'number')
							s = s + "1," + logindata.label.toString(36) + ","
									+ logindata.data;
						else if (!logindata.base64)
							s = s + "2," + logindata.data;
						else
							s = s + "3," + logindata.data;
					} else {
						s = s + "0";
					}
					var e = p(s);
					if (typeof e != "undefined")
						onclose(e);
				}
			}
			tunnel = function(v, l, s) {
				if (!z) {
					var e = p(v.toString(36) + "," + l.toString(36) + "," + s);
					if (typeof e != "undefined")
						onclose(e);
				}
			}
			c.send = function(r, s) {
				if (!z) {
					var e = p(r.__p__.toString(36) + "," + r.__c__.toString(36)
							+ "," + (!r.__i__ ? "0" : r.__i__.toString(36))
							+ ":" + s);
					if (typeof e != "undefined")
						onclose(e);
				}
			}
			c.register = function(r, v, f) {
				if (typeof r.__e__ == "undefined")
					r.__e__ = {}
				if (typeof r.__e__[v] == "undefined")
					r.__e__[v] = f;
				else if (r.__e__[v] instanceof Function)
					r.__e__[v] = [ r.__e__[v], f ];
				else
					r.__e__[v].push(f);
			}
			return 0;
		} else if (t == 1) {
			try {
				return onmessage(p)
			} catch (e) {
				onerror(e);
			}
		}
		return onclose(p);
	}
}

function WebSocketConnector(action, login) {
	var keys = action();
	if (login.logindatas)
		action(login.logindatas);
	keys = keys ? ";" + keys.join() : "";
	var uri = encodeURI(login.scheme + "://" + login.host + "/?username="
			+ login.username + "&token=" + login.token + "&platflag="
			+ login.platflag + keys + "&pvids=" + login.pvids.join());
	var s = new WebSocket(uri);
	var process = function(t, p) {
		switch (action(t, p)) {
		case 2:
			var w = setInterval(function() {
				s.send(" ");
				console.log("keepalive");
			}, 50000);
			s.onerror = s.onclose = function(e) {
				clearInterval(w);
				process(2, e);
			}
			break;
		case 3:
			s.close();
		}
	}
	s.onopen = function() {
		process(0, function(t) {
			try {
				s.send(t);
			} catch (e) {
				return e;
			}
		});
	}
	s.onmessage = function(e) {
		process(1, e.data);
	}
	s.onerror = s.onclose = function(e) {
		process(2, e);
	}
	return {
		close : function() {
			s.onclose();
		}
	}
}

function loadJSON(uri, onjson, timeout, cacheDir, staleEnable) {
	var w, c;
	var r = new XMLHttpRequest();
	r.open("GET", uri, true);
	if (typeof cacheDir != "undefined") {
		c = cacheDir[uri];
		if (typeof c != "undefined") {
			r.setRequestHeader("If-None-Match", c.etag);
			c = c.json;
		}
	}
	r.onreadystatechange = function() {
		if (r.readyState == r.DONE) {
			if (typeof w != "undefined")
				clearTimeout(w);
			switch (r.status) {
			case 200:
				try {
					var json = JSON.parse(r.responseText);
					if (typeof cacheDir != "undefined")
						cacheDir[uri] = {
							etag : r.getResponseHeader("ETag"),
							json : json
						};
					onjson(json);
				} catch (e) {
					r.onerror(e);
				}
				return;
			case 304:
				onjson(c);
				return;
			}
			r.onerror("status = " + r.status);
		}
	}
	r.onerror = r.onabort = r.ontimeout = function(e) {
		staleEnable ? onjson(c) : onjson(undefined, e);
	}
	r.send();
	if (timeout > 0) {
		w = setTimeout(function() {
			r.abort();
		}, timeout);
	}
	return {
		close : function() {
			r.abort();
		}
	};
}

function loadServiceInfos(host, port, appid, additionalQuery, timeout,
		cacheDir, staleEnable, onresult, wss) {
	var url = "http://" + host + ":" + port + "/app?" + (wss ? "wss=" : "ws=")
			+ appid;
	if (additionalQuery.length > 0) {
		if (!additionalQuery.startsWith("&"))
			url += "&";
		url += additionalQuery;
	}
	var l = loadJSON(encodeURI(url), function(json, err) {
		if (typeof json == "undefined")
			onresult(json, err);
		else
			try {
				for ( var i in json.services) {
					var s = json.services[i], t = [];
					for ( var j in s.userjsons)
						t[j] = JSON.parse(s.userjsons[j]);
					s.userjsons = t;
					s.pvids.push(1);
					s.host = function() {
						var a = s.switchers[Math.floor(Math.random()
								* s.switchers.length)];
						return {
							host : a.host,
							port : a.port,
						}
					};
					s.login = function(username, token, platflag) {
						var h = s.host();
						return {
							scheme : wss ? "wss" : "ws",
							host : h.host + ":" + h.port,
							username : username,
							token : token,
							platflag : platflag,
							pvids : s.pvids,
							appid : appid
						};
					};
				}
				onresult(json.services);
			} catch (e) {
				onresult(undefined, e);
			}
	}, timeout, cacheDir, staleEnable);
}
