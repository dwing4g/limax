#include "endpointinc.h"
#include "xmlgeninc/xmlgen.h"
#include "variantdef.h"
#include "viewcontextimpl.h"
#include "erroroccured.h"
#include "variantviews.h"
#include "viewdefineparser.h"

namespace limax {
	namespace helper {

		void AbstractViewContext::sendMessage(View* view, const std::string& msg)
		{
			limax::endpoint::providerendpoint::SendControlToServer protocol;
			protocol.providerid = getProviderId();
			protocol.classindex = view->getClassIndex();
			if (TemporaryView* tv = dynamic_cast<TemporaryView*>(view))
				protocol.instanceindex = tv->getInstanceIndex();
			else
				protocol.instanceindex = 0;
			protocol.controlindex = -1;
			protocol.stringdata = msg;
			protocol.send(manager->getTransport());
		}

		static int64_t getTemporaryViewKey(int16_t classindex, int32_t instanceindex)
		{
			return (((int64_t)classindex & 0x000000000000FFFFL) << 32) | (instanceindex & 0x00000000FFFFFFFFL);
		}

		View* ViewContextImpl::getSessionOrGlobalView(int16_t classIndex)
		{
			std::lock_guard<std::mutex> l(mutex);
			{
				auto it = viewmap.find(classIndex);
				if (it != viewmap.end())
					return it->second.get();
			}

			{
				auto creator = creatorManager->getCreator(classIndex);
				if (!creator)
					return nullptr;
				auto vc = viewcontext->getInstance();
				if (!vc)
					return nullptr;
				auto view = creator->createView(vc);
				viewmap.insert(std::make_pair(classIndex, std::shared_ptr<View>(view)));
				return view;
			}
		}

		TemporaryView* ViewContextImpl::findTemporaryView(int16_t classIndex, int32_t instanceIndex)
		{
			std::lock_guard<std::mutex> l(mutex);
			auto it = tempviewmap.find(getTemporaryViewKey(classIndex, instanceIndex));
			return it == tempviewmap.end() ? nullptr : it->second.get();
		}

		TemporaryView* ViewContextImpl::getTemporaryView(int16_t classIndex, int32_t instanceIndex)
		{
			int64_t key = getTemporaryViewKey(classIndex, instanceIndex);
			std::lock_guard<std::mutex> l(mutex);
			auto it = tempviewmap.find(key);
			if (it != tempviewmap.end())
				return it->second.get();
			auto creator = creatorManager->getCreator(classIndex);
			if (!creator)
				return nullptr;
			auto vc = viewcontext->getInstance();
			if (!vc)
				return nullptr;
			auto view = dynamic_cast<TemporaryView*>(creator->createView(vc));
			if (nullptr == view)
				return nullptr;
			viewcontext->setTemporaryViewInstanceIndex(view, instanceIndex);
			tempviewmap.insert(std::make_pair(key, std::shared_ptr<TemporaryView>(view)));
			return view;
		}

		std::shared_ptr<TemporaryView> ViewContextImpl::closeTemporaryView(int16_t classIndex, int32_t instanceIndex)
		{
			std::lock_guard<std::mutex> l(mutex);
			auto it = tempviewmap.find(getTemporaryViewKey(classIndex, instanceIndex));
			if (it == tempviewmap.end())
				return nullptr;
			auto view = it->second;
			tempviewmap.erase(it);
			return view;
		}

		void ViewContextImpl::fireErrorOccured(int code, limax::endpoint::providerendpoint::SyncViewToClients* protocol, const std::string& info)
		{
			std::stringstream ss;
			ss << "providerId = " << viewcontext->getProviderId() << " classIndex = " << protocol->classindex << " instanceIndex = " << protocol->instanceindex << " syncType = " << (int)protocol->synctype << " " << info;
			limax::erroroccured::fireErrorOccured(viewcontext->getEndpointManager(), SOURCE_ENDPOINT, code, ss.str());
		}

