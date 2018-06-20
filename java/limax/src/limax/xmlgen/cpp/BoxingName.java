package limax.xmlgen.cpp;

import limax.xmlgen.Type;
import limax.xmlgen.TypeList;
import limax.xmlgen.TypeMap;
import limax.xmlgen.TypeSet;
import limax.xmlgen.TypeVector;

class BoxingName extends TypeName {

	public static String getName(Type type) {
		BoxingName visitor = new BoxingName();
		type.accept(visitor);
		return visitor.getName();
	}

	@Override
	public void visit(TypeList type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeVector type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeSet type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeMap type) {
		throw new UnsupportedOperationException();
	}
}
