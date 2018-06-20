#pragma once

namespace limax {

	class LIMAX_DLL_EXPORT_API MarshalMethod;
	class LIMAX_DLL_EXPORT_API Variant;
	class LIMAX_DLL_EXPORT_API VariantView;
	class LIMAX_DLL_EXPORT_API VariantManager;

	enum VariantType
	{
		Null,
		Boolean,
		Byte,
		Short,
		Int,
		Long,
		Float,
		Double,
		String,
		Binary,
		List,
		Vector,
		Set,
		Map,
		Struct,
		Object
	};

	class LIMAX_DLL_EXPORT_API Declaration
	{
	public:
		Declaration();
		virtual ~Declaration();
	public:
		virtual VariantType getType() const = 0;
		virtual std::shared_ptr<MarshalMethod> createMarshalMethod() const = 0;
	public:
		static std::shared_ptr<Declaration> create(VariantType);
		static std::shared_ptr<Declaration> create(VariantType, std::shared_ptr<Declaration>);
		static std::shared_ptr<Declaration> create(VariantType, std::shared_ptr<Declaration>, std::shared_ptr<Declaration>);
	};

	class LIMAX_DLL_EXPORT_API MarshalMethod
	{
	public:
		MarshalMethod();
		virtual ~MarshalMethod();
	public:
		virtual MarshalStream& marshal(MarshalStream&, const Variant&) = 0;
		virtual Variant unmarshal(const UnmarshalStream&) = 0;
		virtual std::shared_ptr<Declaration> getDeclaration() const = 0;
	};

	class LIMAX_DLL_EXPORT_API CollectionDeclaration : public Declaration
	{
	public:
		CollectionDeclaration();
		virtual ~CollectionDeclaration();
	public:
		virtual std::shared_ptr<Declaration> getValue() const = 0;
	};

	class LIMAX_DLL_EXPORT_API MapDeclaration : public CollectionDeclaration
	{
	public:
		MapDeclaration();
		virtual ~MapDeclaration();
	public:
		virtual std::shared_ptr<Declaration> getKey() const = 0;
	};

	class LIMAX_DLL_EXPORT_API StructDeclaration : public Declaration
	{
	public:
		class LIMAX_DLL_EXPORT_API Variable
		{
		public:
			Variable();
			virtual ~Variable();
		public:
			virtual const std::string& getName() const = 0;
			virtual std::shared_ptr<Declaration> getDeclaration() const = 0;
		};
	public:
		StructDeclaration();
		virtual ~StructDeclaration();
	public:
		virtual const std::vector< std::shared_ptr<const Variable> > getVariables() const = 0;
	};

	class LIMAX_DLL_EXPORT_API StructDeclarationCreator
	{
		struct Data;
		Data* data;
	public:
		StructDeclarationCreator();
		~StructDeclarationCreator();
	public:
		StructDeclarationCreator& insertVariable(const std::string&, std::shared_ptr<Declaration>);
	public:
		std::shared_ptr<StructDeclaration> create() const;
	};

	namespace variantdata { struct Data; }
	namespace helper { class VariantViewChangedListenerImpl; }

	class LIMAX_DLL_EXPORT_API Variant
	{
		std::shared_ptr<variantdata::Data> data;
	private:
		Variant(variantdata::Data*);
	public:
		static Variant Null;
		static Variant True;
		static Variant False;
	public:
		Variant();
		Variant(const Variant&);
		~Variant();

		Variant& operator=(const Variant&);
		bool operator==(const Variant&) const;
		bool operator!=(const Variant&) const;
		bool operator<(const Variant&) const;

		VariantType getVariantType() const;
		std::shared_ptr<Declaration> makeDeclaration() const;
		bool equals(const Variant&) const;
		int compare(const Variant&) const;
		std::string toString() const;
		size_t hash_code() const;
	public:
		static Variant create(bool);
		static Variant create(int8_t);
		static Variant create(int16_t);
		static Variant create(int32_t);
		static Variant create(int64_t);
		static Variant create(float);
		static Variant create(double);
		static Variant create(const Octets&);
		static Variant create(const char*);
		static Variant create(const std::string&);
		static Variant createList();
		static Variant createVector();
		static Variant createSet();
		static Variant createMap();
		static Variant createStruct();
	public:
		bool getBooleanValue() const;
		int8_t getByteValue() const;
		int16_t getShortValue() const;
		int32_t getIntValue() const;
		int64_t getLongValue() const;
		float getFloatValue() const;
		double getDoubleValue() const;
		const std::string& getStringValue() const;
		const Octets& getOctetsValue() const;
		const std::list<Variant>& getListValue() const;
		const std::vector<Variant>& getVectorValue() const;
		const hashset<Variant>& getSetValue() const;
		const hashmap<Variant, Variant>& getMapValue() const;

		std::list<Variant>& getRawListValue();
		std::vector<Variant>& getRawVectorValue();
		hashset<Variant>& getRawSetValue();
		hashmap<Variant, Variant>& getRawMapValue();

