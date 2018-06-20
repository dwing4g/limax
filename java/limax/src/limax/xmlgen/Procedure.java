package limax.xmlgen;

import org.w3c.dom.Element;

import limax.util.ElementHelper;
import limax.util.Trace;

public class Procedure extends Naming {
	private int maxExecutionTime = 0;
	private int retryTimes = 3;
	private int retryDelay = 100;
	private boolean retrySerial = false;
	private Trace trace = Trace.WARN;

	public Procedure(Zdb parent, Element self) throws Exception {
		super(parent, self);
		initialize(self);
	}

	public void initialize(Element self) {
		ElementHelper eh = new ElementHelper(self);
		maxExecutionTime = eh.getInt("maxExecutionTime", maxExecutionTime);
		retryTimes = eh.getInt("retryTimes", retryTimes);
		retryDelay = eh.getInt("retryDelay", retryDelay);
		retrySerial = eh.getBoolean("retrySerial", retrySerial);
		String tmp = eh.getString("trace");
		if (!tmp.isEmpty())
			trace = Trace.valueOf(tmp);
		eh.warnUnused();
	}

	public static final class Builder {
		Procedure procedure;

		public Builder(Zdb zdb) {
			procedure = new Procedure(zdb);
		}

		public Procedure build() {
			return procedure;
		}

		public Builder maxExecutionTime(int maxExecutionTime) {
			procedure.maxExecutionTime = maxExecutionTime;
			return this;
		}

		public Builder retryTimes(int retryTimes) {
			procedure.retryTimes = retryTimes;
			return this;
		}

		public Builder retryDelay(int retryDelay) {
			procedure.retryDelay = retryDelay;
			return this;
		}

		public Builder retrySerial(boolean retrySerial) {
			procedure.retrySerial = retrySerial;
			return this;
		}

		public Builder trace(Trace trace) {
			procedure.trace = trace;
			return this;
		}
	}

	Procedure(Zdb parent) {
		super(parent, "");
	}

	public Trace getTrace() {
		return trace;
	}

	public int getRetryTimes() {
		return retryTimes;
	}

	public int getRetryDelay() {
		return retryDelay;
	}

	public boolean getRetrySerial() {
		return retrySerial;
	}

	public int getMaxExecutionTime() {
		return maxExecutionTime;
	}

}
