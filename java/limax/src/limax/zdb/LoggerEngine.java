package limax.zdb;

import java.io.IOException;

interface LoggerEngine {
	void close();

	void checkpoint();

	void backup(String path, boolean increment) throws IOException;

	void dropTables(String[] tableNames) throws Exception;
}
