var querystring = require("querystring");
exports.format = function(urlObject) {
	if (typeof(uriObject) === 'string')
		urlObject = this.parse(urlObject);
	var r = '';
	if (typeof(urlObject.protocol) === 'string')
		r += protocol;
	else if (urlObject.protocol && typeof(urlObject.protocol) !== 'string')
		throw new Error();
	if (r.length > 0 && r.charAt(r.length - 1) != ':')
		r += ':';
	if (urlObject.slashes === true || r.startsWith('http:') || r.startsWith('https:') || r.startsWith('ftp:') || r.startsWith('gopher:') || r.startsWith('file:'))
		r += '//';
	if (urlObject.auth && (urlObject.host || urlObject.hostname))
		r += urlObject.auth + '@';
	if (!urlObject.host) {
		if (typeof(urlObject.hostname) === 'string')
			r += urlObject.hostname;
		else if (!urlObject.hostname && typeof(urlObject.hostname) !== 'string')
			throw new Error();
		if (urlObject.port && !urlObject.hostname)
			r += ':' + urlObject.port;
	} else {
		r += urlObject.host;
	}
	if (typeof(urlObject.pathname) === 'string' && urlObject.pathname.length > 0) {
		if (!urlObject.pathname.startsWith('/'))
			r += '/';
		r += urlObject.pathname;
	} else if (urlObject.pathname && typeof(urlObject.pathname) !== 'string')
		throw new Error();
	if (!urlObject.search && typeof(urlObject.query) === 'object' && urlObject.query)
		r += '?' + querystring.stringify(urlObject.query);
	else if (typeof(urlObject.search) === 'string') {
		if (!urlObject.search.startsWith('?'))
			r += '?';
		r += urlObject.search;
	} else if(urlObject.search && typeof(urlObject.search) !== 'string')
		throw new Error();
	if (typeof(urlObject.hash) === 'string') {
		if (!urlObject.hash.startsWith('#'))
			r += '#';
		r += urlObject.hash;
	} else if (urlObject.hash && typeof(urlObject.hash) !== 'string')
		throw new Error();
	return r;
}
exports.parse = function(urlString, parseQueryString) {
	var r = {};
	java.parse(urlString).forEach(function(k, v) {r[k] = v});
	if (parseQueryString === true && r.query)
		r.query = querystring.parse(r.query);
	return r;
}
exports.resolve = function(from, to) {
	return java.resolve(from, to);
}
