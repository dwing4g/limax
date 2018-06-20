package limax.xmlgen;

import java.util.Set;

public interface Dependency {
	void depends(Set<Type> types);
}
