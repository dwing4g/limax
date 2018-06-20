package limax.endpoint.variant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;

public final class StructDeclarationCreator {

	private static class FieldImpl implements StructDeclaration.Field {

		private final String name;
		private final MarshalMethod mm;

		public FieldImpl(String name, Declaration decl) {
			this.name = name;
			this.mm = decl.createMarshalMethod();
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public Declaration getDeclaration() {
			return mm.getDeclaration();
		}

		MarshalMethod getMarshalMethod() {
			return mm;
		}

	}

	private final List<FieldImpl> variables;

	public StructDeclarationCreator() {
		this.variables = new ArrayList<FieldImpl>();
	}

	public StructDeclaration create() {
		return new StructDeclaration() {

			@Override
			public VariantType getType() {
				return VariantType.Struct;
			}

			@Override
			public MarshalMethod createMarshalMethod() {
				final Declaration self = this;
				return new MarshalMethod() {
					@Override
					public OctetsStream marshal(OctetsStream os, Variant v) {
						for (FieldImpl i : variables)
							i.getMarshalMethod().marshal(os, v.getVariant(i.getName()));
						return os;
					}

					@Override
					public Variant unmarshal(OctetsStream os) throws MarshalException {
						final Variant v = Variant.createStruct();
						for (FieldImpl i : variables)
							v.structSetValue(i.getName(), i.getMarshalMethod().unmarshal(os));
						return v;
					}

					@Override
					public Declaration getDeclaration() {
						return self;
					}
				};
			}

			@Override
			public Collection<? extends Field> getFields() {
				return Collections.unmodifiableCollection(variables);
			}
		};
	}

	public StructDeclarationCreator addFieldDefinition(String fieldname, Declaration decl) {
		variables.add(new FieldImpl(fieldname, decl));
		return this;
	}
}
