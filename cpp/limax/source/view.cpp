#include "endpointinc.h"
#include "xmlgeninc/xmlgen.h"
#include "erroroccured.h"
#include "variantdef.h"
#include "viewcontextimpl.h"

namespace limax {
	namespace helper {

		class ViewChangedListenerContainer
		{
			int idgenerator = 0;
			hashmap<std::string, hashmap<int, ViewChangedListener>> listeners;
			std::recursive_mutex& mutex;
			std::weak_ptr<ViewChangedListenerContainer> instance;
		public:
			ViewChangedListenerContainer(std::recursive_mutex& _mutex) : mutex(_mutex)
			{}

			void setInstance(std::shared_ptr<ViewChangedListenerContainer> ptr)
			{
				instance = ptr;
			}
			Runnable addListener(const std::string& fieldname, ViewChangedListener listener)
			{
				int id = idgenerator++;
				listeners[fieldname].insert(std::make_pair(id, listener));
				auto wptr = instance;
				return[id, fieldname, wptr]()
				{
					if (auto ptr = wptr.lock())
					{
						std::lock_guard<std::recursive_mutex> l(ptr->mutex);
						ptr->listeners[fieldname].erase(id);
					}
				};
			}
			hashmap<int, ViewChangedListener> getListeners(std::string fieldname)
			{
				auto it = listeners.find(fieldname);
				return it == listeners.end() ? hashmap<int, ViewChangedListener>() : it->second;
			}
			void clear()
			{
				listeners.clear();
			}
		};

		class ViewChangedEventImpl : public ViewChangedEvent
		{
			View* view;
			int64_t sessionid;
			std::string varname;
			const void* vaule;
			ViewChangedType type;
		public:
			ViewChangedEventImpl(View* _view, int64_t _sessionid, const std::string& _varname, const void* _vaule, ViewChangedType _type)
				: view(_view), sessionid(_sessionid), varname(_varname), vaule(_vaule), type(_type)
			{}
			virtual ~ViewChangedEventImpl() {}
		public:
			virtual View* getView() const override
			{
				return view;
			}
			virtual int64_t getSessionId() const override
			{
				return sessionid;
			}
			virtual const std::string& getFieldName() const override
			{
				return varname;
			}
			virtual const void* getValue() const override
			{
				return vaule;
			}
			virtual ViewChangedType getType() const override
			{
				return type;
			}
		};

	} // namespace helper {

	View::View(std::shared_ptr<ViewContext> vc)
		: viewContext(std::dynamic_pointer_cast<helper::AbstractViewContext>(vc)), m_listeners(new helper::ViewChangedListenerContainer(mutex))
	{
		m_listeners->setInstance(m_listeners);
	}
	View::~View() {}

	ViewChangedEvent::ViewChangedEvent() {}
	ViewChangedEvent::~ViewChangedEvent() {}

	std::string ViewChangedEvent::toString() const{
		std::stringstream ss;
		ss << getView()->toString() << " " << getSessionId() << " " << getFieldName() << " " << getValue() << " " << ViewChangedEvent::getViewChangedTypeName(getType());
		return ss.str();
	}

	std::string ViewChangedEvent::getViewChangedTypeName(ViewChangedType type)
	{
		switch (type)
		{
		case New:
			return "NEW";
		case Replace:
			return "REPLACE";
		case Touch:
			return "TOUCH";
		case Delete:
			return "DELETE";
		default:
			return "UNKNOWN";
		}
	}

	View::ViewCreator::ViewCreator() {}
	View::ViewCreator::~ViewCreator() {}

	struct View::ViewCreatorManager::Data
	{
		hashmap<int16_t, std::shared_ptr<View::ViewCreator> > map;
		int32_t pvid;
	};

	View::ViewCreatorManager::ViewCreatorManager(int32_t pvid)
		: data(new Data())
	{
		data->pvid = pvid;
	}
	View::ViewCreatorManager::~ViewCreatorManager()
	{
		delete data;
	}