		void ViewContextImpl::onSyncViewToClients(limax::endpoint::providerendpoint::SyncViewToClients* protocol)
		{
			switch (protocol->synctype)
			{
			case limax::endpoint::providerendpoint::SyncViewToClients::DT_VIEW_DATA:
			{
				if (View* view = getSessionOrGlobalView(protocol->classindex))
				{
					std::lock_guard<std::recursive_mutex> l(view->mutex);
					for (const auto& data : protocol->vardatas)
						if (!viewcontext->fireViewOnData(view, viewcontext->getEndpointManager()->getSessionId(), data.index, data.field, data.data, data.dataremoved))
						{
							std::stringstream ss;
							ss << "varindex  = " << data.index << " fieldindex = " << data.field;
							fireErrorOccured(SYSTEM_VIEW_LOST_FIELD, protocol, ss.str());
							return;
						}
					return;
				}
				break;
			}
			case limax::endpoint::providerendpoint::SyncViewToClients::DT_TEMPORARY_INIT_DATA:
			{
				if (TemporaryView* view = getTemporaryView(protocol->classindex, protocol->instanceindex))
				{
					std::lock_guard<std::recursive_mutex> l(view->mutex);
					{
						hashset<int64_t> ms;
						for (const auto& m : protocol->members)
							ms.insert(m.sessionid);
						std::vector<int64_t> ml;
						ml.reserve(ms.size());
						ml.insert(ml.end(), ms.begin(), ms.end());
						viewcontext->fireViewOnOpen(view, ml);
					}
					for (const auto& data : protocol->vardatas)
						if (!viewcontext->fireViewOnData(view, viewcontext->getEndpointManager()->getSessionId(), data.index, data.field, data.data, data.dataremoved))
						{
							std::stringstream ss;
							ss << "varindex  = " << data.index << " fieldindex = " << data.field;
							fireErrorOccured(SYSTEM_VIEW_LOST_FIELD, protocol, ss.str());
							return;
						}
					for (const auto& data : protocol->members)
						if (data.vardata.index >= 0)
							if (!viewcontext->fireViewOnData(view, data.sessionid, data.vardata.index, data.vardata.field, data.vardata.data, data.vardata.dataremoved))
							{
								std::stringstream ss;
								ss << "varindex  = " << data.vardata.index << " fieldindex = " << data.vardata.field;
								fireErrorOccured(SYSTEM_VIEW_LOST_FIELD, protocol, ss.str());
								return;
							}
					return;
				}
				break;
			}
			case limax::endpoint::providerendpoint::SyncViewToClients::DT_TEMPORARY_DATA:
			{
				if (TemporaryView* view = findTemporaryView(protocol->classindex, protocol->instanceindex))
				{
					std::lock_guard<std::recursive_mutex> l(view->mutex);
					for (const auto& data : protocol->vardatas)
						if (!viewcontext->fireViewOnData(view, viewcontext->getEndpointManager()->getSessionId(), data.index, data.field, data.data, data.dataremoved))
						{
							std::stringstream ss;
							ss << "varindex  = " << data.index << " fieldindex = " << data.field;
							fireErrorOccured(SYSTEM_VIEW_LOST_FIELD, protocol, ss.str());
							return;
						}
					for (const auto& data : protocol->members)
						if (!viewcontext->fireViewOnData(view, data.sessionid, data.vardata.index, data.vardata.field, data.vardata.data, data.vardata.dataremoved))
						{
							std::stringstream ss;
							ss << "varindex  = " << data.vardata.index << " fieldindex = " << data.vardata.field;
							fireErrorOccured(SYSTEM_VIEW_LOST_FIELD, protocol, ss.str());
							return;
						}
					return;
				}
				break;
			}
			case limax::endpoint::providerendpoint::SyncViewToClients::DT_TEMPORARY_ATTACH:
			{
				auto memcount = protocol->members.size();
				if (1 > memcount)
				{
					std::stringstream ss;
					ss << "members.size  = " << memcount;
					fireErrorOccured(SYSTEM_VIEW_BAD_PROTOCOL_DATA, protocol, ss.str());
					return;
				}
				const auto& e = protocol->members.front();
				if (TemporaryView* view = findTemporaryView(protocol->classindex, protocol->instanceindex))
				{
					std::lock_guard<std::recursive_mutex> l(view->mutex);
					viewcontext->fireViewOnAttach(view, e.sessionid);
					for (const auto& data : protocol->members)
						if (data.vardata.index >= 0)
							if (!viewcontext->fireViewOnData(view, data.sessionid, data.vardata.index, data.vardata.field, data.vardata.data, data.vardata.dataremoved))
							{
								std::stringstream ss;
								ss << "varindex  = " << data.vardata.index << " fieldindex = " << data.vardata.field;
								fireErrorOccured(SYSTEM_VIEW_LOST_FIELD, protocol, ss.str());
								return;
							}
					return;
				}
				break;
			}
			case limax::endpoint::providerendpoint::SyncViewToClients::DT_TEMPORARY_DETACH:
			{
				auto memcount = protocol->members.size();
				if (1 != memcount)
				{
					std::stringstream ss;
					ss << "members.size  = " << memcount;
					fireErrorOccured(SYSTEM_VIEW_BAD_PROTOCOL_DATA, protocol, ss.str());
					return;
				}
				const auto& e = protocol->members.front();
				if (TemporaryView* view = findTemporaryView(protocol->classindex, protocol->instanceindex))
				{
					std::lock_guard<std::recursive_mutex> l(view->mutex);
					viewcontext->fireViewDetach(view, e.sessionid, e.vardata.index);
					return;
				}
				break;
			}
			case limax::endpoint::providerendpoint::SyncViewToClients::DT_TEMPORARY_CLOSE:
			{
				if (auto view = closeTemporaryView(protocol->classindex, protocol->instanceindex))
				{
					std::lock_guard<std::recursive_mutex> l(view->mutex);
					viewcontext->fireViewOnClose(view.get());
					return;
				}
				break;
			}
			default:
				break;
			}
			fireErrorOccured(SYSTEM_VIEW_LOST_INSTANCE, protocol, "");
		}

