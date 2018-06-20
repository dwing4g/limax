function Map() {
	this.e = [];
	this.size = 0;
}
Map.prototype = {
	clear : function() {
		this.e = [];
		this.size = 0;
	},
	get : function(k) {
		for (var i = 0; i < this.e.length; i += 2)
			if (this.e[i] === k)
				return this.e[i + 1];
	},
	set : function(k, v) {
		for (var i = 0; i < this.e.length; i += 2)
			if (this.e[i] === k) {
				this.e[i + 1] = v;
				return this;
			}
		this.e.push(k, v);
		this.size++;
		return this;
	},
	"delete" : function(k) {
		for (var i = 0; i < this.e.length; i += 2)
			if (this.e[i] === k) {
				this.e.splice(i, 2);
				this.size--;
				return true;
			}
		return false;
	},
	has : function(k) {
		for (var i = 0; i < this.e.length; i += 2)
			if (this.e[i] === k)
				return true;
		return false;
	},
	forEach : function(c) {
		for (var i = 0; i < this.e.length; i += 2)
			c(this.e[i + 1], this.e[i], this);
	},
	keys : function() {
		return new MapIterator(this.e, 0, 1);
	},
	values : function() {
		return new MapIterator(this.e, 1, 1);
	},
	entries : function() {
		return new MapIterator(this.e, 0, 2);
	}
}

function MapIterator(e, p, s) {
	this.e = e;
	this.p = p;
	this.s = s;
}
MapIterator.prototype = {
	next : function() {
		if (this.p >= this.e.length)
			return {
				value : undefined,
				done : true
			};
		var q = this.p;
		this.p += 2;
		return {
			value : this.s == 1 ? this.e[q] : [ this.e[q], this.e[q + 1] ],
			done : false
		};
	}
}