	void View::ViewCreatorManager::addCreator(int16_t classIndex, std::shared_ptr<View::ViewCreator> creator)
	{
		data->map.insert(std::make_pair(classIndex, creator));
	}

	std::shared_ptr<View::ViewCreator> View::ViewCreatorManager::getCreator(int16_t classIndex) const
	{
		auto it = data->map.find(classIndex);
		if (it == data->map.end())
			return nullptr;
		else
			return it->second;
	}

	void View::ViewCreatorManager::staticCreateAll(std::shared_ptr<ViewContext> ctx) const {
		for (const auto& c : data->map)
			c.second->createView(ctx);
	}

	int32_t View::ViewCreatorManager::getProviderId() const
	{
		return data->pvid;
	}

	Runnable View::registerListener(ViewChangedListener handler)
	{
		std::lock_guard<std::recursive_mutex> l(mutex);
		std::vector<Runnable> as;
		for (auto& n : getFieldNames())
			as.push_back(m_listeners->addListener(n, handler));
		return [as](){
			for (auto& a : as)
				a();
		};
	}

	Runnable View::registerListener(const std::string& varname, ViewChangedListener handler)
	{
		std::lock_guard<std::recursive_mutex> l(mutex);
		const auto& names = getFieldNames();
		if (names.find(varname) == names.end())
			return [](){};
		return m_listeners->addListener(varname, handler);
	}

	void View::fireViewChange(View* instance, int64_t sessionid, const std::string& varname, const void* value, ViewChangedType type)
	{
		helper::ViewChangedEventImpl ve(instance, sessionid, varname, value, type);
		for (auto l : m_listeners->getListeners(varname))
			l.second(ve);
	}

	ViewContext* View::getViewContext() const
	{
		return viewContext.get();
	}

	void View::sendMessage(const std::string& msg)
	{
		viewContext->sendMessage(this, msg);
	}

	void View::doClose()
	{
		std::lock_guard<std::recursive_mutex> l(mutex);
		m_listeners->clear();
	}

	std::string View::getClassName() const
	{
		return "";
	}

	std::string View::toString() const
	{
		std::stringstream ss;
		ss << "[class = " << getClassName() << " ProviderId = " << viewContext->getProviderId() << " classindex = " << getClassIndex() << ']';
		return ss.str();
	}

} // namespace limax {

namespace limax {

	View::Control::Control() {}
	View::Control::~Control() {}

	void View::Control::send(const View* view)
	{
		auto endpoint = view->getViewContext()->getEndpointManager();
		limax::OctetsMarshalStream	oms;
		marshal(oms);
		limax::endpoint::providerendpoint::SendControlToServer  protocol;
		protocol.providerid = view->getViewContext()->getProviderId();
		protocol.classindex = view->getClassIndex();
		if (const TemporaryView* tv = dynamic_cast<const TemporaryView*>(view))
			protocol.instanceindex = tv->getInstanceIndex();
		else
			protocol.instanceindex = 0;
		protocol.controlindex = getControlIndex();
		protocol.controlparameter = oms.getOctets();
		protocol.send(endpoint->getTransport());
	}

} // namespace limax {

namespace limax {

	TemporaryView::TemporaryView(std::shared_ptr<ViewContext> vc)
		: View(vc)
	{}
	TemporaryView::~TemporaryView() {}

	int32_t TemporaryView::getInstanceIndex() const
	{
		return instanceIndex;
	}

	void TemporaryView::doClose()
	{
		View::doClose();
		onClose();
	}

	std::string TemporaryView::toString() const
	{
		std::stringstream ss;
		ss << "[class = " << getClassName() << " ProviderId = " << viewContext->getProviderId() << " classindex = " << getClassIndex() << " instanceindex = " << instanceIndex << ']';
		return ss.str();
	}

} // namespace limax {

namespace limax {

	ViewContext::ViewContext() {}
	ViewContext::~ViewContext() {}

} // namespace limax {