		void ViewContextImpl::clear()
		{
			std::lock_guard<std::mutex> l(mutex);
			for (auto& i : viewmap)
				viewcontext->fireViewDoClose(i.second.get());
			for (auto& i : tempviewmap)
				viewcontext->fireViewDoClose(i.second.get());
			viewmap.clear();
			tempviewmap.clear();
		}

	} // namespace helper { 

	namespace helper {
		namespace variant {
			class DynamicViewCreator : public View::ViewCreator
			{
				VariantViewContextImpl* impl;
				std::shared_ptr<limax::ViewDefine> define;
			public:
				DynamicViewCreator(VariantViewContextImpl* _impl, std::shared_ptr<limax::ViewDefine> vd)
					: impl(_impl), define(vd)
				{}
				virtual ~DynamicViewCreator() {}
			public:
				virtual View* createView(std::shared_ptr<ViewContext> vc) override
				{
					return impl->createView(vc, define);
				}
			};

		} // namespace variant {

		VariantViewContextImpl::VariantViewContextImpl(EndpointManager* mng, int32_t pvid, const std::vector< std::shared_ptr<limax::ViewDefine> >& vds)
			: AbstractViewContext(mng, pvid), creatorManager(new View::ViewCreatorManager(pvid)), impl(creatorManager)
		{
			impl.setAbstractViewContext(this);
			for (const auto& define : vds)
			{
				std::shared_ptr<View::ViewCreator> creator = std::shared_ptr<View::ViewCreator>(new variant::DynamicViewCreator(this, define));
				defines.insert(std::make_pair(define->getViewName(), define));
				if (define->isTemporary())
					tempviewname.insert(define->getViewName());
				creatorManager->addCreator(define->getClassIndex(), creator);
			}
			variantManager = std::shared_ptr<limax::VariantManager>(new VariantManager(this));
		}

		VariantViewContextImpl::~VariantViewContextImpl()
		{
		}

