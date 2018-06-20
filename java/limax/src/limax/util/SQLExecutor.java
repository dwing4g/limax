package limax.util;

@FunctionalInterface
public interface SQLExecutor {
	void execute(SQLConnectionConsumer consumer) throws Exception;
}