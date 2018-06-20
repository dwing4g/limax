package limax.zdb;

final class LogKey {
	private final XBean xbean;
	private final String varname;

	LogKey(XBean xbean, String varname) {
		this.xbean = xbean;
		this.varname = varname;
	}

	XBean getXBean() {
		return xbean;
	}

	String getVarname() {
		return varname;
	}

	Object getValue() {
		return XBeanInfo.getValue(xbean, varname);
	}

	void setValue(Object value) {
		XBeanInfo.setValue(xbean, varname, value);
	}

	@Override
	public int hashCode() {
		return xbean._objid_.hashCode() ^ varname.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof LogKey))
			return false;
		LogKey l = (LogKey) o;
		return xbean._objid_.equals(l.xbean._objid_) && varname.equals(l.varname);
	}

	@Override
	public String toString() {
		return xbean.getClass().getSimpleName() + "." + varname;
	}
}
