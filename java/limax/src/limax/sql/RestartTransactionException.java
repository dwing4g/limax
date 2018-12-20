package limax.sql;

public class RestartTransactionException extends RuntimeException {
	private static final long serialVersionUID = -2725158761351281195L;

	RestartTransactionException(Throwable e) {
		super(e);
	}
}