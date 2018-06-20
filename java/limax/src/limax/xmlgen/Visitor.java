package limax.xmlgen;

public interface Visitor {

	void visit(TypeBoolean type);

	void visit(TypeByte type);

	void visit(TypeShort type);

	void visit(TypeInt type);

	void visit(TypeLong type);

	void visit(TypeFloat type);

	void visit(TypeDouble type);

	void visit(TypeBinary type);

	void visit(TypeString type);

	void visit(TypeList type);

	void visit(TypeSet type);

	void visit(TypeVector type);

	void visit(TypeMap type);

	void visit(Bean type); // net only

	void visit(Cbean type); // zdb only

	void visit(Xbean type); // zdb only

	void visit(TypeAny type); // zdb only
}
