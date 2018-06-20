package limax.zdb.tool;

public enum ConvertType {
	/**
	 * <p>
	 * no change
	 * <p>
	 * bool -&gt; byte
	 */
	SAME,

	/**
	 * <p>
	 * bool/byte, short, int, long; from left -&gt; right;
	 * <p>
	 * float -&gt; double;
	 * <p>
	 * bean has child as auto, or not as key and less child (abc -&gt; ab);
	 * <p>
	 * collection element as auto;
	 */
	AUTO,

	/**
	 * <p>
	 * byte/short/int/long -&gt; float/double, may lose precision;
	 * <p>
	 * bean has child as maybe_auto, or more child (ab -&gt; abc), or not as key
	 * and less-more child (abc -&gt; abd)
	 * <p>
	 * collection element as maybe_auto;
	 */
	MAYBE_AUTO,

	/**
	 * other
	 */
	MANUAL
}