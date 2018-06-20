package limax.zdb;

import java.util.ArrayDeque;
import java.util.Deque;

final class LogNotify {
	private final Deque<LogKey> path = new ArrayDeque<>();
	private final Note note;

	private LogNotify(LogKey logkey, Note note) {
		path.add(logkey);
		this.note = note;
	}

	boolean isLast() {
		return path.isEmpty();
	}

	LogKey pop() {
		return path.pop();
	}

	LogNotify push(LogKey logkey) {
		path.push(logkey);
		return this;
	}

	Note getNote() {
		return note;
	}

	static void notify(LogKey logkey, Note note) {
		logkey.getXBean().notify(new LogNotify(logkey, note));
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (LogKey logKey : path)
			sb.append('.').append(logKey.getVarname());
		sb.append('=').append(note);
		return sb.toString();
	}
}
