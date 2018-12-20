package limax.sql;

import java.sql.Connection;

@FunctionalInterface
public interface SQLConnectionConsumer {
	void accept(Connection conn) throws Exception;
}