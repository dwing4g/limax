package limax.net;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import javax.net.ssl.SSLSession;

import limax.codec.Codec;
import limax.codec.CodecException;
import limax.codec.Decrypt;
import limax.codec.Encrypt;
import limax.codec.HmacMD5;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.codec.RFC2118Decode;
import limax.codec.RFC2118Encode;
import limax.codec.SHA1;
import limax.codec.SinkOctets;
import limax.net.io.NetModel;
import limax.net.io.NetProcessor;
import limax.net.io.NetTask;
import limax.net.io.ServerContext;
import limax.util.Helper;
import limax.util.Trace;

class RFC6455Exception extends Exception {
	private final static long serialVersionUID = 4618764472702188950L;

	private final byte[] code;

	public RFC6455Exception(short code, String message) {
		super(message);
		byte[] tmp = message.getBytes(StandardCharsets.UTF_8);
		byte[] msg = new byte[tmp.length + 2];
		msg[0] = (byte) ((code >> 8) & 0xFF);
		msg[1] = (byte) (code & 0xFF);
		System.arraycopy(tmp, 0, msg, 2, tmp.length);
		this.code = msg;
	}

	public byte[] getCode() {
		return code;
	}
}

class RFC6455Server {
	private final static int maxMessageSize = Integer.getInteger("limax.net.WebSocketServer.maxMessageSize", 65536);

	public final static byte opcodeCont = 0;
	public final static byte opcodeText = 1;
	public final static byte opcodeBinary = 2;
	public final static byte opcodeClose = 8;
	public final static byte opcodePing = 9;
	public final static byte opcodePong = 10;

	public final static short closeNormal = 1000;
	public final static short closeNotSupportFrame = 1003;
	public final static short closeVolatilePolicy = 1006;
	public final static short closeSizeExceed = 1009;

	private final OctetsStream os = new OctetsStream();
	private final Octets data = new Octets();
	private byte opcode;

	public byte[] unwrap(byte[] in) throws RFC6455Exception {
		byte[] res = null;
		os.insert(os.size(), in);
		os.begin();
		try {
			opcode = os.unmarshal_byte();
			boolean fin = ((opcode & 0x80) != 0);
			opcode = (byte) (opcode & 15);
			byte t = os.unmarshal_byte();
			if ((t & 0x80) == 0)
				throw new RFC6455Exception(closeVolatilePolicy, "Volatile 5.3.");
			long length = t & 127;
			if (length == 126)
				length = os.unmarshal_short() & 0xFFFFL;
			else if (length == 127)
				length = os.unmarshal_long();
			if (length < 0 || length + data.size() > maxMessageSize)
				throw new RFC6455Exception(closeSizeExceed,
						"messageSize = " + (length + data.size()) + " but MaxMessageSize = " + maxMessageSize);
			int len = (int) length;
			byte[] mask = new byte[] { os.unmarshal_byte(), os.unmarshal_byte(), os.unmarshal_byte(),
					os.unmarshal_byte() };
			if (os.remain() >= len) {
				int pos = os.position();
				int endpos = pos + len;
				byte[] array = os.array();
				for (int i = pos, j = 0; i < endpos; i++, j = (j + 1) & 3)
					array[i] ^= mask[j];
				if (fin) {
					if (data.size() == 0)
						res = Arrays.copyOfRange(array, pos, endpos);
					else {
						data.insert(data.size(), os, pos, endpos - pos);
						res = data.getBytes();
						data.clear();
					}
				} else
					data.insert(data.size(), os, pos, endpos - pos);
				os.position(endpos);
				os.commit();
			} else
				os.rollback();
		} catch (MarshalException e) {
			os.rollback();
		}
		return res;
	}

	public byte getOpcode() {
		return opcode;
	}

