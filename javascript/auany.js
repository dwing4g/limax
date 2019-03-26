/*
 *
 * step 1
 * var auany; -- define globally
 * 
 * step 2A (make sure login.pvids pvid = 1 added)
 * auany = AuanyService(ctx); -- place in ctx.open function
 * 
 * active function pay, temporary, transfer
 * 
 * step 2B (anywhere)
 * auany = AuanyService();
 * 
 * active function temporary, transfer
 *
 * temporary credential usage -> USAGE_LOGIN = 1, USAGE_TRANSFER = 2
 * 
 */

function AuanyService(ctx) {
	function auany_onopen(ctx) {
		var g = 0, q = {}, v1 = ctx[1], r = function(sn) {
			var c = q[sn];
			if (c) {
				delete q[sn];
				clearTimeout(c[1]);
				return c[0];
			}
			return function() {
			};
		}, c = function(s, handler) {
			var sn = g++;
			q[sn] = [ handler, setTimeout(function() {
				r(sn)(2, 2002);
			}, timeout) ];
			ctx.send(v1.auanyviews.Service, s);
		}
		v1.auanyviews.ServiceResult.onopen = function(instanceid, memberids) {
			ctx.register(this[instanceid], "result", function(e) {
				var v = e.value;
				r(v.sn)(v.errorSource, v.errorCode, v.result);
			});
		}
		return {
			pay : function(gateway, payid, product, price, quantity, receipt,
					timeout, handler) {
				c(JSON.stringify({
					cmd : "pay",
					sn : g,
					gateway : gatway,
					payid : payid,
					product : product,
					price : price,
					quantity : quantity,
					receipt : recepit
				}));
			}
		}
	}
	function CredentialContext(host, port, appid, timeout, c, onresult, wss) {
		var w;
		var r = [ 0, 2002 ];
		var l = loadJSON(encodeURI("http://" + host + ":" + port + "/invite?"
				+ (wss ? "wss=" : "ws=") + appid), function(json) {
			if (typeof json != "undefined") {
				l = WebSocketConnector(Limax(function(ctx) {
					ctx.onerror = ctx.onclose = function(e) {
						clearTimeout(w);
						onresult(r[0], r[1], r[2]);
					}
					ctx.onopen = function() {
						ctx[1].auanyviews.ServiceResult.onopen = function(
								instanceid, memberids) {
							ctx.register(this[instanceid], "result",
									function(e) {
										var v = e.value;
										r = [ v.errorSource, v.errorCode,
												v.result ];
										l.close();
									});
						}
						ctx.send(ctx[1].auanyviews.Service, JSON.stringify(c));
					}
				}), {
					scheme : "ws",
					host : json.switcher.host + ":" + json.switcher.port,
					username : json.code,
					token : '',
					platflag : 'invite',
					pvids : [ 1 ],
				});
			} else {
				clearTimeout(w);
				onresult(r[0], r[1]);
			}
		});
		w = setTimeout(function() {
			l.close();
		}, timeout);
	}
	var r = {};
	if (typeof ctx != "undefined" && typeof ctx[1] != "undefined")
		r = auany_open(ctx);
	r["temporary"] = function() {
		function temporary(host, port, appid, username, token, platflag,
				authcode, milliseconds, usage, subid, timeout, onresult, wss) {
			CredentialContext(host, port, appid, timeout, {
				cmd : "temporaryFromLogin",
				sn : 0,
				username : username,
				token : token,
				platflag : platflag,
				appid : appid,
				authcode : authcode,
				milliseconds : milliseconds,
				usage : usage,
				subid : subid
			}, onresult, wss);
		}
		var a = arguments;
		switch (a.length) {
		case 13:
			temporary(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8],
					a[9], a[10], a[11], a[12]);
			break;
		case 10:
			var h = a[0].host();
			temporary(h.host, h.port, h.appid, a[1], a[2], a[3], a[4], a[5],
					a[6], a[7], a[8], a[9], h.scheme == 'wss');
			break;
		default:
			throw "wrong parameter count";
		}
	}
	r["transfer"] = function() {
		function transfer(host, port, appid, username, token, authcode,
				platflag, temp, authtemp, timeout, onresult, wss) {
			CredentialContext(host, port, appid, timeout, {
				cmd : "transfer",
				sn : 0,
				username : username,
				token : token,
				authcode : authcode,
				platflag : platflag,
				temp : temp,
				authtemp : authtemp
			}, onresult, wss);
		}
		var a = arguments;
		switch (a.length) {
		case 12:
			transfer(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8],
					a[9], a[10], a[11]);
			break;
		case 9:
			var h = a[0].host();
			transfer(h.host, h.port, h.appid, a[1], a[2], a[3], a[4], a[5],
					a[6], a[7], a[8], h.scheme == 'wss');
			break;
		default:
			throw "wrong parameter count";
		}
	}
	return r;
}
