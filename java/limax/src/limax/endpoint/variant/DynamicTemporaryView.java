package limax.endpoint.variant;

import java.util.Collection;
import java.util.Set;

import limax.codec.CodecException;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.endpoint.TemporaryView;
import limax.endpoint.View;
import limax.endpoint.ViewChangedEvent;
import limax.endpoint.ViewChangedListener;
import limax.endpoint.ViewChangedType;
import limax.endpoint.ViewContext;
import limax.endpoint.ViewVisitor;
import limax.net.SizePolicyException;

final class DynamicTemporaryView extends TemporaryView {

	private final DynamicViewImpl impl;
	private final TemporaryViewHandler handler;

	DynamicTemporaryView(int providerId, ViewDefine viewDefine, TemporaryViewHandler handler, ViewContext vc) {
		super(vc);
		this.impl = new DynamicViewImpl(providerId, viewDefine);
		this.handler = handler;
	}

	@Override
	protected void onOpen(Collection<Long> sessionids) {
		handler.onOpen(variantView, sessionids);
	}

	@Override
	protected void onClose() {
		handler.onClose(variantView);
	}

	@Override
	protected void onAttach(long sessionid) {
		handler.onAttach(variantView, sessionid);
	}

	@Override
	protected void detach(long sessionid, byte reason) {
		onDetach(sessionid, reason);
		impl.removeSession(sessionid);
	}

	@Override
	protected void onDetach(long sessionid, byte reason) {
		handler.onDetach(variantView, sessionid, reason);
	}

	@Override
	protected short getClassIndex() {
		return impl.classindex;
	}

	@Override
	protected void onData(final long sessionid, byte index, byte field, Octets data, Octets vardata)
			throws MarshalException {
		impl.onData(sessionid, index, field, data, vardata, new DynamicViewImpl.FireViewChanged() {
			@Override
			public void onViewChanged(String fieldname, Variant value, ViewChangedType type) {
				DynamicTemporaryView.this.onViewChanged(sessionid, fieldname, value, type);
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
			DynamicTemporaryView.this.getViewContext().sendMessage(DynamicTemporaryView.this, msg);
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
					return true;
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
					Object value = e.getValue();
					return value == null ? Variant.Null : (Variant) value;
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
			return DynamicTemporaryView.this.registerListener(new ViewChangedListener() {
				@Override
				public void onViewChanged(ViewChangedEvent e) {
					_onViewChanged(listener, e);
				}
			});
		}

		@Override
		public Runnable registerListener(String fieldname, final VariantViewChangedListener listener) {
			return DynamicTemporaryView.this.registerListener(fieldname, new ViewChangedListener() {
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
			synchronized (DynamicTemporaryView.this) {
				impl.visitField(fieldname, visitor);
			}
		}

		@Override
		public String toString() {
			return "[view = " + impl.viewName + " ProviderId = " + impl.providerId + " classindex = " + impl.classindex
					+ " instanceindex = " + getInstanceIndex() + "]";
		}

		@Override
		public int getInstanceIndex() {
			return DynamicTemporaryView.this.getInstanceIndex();
		}

		@Override
		public boolean isTemporaryView() {
			return true;
		}

		@Override
		public View getView() {
			return DynamicTemporaryView.this;
		}
	};

}
