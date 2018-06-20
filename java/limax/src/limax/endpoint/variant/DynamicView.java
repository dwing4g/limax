package limax.endpoint.variant;

import java.util.Collection;
import java.util.Set;

import limax.codec.CodecException;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.endpoint.View;
import limax.endpoint.ViewChangedEvent;
import limax.endpoint.ViewChangedListener;
import limax.endpoint.ViewChangedType;
import limax.endpoint.ViewContext;
import limax.endpoint.ViewVisitor;
import limax.net.SizePolicyException;

final class DynamicView extends View {

	private final DynamicViewImpl impl;

	DynamicView(int providerId, ViewDefine viewDefine, ViewContext vc) {
		super(vc);
		this.impl = new DynamicViewImpl(providerId, viewDefine);
	}

	@Override
	protected short getClassIndex() {
		return impl.classindex;
	}

	@Override
	protected void onData(final long sessionid, byte index, byte field, Octets data, Octets dataremoved)
			throws MarshalException {
		impl.onData(sessionid, index, field, data, dataremoved, new DynamicViewImpl.FireViewChanged() {
			@Override
			public void onViewChanged(String fieldname, Variant value, ViewChangedType type) {
				DynamicView.this.onViewChanged(sessionid, fieldname, value, type);
			}
		});
	}

	@Override
	public Set<String> getFieldNames() {
		return impl.getFieldNames();
	}

	final VariantView variantView = new AbstractVariantView() {
		@Override
		public void sendControl(String name, final Variant arg)
				throws InstantiationException, SizePolicyException, CodecException {
			final ViewDefine.ControlDefine define = impl.getControlDefine(name);
			new Control() {

				@Override
				public OctetsStream marshal(OctetsStream os) {
					return define.method.marshal(os, arg);
				}

				@Override
				public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
					return os;
				}

				@Override
				public byte getControlIndex() {
					return define.ctrlindex;
				}
			}.send();
		}

		@Override
		public void sendMessage(String msg)
				throws InstantiationException, ClassCastException, SizePolicyException, CodecException {
			DynamicView.this.getViewContext().sendMessage(DynamicView.this, msg);
		}

		private void _onViewChanged(VariantViewChangedListener listener, final ViewChangedEvent e) {
			listener.onViewChanged(new VariantViewChangedEvent() {
				@Override
				public VariantView getView() {
					return variantView;
				}

				@Override
				public long getSessionId() {
					return e.getSessionId();
				}

				@Override
				public String getFieldName() {
					return e.getFieldName();
				}

				@Override
				public Variant getValue() {
					Object o = e.getValue();
					return o != null ? (Variant) o : Variant.Null;
				}

				@Override
				public ViewChangedType getType() {
					return e.getType();
				}

				@Override
				public String toString() {
					return variantView + " " + e.getSessionId() + " " + e.getFieldName() + " " + e.getValue() + " "
							+ e.getType();
				}
			});
		}

		@Override
		public Runnable registerListener(final VariantViewChangedListener listener) {
			return DynamicView.this.registerListener(new ViewChangedListener() {
				@Override
				public void onViewChanged(ViewChangedEvent e) {
					_onViewChanged(listener, e);
				}
			});
		}

		@Override
		public Runnable registerListener(String fieldname, final VariantViewChangedListener listener) {
			return DynamicView.this.registerListener(fieldname, new ViewChangedListener() {
				@Override
				public void onViewChanged(ViewChangedEvent e) {
					_onViewChanged(listener, e);
				}
			});
		}

		@Override
		public String getViewName() {
			return impl.viewName;
		}

		@Override
		public void visitField(String fieldname, ViewVisitor<Variant> visitor) {
			synchronized (DynamicView.this) {
				impl.visitField(fieldname, visitor);
			}
		}

		@Override
		public String toString() {
			return "[view = " + impl.viewName + " ProviderId = " + impl.providerId + " classindex = " + impl.classindex
					+ "]";
		}

		@Override
		public int getInstanceIndex() {
			return 0;
		}

		@Override
		public boolean isTemporaryView() {
			return false;
		}

		@Override
		public VariantViewDefinition getDefinition() {
			return new VariantViewDefinition() {

				@Override
				public String getViewName() {
					return impl.viewName;
				}

				@Override
				public boolean isTemporary() {
					return false;
				}

				@Override
				public Collection<String> getVariableNames() {
					return impl.getDefineVariableNames();
				}

				@Override
				public Collection<String> getSubscribeNames() {
					return impl.getDefineSubscribeNames();
				}

				@Override
				public Collection<String> getControlNames() {
					return impl.getDefineControlNames();
				}
			};
		}

		@Override
		public View getView() {
			return DynamicView.this;
		}
	};
}