		View* VariantViewContextImpl::createView(std::shared_ptr<ViewContext> vc, std::shared_ptr<limax::ViewDefine> define)
		{
			if (define->isTemporary())
				return new limax::helper::DynamicTemporaryView(vc, vc->getProviderId(), define, getTemporaryViewHandlerPtr(define->getViewName()));
			else
				return new limax::helper::DynamicView(vc, vc->getProviderId(), define);
		}

		bool VariantViewContextImpl::setTemporaryViewHandler(const std::string& name, TemporaryViewHandler* handler)
		{
			if (tempviewname.find(name) == tempviewname.end())
				return false;
			handles[name] = std::shared_ptr<TemporaryViewHandler>(handler, [](TemporaryViewHandler* h){ h->destroy(); });
			return true;
		}

		std::shared_ptr<TemporaryViewHandler> VariantViewContextImpl::getTemporaryViewHandlerPtr(const std::string& name)
		{
			auto it = handles.find(name);
			if (it == handles.end())
				return nullptr;
			return it->second;
		}

		TemporaryViewHandler* VariantViewContextImpl::getTemporaryViewHandler(const std::string& name)
		{
			auto it = handles.find(name);
			if (it == handles.end())
				return nullptr;
			return it->second.get();
		}

		VariantView* VariantViewContextImpl::getSessionOrGlobalViewInstance(const std::string& name)
		{
			auto it = defines.find(name);
			if (it == defines.end())
				return nullptr;
			auto clsid = it->second->getClassIndex();
			auto v = impl.getSessionOrGlobalView(clsid);
			if (!v)
				return nullptr;
			if (auto dv = dynamic_cast<limax::helper::DynamicView*>(v))
				return dv->getVariantView();
			return nullptr;
		}

		VariantView* VariantViewContextImpl::findTemporaryViewInstance(const std::string& name, int instanceindex)
		{
			auto it = defines.find(name);
			if (it == defines.end())
				return nullptr;
			auto clsid = it->second->getClassIndex();
			auto v = impl.findTemporaryView(clsid, instanceindex);
			if (!v)
				return nullptr;
			if (auto dv = dynamic_cast<limax::helper::DynamicTemporaryView*>(v))
				return dv->getVariantView();
			return nullptr;
		}

		const std::vector<std::string> VariantViewContextImpl::getSessionOrGlobalViewNames()
		{
			std::vector<std::string> names;
			names.reserve(defines.size() - tempviewname.size());

			for (const auto& vd : defines)
				if (!vd.second->isTemporary())
					names.push_back(vd.second->getViewName());
			return names;
		}

		const std::vector<std::string> VariantViewContextImpl::getTemporaryViewNames()
		{
			std::vector<std::string> names;
			names.reserve(tempviewname.size());
			names.insert(names.end(), tempviewname.begin(), tempviewname.end());
			return names;
		}

		void VariantViewContextImpl::sendVariantViewMessage(VariantView* view, const std::string& msg)
		{
			if (auto vvimpl = dynamic_cast<limax::helper::VariantViewImpl*>(view))
				AbstractViewContext::sendMessage(vvimpl->getView(), msg);
		}

		std::shared_ptr<VariantViewContextImpl> VariantViewContextImpl::create(EndpointManager* l, int32_t pvid, const limax::defines::VariantDefines& vds)
		{
			std::vector<std::shared_ptr<limax::ViewDefine>> views;
			try
			{
				limax::variant::viewdefineparser::parseVariantDefines(vds, views);
			}
			catch (limax::variant::viewdefineparser::DataException& e)
			{
				limax::erroroccured::fireErrorOccured(l, SOURCE_ENDPOINT, SYSTEM_PARSE_VARIANT_DEFINES_EXCEPTION, e.getMessage());
				return nullptr;
			}
			auto impl = std::shared_ptr<VariantViewContextImpl>(new VariantViewContextImpl(l, pvid, views));
			impl->setInstance(impl);
			return impl;
		}

	} // namespace helper {
} // namespace limax {
