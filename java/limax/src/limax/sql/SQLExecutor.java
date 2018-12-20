package limax.sql;

@FunctionalInterface
public interface SQLExecutor {
	void execute(SQLConnectionConsumer consumer) throws Exception;

	final static SQLConnectionConsumer COMMIT = conn -> conn.commit();
	final static SQLConnectionConsumer ROLLBACK = conn -> conn.rollback();
}