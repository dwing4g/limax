package limax.endpoint;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import limax.codec.Base64Decode;
import limax.codec.Base64Encode;
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
import limax.codec.SinkOctets;
import limax.endpoint.script.LmkDataReceiver;
import limax.endpoint.script.ScriptEngineHandle;
import limax.endpoint.script.ScriptSender;
import limax.net.Engine;
import limax.net.io.NetModel;
import limax.net.io.NetProcessor;
import limax.net.io.NetTask;
import limax.util.Closeable;
import limax.util.Helper;

class WebSocketConnector implements NetProcessor, ScriptSender, LmkDataReceiver, Closeable {
	private final static int DHGroup = Integer.getInteger("limax.endpoint.WebSocketConnector.DHGroup", 2);
	private final int rsize = Integer.getInteger("limax.endpoint.WebSocketConnector.SO_RCVBUF", 131072);
	private final int wsize = Integer.getInteger("limax.endpoint.WebSocketConnector.SO_SNDBUF", 131072);

	private enum ReadyState {
		CONNECTING, OPEN, CLOSING, CLOSED
	}

	private enum CloseStatus {
		CONNECTION_FAIL, ACTIVE_CLOSE, PASSIVE_CLOSE
	}

	private final LoginConfig loginConfig;
	private final ScriptEngineHandle handle;
	private final Executor executor;
	private final NetTask nettask;
	private final OctetsStream os = new OctetsStream();
	private ReadyState readyState = ReadyState.CONNECTING;
	private Future<?> timer;
	private int reportError;
	private int stage;
	private StringBuilder sbhead = new StringBuilder();
	private byte[] key;
	private BigInteger dhRandom;
	private Codec isec;
	private Codec osec;
	private Octets ibuf = new Octets();
	private Octets obuf = new Octets();

	private void process(int t, Object p) {
		try {
			switch (handle.action(t, p)) {
			case 2:
				timer = Engine.getProtocolScheduler().scheduleWithFixedDelay(new Runnable() {
					@Override
					public void run() {
						send(" ");
					}
				}, 50000, 50000, TimeUnit.MILLISECONDS);
				break;
			case 3:
				close();
			}
		} catch (Exception e) {
			close();
		}
	}

	private void onopen() {
	}

	private void onmessage(final String message) {
		if (Character.isDigit(message.charAt(0)))
			reportError = Integer.parseInt(message);
		else
			executor.execute(new Runnable() {
				@Override
				public void run() {
					process(1, message);
				}
			});
	}

