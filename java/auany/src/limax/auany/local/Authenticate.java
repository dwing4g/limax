package limax.auany.local;

import java.util.function.Consumer;

interface Authenticate {
	enum Result {
		Accept, Reject, Timeout, Fail
	}

	void access(String username, String password, Consumer<Result> response);

	void stop();
}
