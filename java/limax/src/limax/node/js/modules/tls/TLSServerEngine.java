package limax.node.js.modules.tls;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.StandardConstants;

public class TLSServerEngine extends SSLEngine {
	private final SSLEngine impl;
	private ByteBuffer hello = ByteBuffer.allocateDirect(4096);

	public TLSServerEngine(SSLEngine impl) {
		this.impl = impl;
	}

	@Override
	public void beginHandshake() throws SSLException {
		impl.beginHandshake();
	}

	@Override
	public void closeInbound() throws SSLException {
		impl.closeInbound();
	}

	@Override
	public void closeOutbound() {
		impl.closeOutbound();
	}

	@Override
	public Runnable getDelegatedTask() {
		return impl.getDelegatedTask();
	}

	@Override
	public boolean getEnableSessionCreation() {
		return impl.getEnableSessionCreation();
	}

	@Override
	public String[] getEnabledCipherSuites() {
		return impl.getEnabledCipherSuites();
	}

	@Override
	public String[] getEnabledProtocols() {
		return impl.getEnabledProtocols();
	}

	@Override
	public HandshakeStatus getHandshakeStatus() {
		return impl.getHandshakeStatus();
	}

	@Override
	public boolean getNeedClientAuth() {
		return impl.getNeedClientAuth();
	}

	@Override
	public SSLSession getSession() {
		return impl.getSession();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return impl.getSupportedCipherSuites();
	}

	@Override
	public String[] getSupportedProtocols() {
		return impl.getSupportedProtocols();
	}

	@Override
	public boolean getUseClientMode() {
		return impl.getUseClientMode();
	}

	@Override
	public boolean getWantClientAuth() {
		return impl.getWantClientAuth();
	}

	@Override
	public boolean isInboundDone() {
		return impl.isInboundDone();
	}

	@Override
	public boolean isOutboundDone() {
		return impl.isOutboundDone();
	}

	@Override
	public void setEnableSessionCreation(boolean flag) {
		impl.setEnableSessionCreation(flag);
	}

	@Override
	public void setEnabledCipherSuites(String[] suites) {
		impl.setEnabledCipherSuites(suites);
	}

	@Override
	public void setEnabledProtocols(String[] protocols) {
		impl.setEnabledProtocols(protocols);
	}

	@Override
	public void setNeedClientAuth(boolean need) {
		impl.setNeedClientAuth(need);
	}

	@Override
	public void setUseClientMode(boolean mode) {
		impl.setUseClientMode(mode);
	}

	@Override
	public void setWantClientAuth(boolean want) {
		impl.setWantClientAuth(want);
	}

	@Override
	public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) throws SSLException {
		if (hello == null)
			return impl.unwrap(src, dsts, offset, length);
		int len = src.remaining();
		if (len > 0) {
			len += hello.position();
			if (len > hello.capacity()) {
				hello.flip();
				hello = ByteBuffer.allocateDirect(len).put(hello);
			}
			hello.put(src.duplicate());
		}
		SSLEngineResult rs = impl.unwrap(src, dsts, offset, length);
		if (rs.getStatus() != Status.BUFFER_UNDERFLOW) {
			hello.flip();
			try {
				List<SNIServerName> serverNames = extractServerNames(hello);
				if (!serverNames.isEmpty()) {
					SSLParameters params = impl.getSSLParameters();
					params.setServerNames(serverNames);
					impl.setSSLParameters(params);
				}
			} catch (Exception e) {
			} finally {
				hello = null;
			}
		}
		return rs;
	}

	@Override
	public SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst) throws SSLException {
		return impl.wrap(srcs, offset, length, dst);
	}

	private static int getInt8(ByteBuffer in) {
		return in.get();
	}

	private static int getInt16(ByteBuffer in) {
		return ((in.get() & 0xff) << 8) | (in.get() & 0xff);
	}

	private static int getInt24(ByteBuffer in) {
		return ((in.get() & 0xff) << 16) | ((in.get() & 0xff) << 8) | (in.get() & 0xff);
	}

	private static void bypass(ByteBuffer in, int length) {
		in.position(in.position() + length);
	}

	private List<SNIServerName> extractServerNames(ByteBuffer in) {
		byte b0 = in.get();
		bypass(in, 1);
		byte b2 = in.get();
		if ((b0 & 0x80) != 0 && b2 == 0x01)
			return Collections.emptyList();
		bypass(in, 3);
		in.limit(getInt24(in) + in.position());
		bypass(in, 34);
		bypass(in, getInt8(in));
		bypass(in, getInt16(in));
		bypass(in, getInt8(in));
		if (!in.hasRemaining())
			return Collections.emptyList();
		for (int length = getInt16(in); length > 0;) {
			int extType = getInt16(in);
			int extLen = getInt16(in);
			if (extType == 0) {
				List<SNIServerName> serverNames = new ArrayList<>();
				for (int count = getInt16(in); count > 0;) {
					int code = getInt8(in);
					int len = getInt16(in);
					byte[] encoded = new byte[len];
					in.get(encoded);
					if (code == StandardConstants.SNI_HOST_NAME)
						serverNames.add(0, new SNIHostName(encoded));
					else
						serverNames.add(new SNIServerName(code, encoded) {
						});
					count -= encoded.length + 3;
				}
				return serverNames;
			} else
				bypass(in, extLen);
			length -= extLen + 4;
		}
		return Collections.emptyList();
	}
}
