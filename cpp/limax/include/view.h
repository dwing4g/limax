#pragma once

namespace limax {
	namespace helper { class AbstractViewContext; class TemporaryViewNotifySet; class ViewChangedListenerContainer; }
	struct LIMAX_DLL_EXPORT_API EndpointManager;
	class LIMAX_DLL_EXPORT_API View;
	class LIMAX_DLL_EXPORT_API TemporaryView;
	class LIMAX_DLL_EXPORT_API ViewContext;

	enum ViewChangedType
	{
		New = 0,
		Replace,
		Touch,
		Delete,
	};

	class LIMAX_DLL_EXPORT_API ViewChangedEvent
	{
	public:
		ViewChangedEvent();
		virtual ~ViewChangedEvent();
	public:
		virtual View* getView() const = 0;
		virtual int64_t getSessionId() const = 0;
		virtual const std::string& getFieldName() const = 0;
		virtual const void* getValue() const = 0;
		virtual ViewChangedType getType() const = 0;
	public:
		static std::string getViewChangedTypeName(ViewChangedType type);
		std::string toString() const;
	};

	typedef std::function<void(const ViewChangedEvent&)> ViewChangedListener;

	class LIMAX_DLL_EXPORT_API View
	{
		friend class helper::AbstractViewContext;
		friend class TemporaryView;
	public:
		std::recursive_mutex mutex;
		class LIMAX_DLL_EXPORT_API ViewCreator
		{
		public:
			ViewCreator();
			virtual ~ViewCreator();
		public:
			virtual View* createView(std::shared_ptr<ViewContext>) = 0;
		};

		template<class ViewType> class ViewCreatorTemplate : public ViewCreator
		{
		public:
			ViewCreatorTemplate() {}
			virtual ~ViewCreatorTemplate() {}
		public:
			virtual View* createView(std::shared_ptr<ViewContext> vc)
			{
				return new ViewType(vc);
			}
		};

		class LIMAX_DLL_EXPORT_API ViewCreatorManager
		{
			struct Data;
			Data* data;
		public:
			ViewCreatorManager(int32_t);
			~ViewCreatorManager();
		public:
			void addCreator(int16_t classIndex, std::shared_ptr<ViewCreator> creator);
			std::shared_ptr<ViewCreator> getCreator(int16_t classIndex) const;
			int32_t getProviderId() const;
			void staticCreateAll(std::shared_ptr<ViewContext> ctx) const;
		};

		class LIMAX_DLL_EXPORT_API Control : public limax::Marshal
		{
		public:
			Control();
			virtual ~Control();
		protected:
			virtual int8_t getControlIndex() const = 0;
		public:
			void send(const View*);
		};

	public:
		View(std::shared_ptr<ViewContext>);
		virtual ~View();
	private:
		std::shared_ptr<helper::AbstractViewContext> viewContext;
		std::shared_ptr<helper::ViewChangedListenerContainer> m_listeners;
	protected:
		virtual int16_t getClassIndex() const = 0;
	public:
		Runnable registerListener(ViewChangedListener);
		Runnable registerListener(const std::string&, ViewChangedListener);
		ViewContext* getViewContext() const;
		void sendMessage(const std::string& msg);
	protected:
		void fireViewChange(View* instance, int64_t sessionid, const std::string& varname, const void* value, ViewChangedType type);
	protected:
		virtual bool onData(View* instance, int64_t sessionid, int8_t nameIndex, int8_t fieldIndex, const Octets& data, const Octets& dataremoved) = 0;
	public:
		virtual const hashset<std::string>& getFieldNames() const = 0;
		virtual std::string getClassName() const;
		virtual std::string toString() const;
	private:
		void doClose();
	};

	class LIMAX_DLL_EXPORT_API TemporaryView : public View
	{
		friend class helper::AbstractViewContext;
	private:
		int32_t instanceIndex;
	public:
		TemporaryView(std::shared_ptr<ViewContext>);
		virtual ~TemporaryView();
	public:
		int32_t getInstanceIndex() const;
		std::string toString() const override;
	protected:
		virtual void onOpen(const std::vector<int64_t>& sessionids) = 0;
		virtual void onClose() = 0;
		virtual void onAttach(int64_t sessionid) = 0;
		virtual void onDetach(int64_t sessionid, int reason) = 0;
		virtual void detach(int64_t sessionid, int reason) = 0;
	private:
		void doClose();
	};

	class LIMAX_DLL_EXPORT_API ViewContext
	{
	public:
		enum Type
		{
			Static, Variant, Script
		};
	public:
		ViewContext();
		virtual ~ViewContext();
	public:
		virtual View* getSessionOrGlobalView(int16_t classIndex) = 0;
		virtual TemporaryView* findTemporaryView(int16_t classIndex, int32_t instanceIndex) = 0;
		virtual EndpointManager* getEndpointManager() const = 0;
		virtual int32_t getProviderId() const = 0;
		virtual Type getType() const = 0;
		virtual void sendMessage(View*, const std::string&) = 0;
	};
} // namespace limax {