		void collectionInsert(const Variant&);
		template<class T> void collectionInsert(T v)
		{
			collectionInsert(create(v));
		}
		Variant mapInsert(const Variant&, const Variant&);
		template<class K, class V> Variant mapInsert(K k, V v)
		{
			return mapInsert(create(k), create(v));
		}
		template<class K> Variant mapInsert(K k, const Variant& v)
		{
			return mapInsert(create(k), v);
		}
		template<class V> Variant mapInsert(const Variant& k, V v)
		{
			return mapInsert(k, create(v));
		}
	public:
		bool getBoolean(const std::string& name) const;
		int8_t getByte(const std::string& name) const;
		int16_t getShort(const std::string& name) const;
		int32_t getInt(const std::string& name) const;
		int64_t getLong(const std::string& name) const;
		float getFloat(const std::string& name) const;
		double getDouble(const std::string& name) const;
		const std::string& getString(const std::string& name) const;
		const Octets& getOctets(const std::string& name) const;
		const std::list<Variant>& getList(const std::string& name) const;
		const std::vector<Variant>& getVector(const std::string& name) const;
		const hashset<Variant>& getSet(const std::string& name) const;
		const hashmap<Variant, Variant>& getMap(const std::string& name) const;
		Variant getVariant(const std::string& name) const;

		std::list<Variant>& getRawList(const std::string& name);
		std::vector<Variant>& getRawVector(const std::string& name);
		hashset<Variant>& getRawSet(const std::string& name);
		hashmap<Variant, Variant>& getRawMap(const std::string& name);

		void setValue(const std::string&, const Variant&);
		template<class T> void setValue(const std::string& name, T v)
		{
			setValue(name, create(v));
		}
	};

	class LIMAX_DLL_EXPORT_API VariantViewChangedEvent
	{
	public:
		VariantViewChangedEvent();
		virtual ~VariantViewChangedEvent();
	public:
		virtual VariantView* getView() const = 0;
		virtual int64_t getSessionId() const = 0;
		virtual const std::string& getFieldName() const = 0;
		virtual Variant getValue() const = 0;
		virtual ViewChangedType getType() const = 0;
		std::string toString() const;
	};

	typedef std::function<void(const VariantViewChangedEvent&)> VariantViewChangedListener;

	class LIMAX_DLL_EXPORT_API VariantView
	{
	public:
		VariantView();
		virtual ~VariantView();
	public:
		class LIMAX_DLL_EXPORT_API Define
		{
		public:
			Define();
			virtual ~Define();
		public:
			virtual const std::string& getViewName() const = 0;
			virtual bool isTemporary() const = 0;
			virtual const std::vector<std::string>& getFieldNames() const = 0;
			virtual const std::vector<std::string>& getSubscribeNames() const = 0;
			virtual const std::vector<std::string>& getControlNames() const = 0;
		};
	public:
		virtual const std::string& getViewName() const = 0;
		virtual void visitField(const std::string& fieldname, std::function<void(const Variant&)> visitor) const = 0;
		virtual void sendControl(const std::string& controlname, Variant arg) const = 0;
		virtual void sendMessage(const std::string& msg) const = 0;
		virtual Runnable registerListener(VariantViewChangedListener) = 0;
		virtual Runnable registerListener(const std::string& varname, VariantViewChangedListener) = 0;
		virtual int32_t getInstanceIndex() const = 0;
		virtual bool isTemporaryView() const = 0;
		virtual std::string toString() const = 0;
		virtual const Define& getDefine() const = 0;
	};

	namespace helper { class VariantViewContextImpl; }

	class LIMAX_DLL_EXPORT_API TemporaryViewHandler
	{
	public:
		TemporaryViewHandler();
		virtual ~TemporaryViewHandler();
	public:
		virtual void onOpen(VariantView* view, const std::vector<int64_t>& sessionids) = 0;
		virtual void onClose(VariantView* view) = 0;
		virtual void onAttach(VariantView* view, int64_t sessionid) = 0;
		virtual void onDetach(VariantView* view, int64_t sessionid, int reason) = 0;

		virtual void destroy() = 0;
	};

	class LIMAX_DLL_EXPORT_API VariantManager
	{
		friend class limax::helper::VariantViewContextImpl;
		limax::helper::VariantViewContextImpl* impl;
	private:
		VariantManager(limax::helper::VariantViewContextImpl*);
	public:
		~VariantManager();
	public:
		const std::vector<std::string> getSessionOrGlobalViewNames() const;
		const std::vector<std::string> getTemporaryViewNames() const;
		void setTemporaryViewHandler(const std::string&, TemporaryViewHandler*);
		TemporaryViewHandler* getTemporaryViewHandler(const std::string&);
		VariantView* getSessionOrGlobalView(const std::string&);
		VariantView* findTemporaryView(const std::string&, int);
		void sendMessage(VariantView*, const std::string&);
	public:
		static VariantManager* getInstance(EndpointManager*, int32_t);
	};

	typedef std::shared_ptr<Declaration> DeclarationPtr;
	typedef std::shared_ptr<MarshalMethod> MarshalMethodPtr;

} // namespace limax {

