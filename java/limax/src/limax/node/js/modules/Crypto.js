var processCallback = function(callback) {
	return function(obj) {
		var list = Java.from(obj);
		callback(list[0], list[0] ? null : list[1]);
	}
}
var processPush = function(self, callback) {
	return function(obj) {
		var list = Java.from(obj);
		if (list[0]) {
			self.emit('error', list[0]);
			self.push(null);
		}
		else if (list[1] && list[1].length > 0)
			self.push(list[1]);
		callback();
	}
}
var toBuffer = function(buf, encoding) {
	return buf instanceof Buffer ? buf : new Buffer(buf, Buffer.isEncoding(encoding) ? encoding : 'binary');
}
var cast = function(buf, output_encoding) {
	return Buffer.isEncoding(output_encoding) ? buf.toString(output_encoding) : buf;
}
var Writable = require('stream').Writable;
var Transform = require('stream').Transform;
var util = require('util');
function Certificate() {
	if (!(this instanceof Certificate))
		return new Certificate();
	this.exportChallenge = function(spkac) {
		return java.exportChallenge(toBuffer(spkac));
	}
	this.exportPublicKey = function(spkac) {
		return java.exportPublicKey(toBuffer(spkac));
	}
	this.verifySpkac = function(spkac) {
		return java.verifySpkac(toBuffer(spkac));
	}
}
function Cipher(algorithm, arg0, arg1) {
	if (!(this instanceof Cipher))
		return new Cipher(algorithm, arg0, arg1);
	Transform.call(this);
	var cipher = arg1 ? java.createCipheriv(algorithm, arg0, arg1) : java.createCipher(algorithm, arg0);
	this._transform = function(chunk, encoding, callback) {
		cipher.update(toBuffer(chunk, encoding), processPush(this, callback));
	}
	this._flush = function(callback) {
		cipher._final(processPush(this, callback));
	}
	this.final = function(output_encoding) {
		return cast(cipher._final(), output_encoding);
	} 
	this.update = function(data, input_encoding, output_encoding) {
		return cast(cipher.update(toBuffer(data, input_encoding)), output_encoding);
	}
}
util.inherits(Cipher, Transform);
function Decipher(algorithm, arg0, arg1) {
	if (!(this instanceof Cipher))
		return new Decipher(algorithm, arg0, arg1);
	Transform.call(this);
	var decipher = arg1 ? java.createDecipheriv(algorithm, arg0, arg1) : java.createDecipher(algorithm, arg0);
	this._transform = function(chunk, encoding, callback) {
		decipher.update(toBuffer(chunk, encoding), processPush(this, callback));
	}
	this._flush = function(callback) {
		decipher._final(processPush(this, callback));
	}
	this.final = function(output_encoding) {
		return cast(decipher._final(), output_encoding);
	} 
	this.update = function(data, input_encoding, output_encoding) {
		return cast(decipher.update(toBuffer(data, input_encoding)), output_encoding);
	}
}
util.inherits(Decipher, Transform);
function DiffieHellman(arg0, arg1) {
	if (!(this instanceof DiffieHellman))
		return new DiffieHellman(arg0, arg1);
	var dh = java.createDiffieHellman(arg0, arg1);
	this.computeSecret = function() {
		return cast(dh.computeSecret(toBuffer(arguments[0], arguments[1])), arguments[2]);
	}
	this.generateKeys = function() {
		return cast(dh.generateKeys(), arguments[0]);
	}
	this.getGenerator = function() {
		return cast(dh.getGenerator(), arguments[0]);
	}
	this.getPrime = function() {
		return cast(dh.getPrime(), arguments[0]);
	}
	this.getPrivateKey = function() {
		return cast(dh.getPrivateKey(), arguments[0]);
	}
	this.getPublicKey = function() {
		return cast(dh.getPublicKey(), arguments[0]);
	}
}
function ECDH(curve) {
	if (!(this instanceof ECDH))
		return new ECDH(curve);
	var ecdh = java.createECDH(curve);
	this.computeSecret = function() {
		return cast(ecdh.computeSecret(toBuffer(arguments[0], arguments[1])), arguments[2]);
	}
	this.generateKeys = function() {
		return cast(ecdh.generateKeys(), arguments[0]);
	}
	this.getPrivateKey = function() {
		return cast(ecdh.getPrivateKey(), arguments[0]);
	}
	this.getPublicKey = function() {
		return cast(ecdh.getPublicKey(), arguments[0]);
	}
}
function Hash(algorithm) {
	if (!(this instanceof Hash))
		return new Hash(algorithm);
	var hash = java.createHash(algorithm);
	this._transform = function(chunk, encoding, callback) {
		hash.update(toBuffer(chunk, encoding), processPush(this, callback));
	}
	this._flush = function(callback) {
		hash.digest(processPush(this, callback));
	}
	this.update = function(data, input_encoding) {
		hash.update(toBuffer(data, input_encoding));
		return this;
	}
	this.digest = function(encoding) {
		return cast(hash.digest(), encoding);
	}
	Transform.call(this);
}
util.inherits(Hash, Transform);
function Hmac(algorithm, key) {
	if (!(this instanceof Hmac))
		return new Hmac(algorithm, key);
	var hmac = java.createHmac('hmac' + algorithm, key);
	Transform.call(this);
	this._transform = function(chunk, encoding, callback) {
		hmac.update(toBuffer(chunk, encoding), processPush(this, callback));
	}
	this._flush = function(callback) {
		hmac.digest(processPush(this, callback));
	}
	this.update = function(data, input_encoding) {
		hmac.update(toBuffer(data, input_encoding));
		return this;
	}
	this.digest = function(encoding) {
		return cast(hmac.digest(), encoding);
	}
}
util.inherits(Hmac, Transform);
function SignVerify(algorithm) {
	if (!(this instanceof SignVerify)) 
		return new SignVerify(algorithm);
	var signature = java.createSignVerify(algorithm);
	this._write = function(chunk, encoding, callback) {
		signature.update(toBuffer(chunk, encoding), function(obj) {
			callback(Java.from(obj)[0]);
		});
	}
	this._writev = function(chunks, callback) {
		var err = null;
		for each(var chunk in chunks)
			this._write(toBuffer(chunk.chunk, chunk.encoding), function(obj) {
				if (!err) {
					var e = Java.from(obj)[0];
					if (e)
						err = e;
				}
			});
		callback(err);
	}
	this.update = function(data, input_encoding) {
		signature.update(toBuffer(data, input_encoding));
		return this;
	}
	Writable.call(this);
	this._signature = signature;
}
util.inherits(SignVerify, Writable);
function Sign(algorithm) {
	if (!(this instanceof Sign))
		return new Sign(algorithm);
	SignVerify.call(this, algorithm);
	this.sign = function(private_key, output_format) {
		var key = parseKey(private_key);
		return cast(this._signature.sign(key[0], key[1]), output_format);
	}
}
util.inherits(Sign, SignVerify);
function Verify(algorithm) {
	if (!(this instanceof Verify))
		return new Verify(algorithm);
	SignVerify.call(this, algorithm);
	this.verify = function(public_key, signature, signature_format) {
		var key = parseKey(public_key);
		return this._signature.verify(key[0], key[1], toBuffer(signature, signature_format));
	}
}
util.inherits(Verify, SignVerify);
var parseKey = function(key) {
	return typeof(key) === 'string' ? [key.toString(), null] : [key.key, key.passphrase ? key.passphrase : null];
}
exports.Certificate = Certificate
exports.Cipher = Cipher
exports.Decipher = Decipher
exports.DiffieHellman = DiffieHellman
exports.Hash = Hash
exports.Hmac = Hmac
exports.Sign = Sign
exports.Verify = Verify
exports.createCipher = function(algorithm, password) {
	return new Cipher(algorithm, password);
}
exports.createCipheriv = function(algorithm, key, iv) {
	return new Cipher(algorithm, toBuffer(key), toBuffer(iv));
}
exports.createDecipher = function(algorithm, password) {
	return new Decipher(algorithm, password);
}
exports.createDecipheriv = function(algorithm, key, iv) {
	return new Decipher(algorithm, toBuffer(key), toBuffer(iv));
}
exports.createDiffieHellman = function() {
	if (typeof(arguments[0]) === 'number') {
		return new DiffieHellman(arguments[0], arguments[1] ? toBuffer(arguments[1], arguments[2]) : null);
	}
	switch (arguments.length) {
	case 1:
		return new DiffieHellman(toBuffer(arguments[0]), null);
	case 2:
	case 3:
		return Buffer.isEncoding(arguments[1]) ? 
			new DiffieHellman(toBuffer(arguments[0], arguments[1]), null) : 
			new DiffieHellman(toBuffer(arguments[0]), toBuffer(arguments[1], arguments[2]));
	}
	return new DiffieHellman(toBuffer(arguments[0], arguments[1]), toBuffer(arguments[2], arguments[3]));
}
exports.createECDH = function(curve) {
	return new ECDH(curve);
}
exports.createHash = function(algorithm) {
	return new Hash(algorithm);
}
exports.createHmac = function(algorithm, key) {
	return new Hmac(algorithm, toBuffer(key));
}
exports.createSign = function(algorithm) {
	return new Sign(algorithm);
}
exports.createVerify = function(algorithm) {
	return new Verify(algorithm);
}
exports.getCiphers = function() {
	return Java.from(java.getCiphers()).sort();
}
exports.getCurves = function() {
	return Java.from(java.getCurves()).sort();
}
exports.getHashes = function() {
	return Java.from(java.getHashes()).sort();
}
exports.getDiffieHellman = function() {
	return new DiffieHellman(arguments[0], 0);
}
exports.pbkdf2 = function(password, salt, iterations, keylen, digest, callback) {
	java.pbkdf2(password, toBuffer(salt), iterations, keylen, digest, processCallback(callback))
}
exports.pbkdf2Sync = function(password, salt, iterations, keylen, digest) {
	return java.pbkdf2Sync(password, toBuffer(salt), iterations, keylen, digest);
}
exports.privateDecrypt = function(private_key, buffer) {
	var key = parseKey(private_key);
	return java.privateDecrypt(key[0], key[1], buffer);
}
exports.privateEncrypt = function(private_key, buffer) {
	var key = parseKey(private_key);
	return java.privateEncrypt(key[0], key[1], buffer);
}
exports.publicDecrypt = function(public_key, buffer) {
	var key = parseKey(public_key);
	return java.publicDecrypt(key[0], key[1], buffer);
}
exports.publicEncrypt = function(public_key, buffer) {
	var key = parseKey(public_key);
	return java.publicEncrypt(key[0], key[1], buffer);
}
exports.randomBytes = function() {
	if (arguments.length == 1) {
		return java.randomBytesSync(parseInt(arguments[0]));
	} else {
		java.randomBytes(arguments[0], processCallback(arguments[1]));
	}
}