	public static ByteBuffer wrap(byte opcode, byte[] data, int off, int len) {
		ByteBuffer bb = ByteBuffer.allocateDirect(len + 14);
		bb.put((byte) (opcode | 0x80));
		if (len < 126) {
			bb.put((byte) len);
		} else if (len < 65536) {
			bb.put((byte) 126);
			bb.put((byte) (len >> 8));
			bb.put((byte) (len));
		} else {
			bb.put((byte) 127);
			bb.putLong(len);
		}
		bb.put(data, off, len).flip();
		return bb;
	}

	public static void wrap(Codec codec, byte opcode, byte[] data, int off, int len) throws CodecException {
		codec.update((byte) (opcode | 0x80));
		if (len < 126) {
			codec.update((byte) len);
		} else if (len < 65536) {
			codec.update((byte) 126);
			codec.update((byte) (len >> 8));
			codec.update((byte) (len));
		} else {
			codec.update((byte) 127);
			codec.update((byte) (len >> 56));
			codec.update((byte) (len >> 48));
			codec.update((byte) (len >> 40));
			codec.update((byte) (len >> 32));
			codec.update((byte) (len >> 24));
			codec.update((byte) (len >> 16));
			codec.update((byte) (len >> 8));
			codec.update((byte) (len));
		}
		codec.update(data, off, len);
	}
}

class WebSocketServerTask implements NetProcessor {
	private final static long handShakeTimeout = Long.getLong("limax.net.WebSocketServer.handShakeTimeout", 1000);
	private final static long keyExchangeTimeout = Long.getLong("limax.net.WebSocketServer.keyExchangeTimeout", 3000);
	private final static long dhGroupMax = Long.getLong("limax.net.WebSocketServer.dhGroupMax", 2);
	private final static byte[] secureIp;
	private final static int maxRequestSize = 16384;
	private final static byte[] zero = new byte[0];
	private final NetTask nettask;
	private final WebSocketProcessor processor;
	private final RFC6455Server server = new RFC6455Server();
	private byte[] request = new byte[0];
	private URI requestURI;
	private URI origin;
	private SocketAddress local;
	private SocketAddress peer;
	private byte lastOpcode;
	private Codec isec;
	private Codec osec;
	private Octets ibuf;
	private Octets obuf;

	static {
		byte[] _secureIp;
		try {
			_secureIp = InetAddress.getByName(System.getProperty("limax.net.secureIp")).getAddress();
		} catch (UnknownHostException e) {
			_secureIp = null;
		}
		secureIp = _secureIp;
	}

	WebSocketServerTask(ServerContext context, WebSocketProcessor processor) {
		this.processor = processor;
		this.nettask = NetModel.createServerTask(context, this);
	}

	NetTask getNetTask() {
		return nettask;
	}

	private boolean _fillRequest(byte[] in) {
		int l = request.length;
		request = Arrays.copyOf(request, l + in.length);
		System.arraycopy(in, 0, request, l, in.length);
		l = request.length;
		return l >= 4 && request[l - 4] == 0xd && request[l - 3] == 0xa && request[l - 2] == 0xd
				&& request[l - 1] == 0xa;
	}

	private boolean fillRequest(byte[] in) {
		boolean r = _fillRequest(in);
		if (request.length > maxRequestSize)
			throw new IllegalArgumentException(
					"maxRequestSize = " + maxRequestSize + " but requestLength = " + request.length);
		return r;
	}

	private static class ParseResult {
		private final String message;
		private final boolean succeed;

		ParseResult(String message, boolean succeed) {
			this.message = message;
			this.succeed = succeed;
		}
	}

