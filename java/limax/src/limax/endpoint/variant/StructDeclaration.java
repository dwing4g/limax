package limax.endpoint.variant;

import java.util.Collection;

public interface StructDeclaration extends Declaration {

	public interface Field {

		String getName();

		Declaration getDeclaration();

	}

	Collection<? extends Field> getFields();

}
