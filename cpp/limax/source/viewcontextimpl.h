#pragma once

namespace limax {
	namespace helper {

		class AbstractViewContext : public ViewContext
		{
			EndpointManager* manager;
			int32_t		providerId;
			std::weak_ptr<AbstractViewContext> instance;
		public:
			AbstractViewContext(EndpointManager* mng, int32_t pvid)
				: manager(mng), providerId(pvid)
			{}
			virtual ~AbstractViewContext() {}
		public:
			virtual void onSyncViewToClients(limax::endpoint::providerendpoint::SyncViewToClients* protocol) = 0;
			virtual void clear() = 0;
		public:
			virtual int32_t getProviderId() const override
			{
				return providerId;
			}
			virtual EndpointManager* getEndpointManager() const override
			{
				return manager;
			}
		public:
			std::shared_ptr<AbstractViewContext> getInstance()
			{
				return instance.lock();
			}
		public:
			void sendMessage(View*, const std::string&) override;
			void setInstance(std::shared_ptr<AbstractViewContext> _instance)
			{
				instance = _instance;
			}
			bool fireViewOnData(View* view, int64_t sessionid, int8_t nameIndex, int8_t fieldIndex, const limax::Octets& data, const Octets& dataremoved)
			{
				return view->onData(view, sessionid, nameIndex, fieldIndex, data, dataremoved);
			}
			void fireViewOnOpen(TemporaryView* view, const std::vector<int64_t>& sessionids)
			{
				view->onOpen(sessionids);
			}
			void fireViewOnClose(TemporaryView* view)
			{
				view->onClose();
			}
			void fireViewOnAttach(TemporaryView* view, int64_t sessionid)
			{
				view->onAttach(sessionid);
			}
			void fireViewDetach(TemporaryView* view, int64_t sessionid, int reason)
			{
				view->detach(sessionid, reason);
			}
			void fireViewDoClose(View* view)
			{
				view->doClose();
			}
			void fireViewDoClose(TemporaryView* view)
			{
				view->doClose();
			}
			void setTemporaryViewInstanceIndex(TemporaryView* view, int instance)
			{
				view->instanceIndex = instance;
			}
		};

		class ViewContextImpl
		{
			std::shared_ptr<const View::ViewCreatorManager> creatorManager;
			AbstractViewContext* viewcontext;
			hashmap<int16_t, std::shared_ptr<View> > viewmap;
			hashmap<int64_t, std::shared_ptr<TemporaryView>> tempviewmap;
			std::mutex mutex;
		public:
			ViewContextImpl(std::shared_ptr<const View::ViewCreatorManager> cm)
				: creatorManager(cm)
			{}
			virtual ~ViewContextImpl() {}
		public:
			void setAbstractViewContext(AbstractViewContext * vc)
			{
				viewcontext = vc;
			}
		public:
			View* getSessionOrGlobalView(int16_t classIndex);
			TemporaryView* findTemporaryView(int16_t classIndex, int32_t instanceIndex);
			void onSyncViewToClients(limax::endpoint::providerendpoint::SyncViewToClients* protocol);
			void clear();
		private:
			TemporaryView* getTemporaryView(int16_t classIndex, int32_t instanceIndex);
			std::shared_ptr<TemporaryView> closeTemporaryView(int16_t classIndex, int32_t instanceIndex);
			void fireErrorOccured(int code, limax::endpoint::providerendpoint::SyncViewToClients* protocol, const std::string& info);
		};

		class StaticViewContextImpl : public AbstractViewContext
		{
			ViewContextImpl impl;
		private:
			StaticViewContextImpl(EndpointManager* mng, int32_t pvid, std::shared_ptr<const View::ViewCreatorManager> cm)
				: AbstractViewContext(mng, pvid), impl(cm)
			{
				impl.setAbstractViewContext(this);
			}
		public:
			virtual ~StaticViewContextImpl() {}
		public:
			virtual View* getSessionOrGlobalView(int16_t classIndex) override
			{
				return impl.getSessionOrGlobalView(classIndex);
			}
			virtual TemporaryView* findTemporaryView(int16_t classIndex, int32_t instanceIndex) override
			{
				return impl.findTemporaryView(classIndex, instanceIndex);
			}
			virtual void onSyncViewToClients(limax::endpoint::providerendpoint::SyncViewToClients* protocol) override
			{
				impl.onSyncViewToClients(protocol);
			}
			virtual void clear() override
			{
				impl.clear();
			}
			virtual Type getType() const override
			{
				return Type::Static;
			}
		private:
			std::shared_ptr<TemporaryView> getTemporaryView(int16_t classIndex, int32_t instanceIndex);
			void fireErrorOccured(int code, limax::endpoint::providerendpoint::SyncViewToClients* protocol, const std::string& info);
		public:
			static std::shared_ptr<AbstractViewContext> create(EndpointManager* mng, int32_t pvid, std::shared_ptr<const View::ViewCreatorManager> vcm)
			{
				auto ins = std::shared_ptr<AbstractViewContext>(new StaticViewContextImpl(mng, pvid, vcm));
				ins->setInstance(ins);
				vcm->staticCreateAll(ins);
				return ins;
			}
		};

		namespace variant { class Impl; }
		class VariantViewContextImpl : public AbstractViewContext
		{
			hashmap<std::string, std::shared_ptr<limax::TemporaryViewHandler>> handles;
			hashmap<std::string, std::shared_ptr<limax::ViewDefine>> defines;
			hashset<std::string> tempviewname;
			std::shared_ptr<limax::View::ViewCreatorManager> creatorManager;
			std::shared_ptr<limax::VariantManager> variantManager;
			ViewContextImpl impl;
		private:
			VariantViewContextImpl(EndpointManager*, int32_t, const std::vector< std::shared_ptr<limax::ViewDefine> >& vds);
		public:
			virtual ~VariantViewContextImpl();
		public:
			virtual View* getSessionOrGlobalView(int16_t classIndex) override
			{
				return impl.getSessionOrGlobalView(classIndex);
			}
			virtual TemporaryView* findTemporaryView(int16_t classIndex, int32_t instanceIndex) override
			{
				return impl.findTemporaryView(classIndex, instanceIndex);
			}
			virtual void onSyncViewToClients(limax::endpoint::providerendpoint::SyncViewToClients* protocol) override
			{
				impl.onSyncViewToClients(protocol);
			}
			virtual void clear() override
			{
				impl.clear();
			}
			virtual Type getType() const override
			{
				return Type::Variant;
			}
		private:
			std::shared_ptr<TemporaryViewHandler> getTemporaryViewHandlerPtr(const std::string& name);
		public:
			bool setTemporaryViewHandler(const std::string& name, TemporaryViewHandler* handler);
			TemporaryViewHandler* getTemporaryViewHandler(const std::string& name);
			VariantView* getSessionOrGlobalViewInstance(const std::string& name);
			VariantView* findTemporaryViewInstance(const std::string& name, int instanceindex);
			View* createView(std::shared_ptr<limax::ViewContext> vc, std::shared_ptr<limax::ViewDefine> define);
			const std::vector<std::string> getSessionOrGlobalViewNames();
			const std::vector<std::string> getTemporaryViewNames();
			void sendVariantViewMessage(VariantView*, const std::string&);
			VariantManager* getVariantManager()	{ return variantManager.get(); }
		public:
			static std::shared_ptr<VariantViewContextImpl> create(EndpointManager*, int32_t pvid, const limax::defines::VariantDefines&);
		};

	} // namespace helper { 
} // namespace limax {
