package limax.util;

/**
 * base on System.nanoTime
 */
public final class Elapse {
	long start = System.nanoTime();

	/**
	 * @return now - start
	 */
	public long elapsed() {
		return System.nanoTime() - start;
	}

	/**
	 * start = now;
	 */
	public void reset() {
		start = System.nanoTime();
	}

	/**
	 * see elapse and reset
	 * 
	 * @return elapsed
	 */
	public long elapsedAndReset() {
		long now = System.nanoTime();
		long elapse = now - start;
		start = now;
		return elapse;
	}

	/**
	 * @return now
	 */
	public long now() {
		return System.nanoTime();
	}

	public long getStart() {
		return start;
	}
}