	private void onclose(final CloseStatus cs) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				if (timer != null)
					timer.cancel(true);
				process(2, cs.toString() + " " + reportError);
			}
		});
	}

	@Override
	public void onLmkData(String lmkdata, Runnable done) throws Exception {
		if (!lmkdata.isEmpty()) {
			LmkUpdater lmkUpdater = loginConfig.getLmkUpdater();
			if (lmkUpdater != null)
				lmkUpdater.update(Octets.wrap(Base64Decode.transform(lmkdata.getBytes())), done);
		}
	}

	@Override
	public synchronized void process(byte[] in) throws Exception {
		switch (readyState) {
		case CONNECTING:
			for (int i = 0, len = in.length; i < len; i++) {
				byte b = in[i];
				sbhead.append((char) b);
				switch (stage) {
				case 0:
					stage = b == '\r' ? 1 : 0;
					break;
				case 1:
					stage = b == '\n' ? 2 : 0;
					break;
				case 2:
					stage = b == '\r' ? 3 : 0;
					break;
				case 3:
					if (b == '\n') {
						String head = sbhead.toString();
						String security = null;
						for (int spos = 0, epos; security == null
								&& (epos = head.indexOf('\n', spos)) != -1; spos = epos + 1) {
							int mpos = head.lastIndexOf(':', epos);
							if (mpos > spos && head.substring(spos, mpos).trim().equalsIgnoreCase("x-limax-security"))
								security = head.substring(mpos + 1, epos).trim();
						}
						if (security == null) {
							close();
							return;
						}
						byte[] material = Helper.computeDHKey(DHGroup,
								new BigInteger(Base64Decode.transform(security.getBytes())), dhRandom).toByteArray();
						int half = material.length / 2;
						HmacMD5 mac = new HmacMD5(key, 0, key.length);
						mac.update(material, 0, half);
						osec = new RFC2118Encode(new Encrypt(new SinkOctets(obuf), mac.digest()));
						mac = new HmacMD5(key, 0, key.length);
						mac.update(material, half, material.length - half);
						isec = new Decrypt(new RFC2118Decode(new SinkOctets(ibuf)), mac.digest());
						StringBuilder pvids = new StringBuilder();
						for (int pvid : handle.getProviders())
							pvids.append(pvid).append(',');
						pvids.deleteCharAt(pvids.length() - 1);
						StringBuilder keys = new StringBuilder(";");
						for (String v : handle.getDictionaryCache().keys())
							keys.append(v).append(',');
						keys.deleteCharAt(keys.length() - 1);
						String query = "/?username=" + loginConfig.getUsername() + "&token="
								+ loginConfig.getToken(Octets.wrap(material)) + "&platflag=" + loginConfig.getPlatflag()
								+ keys + "&pvids=" + pvids.toString();
						send(query.getBytes("UTF-8"));
						readyState = ReadyState.OPEN;
						onopen();
						return;
					} else
						stage = 0;
				}
			}
			break;
		case OPEN: {
			isec.update(in, 0, in.length);
			isec.flush();
			os.insert(os.size(), ibuf.getBytes());
			ibuf.clear();
			while (true) {
				os.begin();
				try {
					int opcode = os.unmarshal_byte() & 0xff;
					int len = os.unmarshal_byte() & 0x7f;
					switch (len) {
					case 126:
						len = os.unmarshal_short() & 0xffff;
						break;
					case 127:
						len = (int) os.unmarshal_long();
					}
					if (os.remain() >= len) {
						int pos = os.position();
						int endpos = pos + len;
						if (opcode == 0x81)
							onmessage(new String(Arrays.copyOfRange(os.array(), pos, endpos), "UTF-8"));
						os.position(endpos);
						os.commit();
					} else {
						os.rollback();
						break;
					}
				} catch (MarshalException e) {
					os.rollback();
					break;
				}
			}
		}
			break;
		case CLOSING:
			nettask.sendFinal();
		default:
		}
	}

	private void send(byte[] data) throws CodecException {
		int len = data.length;
		osec.update((byte) 0x81);
		if (len < 126)
			osec.update((byte) (len | 0x80));
		else if (len < 65536) {
			osec.update((byte) 254);
			osec.update((byte) (len >> 8));
			osec.update((byte) (len));
		} else {
			osec.update((byte) 255);
			osec.update((byte) (len >> 56));
			osec.update((byte) (len >> 48));
			osec.update((byte) (len >> 40));
			osec.update((byte) (len >> 32));
			osec.update((byte) (len >> 24));
			osec.update((byte) (len >> 16));
			osec.update((byte) (len >> 8));
			osec.update((byte) (len));
		}
		osec.update((byte) 0);
		osec.update((byte) 0);
		osec.update((byte) 0);
		osec.update((byte) 0);
		osec.update(data, 0, data.length);
		osec.flush();
		nettask.send(obuf.getBytes());
		obuf.clear();
	}

	@Override
	public synchronized Throwable send(String s) {
		if (readyState != ReadyState.OPEN)
			return null;
		try {
			send(s.getBytes("UTF-8"));
		} catch (Exception e) {
			return e;
		}
		return null;
	}

	@Override
	public synchronized void shutdown(Throwable closeReason) {
		onclose(dhRandom != null
				? readyState == ReadyState.CLOSING ? CloseStatus.ACTIVE_CLOSE : CloseStatus.PASSIVE_CLOSE
				: CloseStatus.CONNECTION_FAIL);
		readyState = ReadyState.CLOSED;
	}

	@Override
	public synchronized boolean startup(NetTask nettask, SocketAddress local, SocketAddress peer) throws Exception {
		dhRandom = Helper.makeDHRandom();
		StringBuilder sb = new StringBuilder(
				"GET / HTTP/1.1\r\nConnection: Upgrade\r\nUpgrade: WebSocket\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: AQIDBAUGBwgJCgsMDQ4PEC==\r\nOrigin: null\r\n");
		sb.append("X-Limax-Security: ").append(DHGroup).append(';')
				.append(new String(Base64Encode.transform(Helper.generateDHResponse(DHGroup, dhRandom).toByteArray())))
				.append("\r\n\r\n");
		nettask.send(sb.toString().getBytes());
		key = ((InetSocketAddress) peer).getAddress().getAddress();
		return true;
	}

	@Override
	public void close() {
		if (Engine.remove(this))
			return;
		synchronized (this) {
			switch (readyState) {
			case OPEN:
				nettask.sendFinal();
			case CONNECTING:
				readyState = ReadyState.CLOSING;
			default:
			}
		}
	}

	public WebSocketConnector(String host, int port, LoginConfig loginConfig, ScriptEngineHandle handle,
			Executor executor) throws Exception {
		this.loginConfig = loginConfig;
		this.handle = handle;
		this.executor = executor;
		this.reportError = 0;
		this.stage = 0;
		handle.registerScriptSender(this);
		handle.registerLmkDataReceiver(this);
		if (loginConfig.getProviderLoginDataManager() != null)
			handle.registerProviderLoginManager(loginConfig.getProviderLoginDataManager());
		NetModel.addClient(new InetSocketAddress(host, port),
				nettask = NetModel.createClientTask(rsize, wsize, null, this, false));
		Engine.add(this);
	}
}
