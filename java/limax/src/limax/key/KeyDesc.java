package limax.key;

import java.util.Arrays;

import javax.security.auth.Destroyable;

public class KeyDesc implements Destroyable {
	private volatile boolean destroyed = false;
	private final byte[] ident;
	private final byte[] key;

	KeyDesc(byte[] ident, byte[] key) {
		this.ident = ident;
		this.key = key;
	}

	public byte[] getIdent() {
		if (isDestroyed())
			throw new IllegalStateException("KeyDesc has been destroyed.");
		return ident;
	}

	public byte[] getKey() {
		if (isDestroyed())
			throw new IllegalStateException("KeyDesc has been destroyed.");
		return key;
	}

	@Override
	public void destroy() {
		destroyed = true;
		Arrays.fill(key, (byte) 0);
	}

	@Override
	public boolean isDestroyed() {
		return destroyed;
	}
}
