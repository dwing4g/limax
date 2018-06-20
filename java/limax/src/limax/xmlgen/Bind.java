package limax.xmlgen;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public class Bind extends Naming implements Dependency {
	private final String tableName;
	private Table table;
	private final boolean clip;
	private final boolean snapshot;

	public Bind(View parent, Element self) throws Exception {
		super(parent, self);
		Variable.verifyName(this);
		ElementHelper eh = new ElementHelper(self);
		this.tableName = eh.getString("table");
		this.clip = eh.getBoolean("clip", false);
		this.snapshot = eh.getBoolean("snapshot", false);
		eh.warnUnused("name");
	}

	@Override
	public boolean resolve() {
		table = search(new String[] { tableName }, Table.class);
		if (table == null)
			return false;
		if (!getRefs().stream().allMatch(ref -> ref.resolve()))
			return false;
		if (getRefs().isEmpty()) {
			if (table.getValueType().isAny())
				throw new RuntimeException(
						"Bind " + getView().getFullName() + "." + getName() + " depend XBean with type any.");
		} else {
			getRefs().stream().filter(ref -> ref.getVariable().getType().isAny()).findFirst().ifPresent(var -> {
				throw new RuntimeException("Bind ref " + getView().getFullName() + "." + getName() + "." + var.getName()
						+ " depend type any.");
			});
		}
		return true;
	}

	public Table getTable() {
		return table;
	}

	public Type getValueType() {
		return table.getValueType();
	}

	public List<Ref> getRefs() {
		return getChildren(Ref.class);
	}

	public View getView() {
		return (View) getParent();
	}

	public List<Variable> getVariables() {
		return getRefs().stream().map(ref -> ref.getVariable()).collect(Collectors.toList());
	}

	public boolean isFullBind() {
		return getRefs().isEmpty();
	}

	public boolean isImmutable() {
		return clip || !(table.getValueType() instanceof Xbean);
	}

	public boolean isClip() {
		return clip;
	}

	public boolean isSnapshot() {
		return snapshot;
	}

	@Override
	public void depends(Set<Type> types) {
		if (isFullBind())
			table.getValueType().depends(types);
		else
			getVariables().forEach(var -> var.getType().depends(types));
	}
}
