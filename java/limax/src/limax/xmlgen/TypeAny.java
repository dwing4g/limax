package limax.xmlgen;

public class TypeAny extends Type {

	private final String typeName;
	private String resolvedTypeName = null;

	TypeAny(Naming parent, String name) {
		super(parent);
		typeName = name;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(this);
	}

	@Override
	public boolean isConstType() {
		return false;
	}

	public String getTypeName() {
		if (null == resolvedTypeName)
			resolvedTypeName = _getTypeName();
		return resolvedTypeName;
	}

	private String _getTypeName() {
		final String[] names = typeName.split("\\.");
		View v = search(names, View.class);
		if (v != null) {
			Service viewservice = null;
			for (Service s : Main.currentProject.getServices()) {
				if (s.isUseZdb()) {
					if (null != viewservice)
						throw new RuntimeException(
								"unsupported multi serivce, please use view fullname([projectname].[servicename].[viewfullname])!");
					viewservice = s;
				}
			}
			return null == viewservice ? typeName : (viewservice.getFullName() + "." + v.getFullName());
		}
		Bean b = search(names, Bean.class);
		return b != null ? b.getFullName() : typeName;
	}

	@Override
	public boolean isAny() {
		return true;
	}

	@Override
	public boolean isJSONSerializable() {
		return false;
	}
}