	private String toResult(String swkey, String additional) {
		return "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
				+ Base64.getEncoder()
						.encodeToString(SHA1.digest((swkey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes()))
				+ "\r\n" + additional + "\r\n";
	}

	private ParseResult parseRequest() throws Exception {
		String[] header = new String(request, StandardCharsets.UTF_8).split("(\r\n)");
		String method = header[0];
		if (!method.startsWith("GET"))
			throw new IllegalArgumentException("Invalid method <" + method + ">");
		requestURI = URI.create(method.substring(4, method.length() - 9).trim());
		if (Double.parseDouble(method.substring(method.lastIndexOf('/') + 1, method.length())) < 1.1)
			throw new IllegalArgumentException("Invalid version <" + method.substring(method.length() - 8) + ">");
		String swkey = "";
		String security = "";
		int check = 0;
		for (int i = 1, n = header.length; i < n; i++) {
			int pos = header[i].indexOf(":");
			String key = header[i].substring(0, pos).trim().toLowerCase();
			String val = header[i].substring(pos + 1, header[i].length()).trim();
			switch (key) {
			case "connection":
				if (val.toLowerCase().lastIndexOf("upgrade") == -1)
					throw new IllegalArgumentException("Invalid Header<" + header[i] + ">");
				check |= 1;
				break;
			case "upgrade":
				if (!val.equalsIgnoreCase("websocket"))
					throw new IllegalArgumentException("Invalid Header<" + header[i] + ">");
				check |= 2;
				break;
			case "sec-websocket-version":
				if (!val.equals("13"))
					return new ParseResult("HTTP/1.1 400 Bad Request\r\nSec-WebSocket-Version: 13\r\n\r\n", false);
				check |= 4;
				break;
			case "sec-websocket-key":
				swkey = val;
				check |= 8;
				break;
			case "origin":
				try {
					origin = URI.create(val);
					check |= 16;
				} catch (Exception e) {
				}
				break;
			case "x-limax-security":
				security = val;
				check |= 32;
			}
		}
		if ((check & 16) == 0) {
			origin = URI.create("null");
			check |= 16;
		}
		if ((check & 31) != 31)
			throw new IllegalArgumentException("incompletion head " + check);
		if (check == 31)
			return new ParseResult(toResult(swkey, ""), true);
		int pos = security.indexOf(';');
		int group = Integer.parseInt(security.substring(0, pos));
		if (group > dhGroupMax || !Helper.isDHGroupSupported(group))
			throw new IllegalArgumentException("unsupported dhgroup " + group);
		nettask.resetAlarm(keyExchangeTimeout);
		BigInteger data = new BigInteger(Base64.getDecoder().decode(security.substring(pos + 1, security.length())));
		BigInteger rand = Helper.makeDHRandom();
		byte[] material = Helper.computeDHKey(group, data, rand).toByteArray();
		int half = material.length / 2;
		byte[] key = secureIp != null ? secureIp : ((InetSocketAddress) local).getAddress().getAddress();
		HmacMD5 mac = new HmacMD5(key, 0, key.length);
		mac.update(material, 0, half);
		isec = new Decrypt(new RFC2118Decode(new SinkOctets(ibuf = new Octets())), mac.digest());
		mac = new HmacMD5(key, 0, key.length);
		mac.update(material, half, material.length - half);
		osec = new RFC2118Encode(new Encrypt(new SinkOctets(obuf = new Octets()), mac.digest()));
		((Transport) processor).setSessionObject(Octets.wrap(material));
		return new ParseResult(toResult(swkey, "X-Limax-Security: "
				+ Base64.getEncoder().encodeToString(Helper.generateDHResponse(group, rand).toByteArray()) + "\r\n"),
				false);
	}

	private void handshaked() throws Exception {
		request = null;
		if (processor.startup(new WebSocketTask() {
			@Override
			public void send(String text) {
				byte[] data = text.getBytes(StandardCharsets.UTF_8);
				WebSocketServerTask.this.send(RFC6455Server.opcodeText, data, 0, data.length);
			}

			@Override
			public void send(byte[] binary) {
				WebSocketServerTask.this.send(RFC6455Server.opcodeBinary, binary, 0, binary.length);
			}

			@Override
			public void sendFinal(long timeout) {
				sendClose(new RFC6455Exception(RFC6455Server.closeNormal, "Close Normal"), timeout);
			}

			@Override
			public void sendFinal() {
				sendFinal(0);
			}

			@Override
			public void resetAlarm(long millisecond) {
				nettask.resetAlarm(millisecond);
			}

			@Override
			public void enable() {
				nettask.enable();
			}

			@Override
			public void disable() {
				nettask.disable();
			}

			@Override
			public SSLSession getSSLSession() {
				return nettask.getSSLSession();
			}
		}, local, new WebSocketAddress(peer, requestURI, origin)))
			nettask.enable();
		else
			nettask.disable();
	}

	@Override
	public void process(byte[] in) throws Exception {
		if (isec != null) {
			isec.update(in, 0, in.length);
			isec.flush();
			in = ibuf.getBytes();
			ibuf.clear();
		}
		if (request != null) {
			if (isec != null) {
				byte[] data = server.unwrap(in);
				if (data == null)
					return;
				requestURI = URI.create(new String(data, StandardCharsets.UTF_8));
				handshaked();
				in = zero;
			} else {
				if (!fillRequest(in))
					return;
				try {
					ParseResult r = parseRequest();
					nettask.send(r.message.getBytes());
					if (r.succeed)
						handshaked();
					else if (isec == null)
						nettask.sendFinal();
				} catch (Exception e) {
					if (Trace.isErrorEnabled())
						Trace.error(e);
					nettask.send("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes());
					nettask.sendFinal();
				}
				return;
			}
		}
		while (true) {
			try {
				byte[] data = server.unwrap(in);
				if (data == null)
					return;
				in = zero;
				byte opcode = server.getOpcode();
				if (opcode == RFC6455Server.opcodeCont)
					opcode = lastOpcode;
				else
					lastOpcode = opcode;
				switch (opcode) {
				case RFC6455Server.opcodePing:
					nettask.send(RFC6455Server.wrap(RFC6455Server.opcodePong, data, 0, data.length));
					break;
				case RFC6455Server.opcodeText:
					processor.process(new String(data, StandardCharsets.UTF_8));
					break;
				case RFC6455Server.opcodeBinary:
					processor.process(data);
					break;
				case RFC6455Server.opcodeClose:
					if (data.length >= 2) {
						int code = ((data[0] << 8) & 0xff) + (data[1] & 0xff);
						String reason = new String(data, 2, data.length - 2, StandardCharsets.UTF_8);
						processor.process(code, reason);
					} else
						processor.process(1000, "");
					nettask.sendFinal();
					return;
				case RFC6455Server.opcodePong:
					break;
				default:
					throw new RFC6455Exception(RFC6455Server.closeNotSupportFrame, "Invalid Frame opcode = " + opcode);
				}
			} catch (RFC6455Exception e) {
				sendClose(e, 0);
				return;
			}
		}
	}

	@Override
	public void shutdown(Throwable closeReason) {
		processor.shutdown(closeReason);
	}

	@Override
	public boolean startup(NetTask nettask, SocketAddress local, SocketAddress peer) {
		this.local = local;
		this.peer = peer;
		if (nettask.isSSLSupported())
			nettask.attachSSL(null);
		nettask.resetAlarm(handShakeTimeout);
		return true;
	}

	private void send(byte opcode, byte[] data, int off, int len) {
		if (osec != null)
			try {
				synchronized (osec) {
					RFC6455Server.wrap(osec, opcode, data, 0, data.length);
					osec.flush();
					nettask.send(obuf.getBytes());
					obuf.clear();
				}
			} catch (CodecException e) {
				if (Trace.isWarnEnabled())
					Trace.warn("websocket send", e);
				nettask.sendFinal();
			}
		else
			nettask.send(RFC6455Server.wrap(opcode, data, 0, data.length));
	}

	private void sendClose(RFC6455Exception e, long timeout) {
		byte[] code = e.getCode();
		send(RFC6455Server.opcodeClose, code, 0, code.length);
		nettask.sendFinal(timeout);
	}
}

public class WebSocketServer {
	private WebSocketServer() {
	}

	public static NetTask createServerTask(ServerContext context, WebSocketProcessor processor) {
		return new WebSocketServerTask(context, processor).getNetTask();
	}
}
