package limax.zdb;

public interface Table {
	enum Persistence {
		MEMORY, DB
	}

	String getName();

	Persistence getPersistence();
}
