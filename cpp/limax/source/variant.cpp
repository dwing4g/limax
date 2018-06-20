#include "endpointinc.h"
#include "variantdef.h"

namespace limax {

	Declaration::Declaration() {}
	Declaration::~Declaration() {}

	MarshalMethod::MarshalMethod() {}
	MarshalMethod::~MarshalMethod() {}

	CollectionDeclaration::CollectionDeclaration() {}
	CollectionDeclaration::~CollectionDeclaration() {}

	MapDeclaration::MapDeclaration() {}
	MapDeclaration::~MapDeclaration() {}

	StructDeclaration::StructDeclaration() {}
	StructDeclaration::~StructDeclaration() {}

	StructDeclaration::Variable::Variable() {}
	StructDeclaration::Variable::~Variable() {}

} // namespace limax {

namespace limax {

	class DeclarationImpl
	{

		class NullMarshalMethod : public MarshalMethod
		{
		public:
			NullMarshalMethod() {}
			virtual ~NullMarshalMethod() {}
		public:
			virtual MarshalStream& marshal(MarshalStream& ms, const Variant&) override
			{
				return ms;
			}
			virtual Variant unmarshal(const UnmarshalStream&) override
			{
				return Variant::Null;
			}
			virtual std::shared_ptr<Declaration> getDeclaration() const override;
		};
		static std::shared_ptr<MarshalMethod> nullMarshalMethod;

		class NullDeclaration : public Declaration
		{
			VariantType type;
		public:
			NullDeclaration(VariantType _type)
				: type(_type)
			{}
			virtual ~NullDeclaration() {}
		public:
			virtual VariantType getType() const override
			{
				return type;
			}
			virtual std::shared_ptr<MarshalMethod> createMarshalMethod() const override
			{
				return nullMarshalMethod;
			}
		};

		class Single : public Declaration
		{
			VariantType	type;
			std::shared_ptr<MarshalMethod>(*method)();
		public:
			Single(VariantType _type, std::shared_ptr<MarshalMethod>(*_method)())
				: type(_type), method(_method)
			{}
			virtual ~Single() {}
		public:
			virtual VariantType getType() const override
			{
				return type;
			}

			virtual std::shared_ptr<MarshalMethod> createMarshalMethod() const override
			{
				return method();
			}
		};

		class Collection : public CollectionDeclaration
		{
			friend class DeclarationImpl;

			VariantType	type;
			std::shared_ptr<Declaration> value;
			std::weak_ptr<Declaration> instance;
			std::shared_ptr<MarshalMethod>(*method)(std::shared_ptr<Declaration>, std::shared_ptr<MarshalMethod>);
		public:
			Collection(VariantType _type, std::shared_ptr<Declaration> _value, std::shared_ptr<MarshalMethod>(*_method)(std::shared_ptr<Declaration>, std::shared_ptr<MarshalMethod>))
				: type(_type), value(_value), method(_method)
			{}
			virtual ~Collection() {}
		public:
			virtual VariantType getType() const override
			{
				return type;
			}
			virtual std::shared_ptr<Declaration> getValue() const override
			{
				return value;
			}

			virtual std::shared_ptr<MarshalMethod> createMarshalMethod() const override
			{
				if (auto ins = instance.lock())
					return method(ins, value->createMarshalMethod());
				else
					return nullptr;
			}
		};

		class Map : public MapDeclaration
		{
			friend class DeclarationImpl;

			std::shared_ptr<Declaration> key;
			std::shared_ptr<Declaration> value;
			std::weak_ptr<Declaration> instance;
		public:
			Map(std::shared_ptr<Declaration> _key, std::shared_ptr<Declaration> _value)
				: key(_key), value(_value)
			{}
			virtual ~Map() {}
		public:
			virtual VariantType getType() const override
			{
				return VariantType::Map;
			}
			virtual std::shared_ptr<Declaration> getKey() const override
			{
				return key;
			}
			virtual std::shared_ptr<Declaration> getValue() const override
			{
				return value;
			}

			virtual std::shared_ptr<MarshalMethod> createMarshalMethod() const override;

		};

	public:

		static std::shared_ptr<Declaration> Null;
		static std::shared_ptr<Declaration> Object;
		static std::shared_ptr<Declaration> Boolean;
		static std::shared_ptr<Declaration> Byte;
		static std::shared_ptr<Declaration> Short;
		static std::shared_ptr<Declaration> Int;
		static std::shared_ptr<Declaration> Long;
		static std::shared_ptr<Declaration> Float;
		static std::shared_ptr<Declaration> Double;
		static std::shared_ptr<Declaration> String;
		static std::shared_ptr<Declaration> Binary;

		static std::shared_ptr<Declaration> createList(std::shared_ptr<Declaration>);
		static std::shared_ptr<Declaration> createVector(std::shared_ptr<Declaration>);
		static std::shared_ptr<Declaration> createSet(std::shared_ptr<Declaration>);
		static std::shared_ptr<Declaration> createMap(std::shared_ptr<Declaration>, std::shared_ptr<Declaration>);
	};

	class MarshalMethods
	{
		template<class T> class SingleMarshalMethod : public MarshalMethod
		{
			std::shared_ptr<Declaration> m_decl;
			T(Variant::*m_getp)() const;
		public:
			SingleMarshalMethod(std::shared_ptr<Declaration> decl, T(Variant::*_getp)() const)
				: m_decl(decl), m_getp(_getp)
			{}
			virtual ~SingleMarshalMethod() {}
		public:
			virtual MarshalStream& marshal(MarshalStream& ms, const Variant& v) override
			{
				return ms.push((v.*m_getp)());
			}
			virtual Variant unmarshal(const UnmarshalStream& us) override
			{
				T v;
				us >> v;
				return Variant::create(v);
			}
			virtual std::shared_ptr<Declaration> getDeclaration() const override
			{
				return m_decl;
			}
		};

		template<class T> static std::shared_ptr<MarshalMethod> createSingleMarshalMethod(std::shared_ptr<Declaration> decl, T(Variant::*_getp)() const)
		{
			return std::shared_ptr<MarshalMethod>(new SingleMarshalMethod<T>(decl, _getp));
		}

		template<class T> class ReferenceMarshalMethod : public MarshalMethod
		{
			std::shared_ptr<Declaration> m_decl;
			const T& (Variant::*m_getp)() const;
		public:
			ReferenceMarshalMethod(std::shared_ptr<Declaration> decl, const T& (Variant::*_getp)() const)
				: m_decl(decl), m_getp(_getp)
			{}
			virtual ~ReferenceMarshalMethod() {}
		public:
			virtual MarshalStream& marshal(MarshalStream& ms, const Variant& v) override
			{
				ms << (v.*m_getp)();
				return ms;
			}
			virtual Variant unmarshal(const UnmarshalStream& us) override
			{
				T v;
				us >> v;
				return Variant::create(v);
			}
			virtual std::shared_ptr<Declaration> getDeclaration() const override
			{
				return m_decl;
			}
		};

		template<class T> static std::shared_ptr<MarshalMethod> createReferenceMarshalMethod(std::shared_ptr<Declaration> decl, const T& (Variant::*_getp)() const)
		{
			return std::shared_ptr<MarshalMethod>(new ReferenceMarshalMethod<T>(decl, _getp));
		}

		template<class CT> class CollectionMarshalMethod : public MarshalMethod
		{
			std::shared_ptr<Declaration> m_decl;
			std::shared_ptr<MarshalMethod> m_valuemm;
			const CT& (Variant::*m_getcollection)() const;
			Variant(*m_create)();
		public:
			CollectionMarshalMethod(std::shared_ptr<Declaration> decl, std::shared_ptr<MarshalMethod> valuemm, const CT& (Variant::*_getcollection)() const, Variant(*_create)())
				: m_decl(decl), m_valuemm(valuemm), m_getcollection(_getcollection), m_create(_create)
			{}
			virtual ~CollectionMarshalMethod() {}
		public:
			virtual MarshalStream& marshal(MarshalStream& ms, const Variant& v) override
			{
				const auto& container = (v.*m_getcollection)();
				ms.marshal_size((int32_t)container.size());
				for (auto i = container.begin(), e = container.end(); i != e; ++i)
				{
					const Variant& a = *i;
					m_valuemm->marshal(ms, a);
				}
				return ms;
			}
			virtual Variant unmarshal(const UnmarshalStream& us) override
			{
				Variant v = (*m_create)();
				size_t size = (size_t)us.unmarshal_size();
				for (; size > 0; --size)
				{
					Variant e = m_valuemm->unmarshal(us);
					v.collectionInsert(e);
				}
				return v;
			}
			virtual std::shared_ptr<Declaration> getDeclaration() const override
			{
				return m_decl;
			}
		};

		template<class CT> static std::shared_ptr<MarshalMethod> createCollectionMarshalMethod(std::shared_ptr<Declaration> decl, std::shared_ptr<MarshalMethod> valuemm, const CT& (Variant::*_getcollection)() const, Variant(*_create)())
		{
			return std::shared_ptr<MarshalMethod>(new CollectionMarshalMethod<CT>(decl, valuemm, _getcollection, _create));
		}

		class MapMarshalMethod : public MarshalMethod
		{
			std::shared_ptr<Declaration> m_decl;
			std::shared_ptr<MarshalMethod> m_keymm;
			std::shared_ptr<MarshalMethod> m_valuemm;
		public:
			MapMarshalMethod(std::shared_ptr<Declaration> decl, std::shared_ptr<MarshalMethod> keymm, std::shared_ptr<MarshalMethod> valuemm)
				: m_decl(decl), m_keymm(keymm), m_valuemm(valuemm)
			{}
			virtual ~MapMarshalMethod() {}
		public:
			virtual MarshalStream& marshal(MarshalStream& ms, const Variant& v) override
			{
				const auto& container = v.getMapValue();
				ms.marshal_size((int32_t)container.size());
				for (auto i = container.begin(), e = container.end(); i != e; ++i)
				{
					const Variant& k = i->first;
					const Variant& v = i->second;
					m_keymm->marshal(ms, k);
					m_valuemm->marshal(ms, v);
				}
				return ms;
			}
			virtual Variant unmarshal(const UnmarshalStream& us) override
			{
				Variant value = Variant::createMap();
				size_t size = (size_t)us.unmarshal_size();
				for (; size > 0; --size)
				{
					Variant k = m_keymm->unmarshal(us);
					Variant v = m_valuemm->unmarshal(us);
					value.mapInsert(k, v);
				}
				return value;
			}
			virtual std::shared_ptr<Declaration> getDeclaration() const override
			{
				return m_decl;
			}
		};

	public:
		static std::shared_ptr<MarshalMethod> createBooleanMethod();
		static std::shared_ptr<MarshalMethod> createInt8Method();
		static std::shared_ptr<MarshalMethod> createInt16Method();
		static std::shared_ptr<MarshalMethod> createInt32Method();
		static std::shared_ptr<MarshalMethod> createInt64Method();
		static std::shared_ptr<MarshalMethod> createFloatMethod();
		static std::shared_ptr<MarshalMethod> createDoubleMethod();
		static std::shared_ptr<MarshalMethod> createStringMethod();
		static std::shared_ptr<MarshalMethod> createOctetsMethod();
		static std::shared_ptr<MarshalMethod> createListMethod(std::shared_ptr<Declaration> list, std::shared_ptr<MarshalMethod> valueMethod);
		static std::shared_ptr<MarshalMethod> createSetMethod(std::shared_ptr<Declaration> set, std::shared_ptr<MarshalMethod> valueMethod);
		static std::shared_ptr<MarshalMethod> createVectorMethod(std::shared_ptr<Declaration> vector, std::shared_ptr<MarshalMethod> valueMethod);
		static std::shared_ptr<MarshalMethod> createMapMethod(std::shared_ptr<Declaration> map, std::shared_ptr<MarshalMethod> keyMethod, std::shared_ptr<MarshalMethod> valueMethod);
	};

	std::shared_ptr<Declaration> Declaration::create(VariantType type)
	{
		switch (type)
		{
		case VariantType::Null:
			return DeclarationImpl::Null;
		case VariantType::Boolean:
			return DeclarationImpl::Boolean;
		case VariantType::Byte:
			return DeclarationImpl::Byte;
		case VariantType::Short:
			return DeclarationImpl::Short;
		case VariantType::Int:
			return DeclarationImpl::Int;
		case VariantType::Long:
			return DeclarationImpl::Long;
		case VariantType::Float:
			return DeclarationImpl::Float;
		case VariantType::Double:
			return DeclarationImpl::Double;
		case VariantType::String:
			return DeclarationImpl::String;
		case VariantType::Binary:
			return DeclarationImpl::Binary;
		default:
			return nullptr;
		}
	}
	std::shared_ptr<Declaration> Declaration::create(VariantType type, std::shared_ptr<Declaration> value)
	{
		switch (type)
		{
		case VariantType::List:
			return DeclarationImpl::createList(value);
		case VariantType::Vector:
			return DeclarationImpl::createVector(value);
		case VariantType::Set:
			return DeclarationImpl::createSet(value);
		default:
			return nullptr;
		}
	}
	std::shared_ptr<Declaration> Declaration::create(VariantType type, std::shared_ptr<Declaration> key, std::shared_ptr<Declaration> value)
	{
		switch (type)
		{
		case VariantType::Map:
			return DeclarationImpl::createMap(key, value);
		default:
			throw nullptr;
		}
	}

	namespace helper {

		struct DeclarationVariableImpl : public StructDeclaration::Variable
		{
			DeclarationVariableImpl() {}
			virtual ~DeclarationVariableImpl() {}

			std::string						name;
			std::shared_ptr<MarshalMethod>	method;

			virtual const std::string& getName() const override
			{
				return name;
			}

			virtual std::shared_ptr<Declaration> getDeclaration() const override
			{
				return method->getDeclaration();
			}
		};

		struct StructDeclarationItemFinder
		{
			const std::string& name;
			StructDeclarationItemFinder(const std::string& _name)
				: name(_name)
			{}

			bool operator()(std::shared_ptr<DeclarationVariableImpl> value)
			{
				return limax::equals(value->name, name);
			}
		};

		class StructMarshalMethod;
		class StructDeclarationImpl : public StructDeclaration
		{
			friend class StructMarshalMethod;

			std::vector< std::shared_ptr<DeclarationVariableImpl> > items;
			std::weak_ptr<StructDeclarationImpl> instance;
		public:
			StructDeclarationImpl(const std::vector< std::shared_ptr<DeclarationVariableImpl> >& _items)
				: items(_items)
			{}
			virtual ~StructDeclarationImpl() {}
		public:
			virtual VariantType getType() const override
			{
				return VariantType::Struct;
			}
			virtual const std::vector< std::shared_ptr<const StructDeclaration::Variable> > getVariables() const override
			{
				std::vector< std::shared_ptr<const Variable> > result;
				result.reserve(items.size());
				result.insert(result.end(), items.begin(), items.end());
				return result;
			}

			virtual std::shared_ptr<MarshalMethod> createMarshalMethod() const override;
		public:
			static std::shared_ptr<StructDeclaration> create(const std::vector< std::shared_ptr< DeclarationVariableImpl> >& items)
			{
				auto decl = std::shared_ptr<StructDeclarationImpl>(new StructDeclarationImpl(items));
				decl->instance = decl;
				return decl;
			}
		};

		class StructMarshalMethod : public MarshalMethod
		{
			std::shared_ptr<StructDeclarationImpl> decl;
		public:
			StructMarshalMethod(std::shared_ptr<StructDeclarationImpl> _decl)
				: decl(_decl)
			{}
			virtual ~StructMarshalMethod() {}
		public:
			virtual MarshalStream& marshal(MarshalStream& ms, const Variant& v) override
			{
				const auto& items = decl->items;
				for (auto it = items.begin(), ite = items.end(); it != ite; ++it)
				{
					const auto& i = *it;
					Variant cv = v.getVariant(i->name);
					i->method->marshal(ms, cv);
				}
				return ms;
			}
			virtual Variant unmarshal(const UnmarshalStream& us) override
			{
				Variant sv = Variant::createStruct();
				const auto& items = decl->items;
				for (auto it = items.begin(), ite = items.end(); it != ite; ++it)
				{
					const auto& i = *it;
					Variant cv = i->method->unmarshal(us);
					sv.setValue(i->name, cv);
				}
				return sv;
			}

			virtual std::shared_ptr<Declaration> getDeclaration() const override
			{
				return decl;
			}
		};

		std::shared_ptr<MarshalMethod> StructDeclarationImpl::createMarshalMethod() const
		{
			if (auto ins = instance.lock())
				return std::shared_ptr<MarshalMethod>(new StructMarshalMethod(ins));
			else
				return nullptr;
		}

	} // namespace helper {

	struct StructDeclarationCreator::Data
	{
		std::vector< std::shared_ptr<helper::DeclarationVariableImpl> > items;
	};

	StructDeclarationCreator::StructDeclarationCreator()
		: data(new Data())
	{}
	StructDeclarationCreator::~StructDeclarationCreator()
	{
		delete data;
	}

	StructDeclarationCreator& StructDeclarationCreator::insertVariable(const std::string& name, std::shared_ptr<Declaration> type)
	{
		auto it = std::find_if(data->items.begin(), data->items.end(), helper::StructDeclarationItemFinder(name));
		if (it != data->items.end())
			data->items.erase(it);
		auto dv = std::shared_ptr<helper::DeclarationVariableImpl>(new helper::DeclarationVariableImpl());
		dv->name = name;
		dv->method = type->createMarshalMethod();
		data->items.push_back(dv);
		return *this;
	}

	std::shared_ptr<StructDeclaration> StructDeclarationCreator::create() const
	{
		return helper::StructDeclarationImpl::create(data->items);
	}

} // namespace limax {

namespace limax {

	std::shared_ptr<MarshalMethod> DeclarationImpl::nullMarshalMethod = std::shared_ptr<MarshalMethod>(new NullMarshalMethod());

	std::shared_ptr<Declaration> DeclarationImpl::Null = std::shared_ptr<Declaration>(new DeclarationImpl::NullDeclaration(VariantType::Null));
	std::shared_ptr<Declaration> DeclarationImpl::Object = std::shared_ptr<Declaration>(new DeclarationImpl::NullDeclaration(VariantType::Object));
	std::shared_ptr<Declaration> DeclarationImpl::Boolean = std::shared_ptr<Declaration>(new DeclarationImpl::Single(VariantType::Boolean, &MarshalMethods::createBooleanMethod));
	std::shared_ptr<Declaration> DeclarationImpl::Byte = std::shared_ptr<Declaration>(new DeclarationImpl::Single(VariantType::Byte, &MarshalMethods::createInt8Method));
	std::shared_ptr<Declaration> DeclarationImpl::Short = std::shared_ptr<Declaration>(new DeclarationImpl::Single(VariantType::Short, &MarshalMethods::createInt16Method));
	std::shared_ptr<Declaration> DeclarationImpl::Int = std::shared_ptr<Declaration>(new DeclarationImpl::Single(VariantType::Int, &MarshalMethods::createInt32Method));
	std::shared_ptr<Declaration> DeclarationImpl::Long = std::shared_ptr<Declaration>(new DeclarationImpl::Single(VariantType::Long, &MarshalMethods::createInt64Method));
	std::shared_ptr<Declaration> DeclarationImpl::Float = std::shared_ptr<Declaration>(new DeclarationImpl::Single(VariantType::Float, &MarshalMethods::createFloatMethod));
	std::shared_ptr<Declaration> DeclarationImpl::Double = std::shared_ptr<Declaration>(new DeclarationImpl::Single(VariantType::Double, &MarshalMethods::createDoubleMethod));
	std::shared_ptr<Declaration> DeclarationImpl::String = std::shared_ptr<Declaration>(new DeclarationImpl::Single(VariantType::String, &MarshalMethods::createStringMethod));
	std::shared_ptr<Declaration> DeclarationImpl::Binary = std::shared_ptr<Declaration>(new DeclarationImpl::Single(VariantType::Binary, &MarshalMethods::createOctetsMethod));

	std::shared_ptr<Declaration> DeclarationImpl::createList(std::shared_ptr<Declaration> value)
	{
		auto result = std::shared_ptr<DeclarationImpl::Collection>(new DeclarationImpl::Collection(VariantType::List, value, &MarshalMethods::createListMethod));
		result->instance = result;
		return result;
	}
	std::shared_ptr<Declaration> DeclarationImpl::createVector(std::shared_ptr<Declaration> value)
	{
		auto result = std::shared_ptr<DeclarationImpl::Collection>(new DeclarationImpl::Collection(VariantType::Vector, value, &MarshalMethods::createVectorMethod));
		result->instance = result;
		return result;
	}
	std::shared_ptr<Declaration> DeclarationImpl::createSet(std::shared_ptr<Declaration> value)
	{
		auto result = std::shared_ptr<DeclarationImpl::Collection>(new DeclarationImpl::Collection(VariantType::Set, value, &MarshalMethods::createSetMethod));
		result->instance = result;
		return result;
	}
	std::shared_ptr<Declaration> DeclarationImpl::createMap(std::shared_ptr<Declaration> key, std::shared_ptr<Declaration> value)
	{
		auto result = std::shared_ptr<DeclarationImpl::Map>(new DeclarationImpl::Map(key, value));
		result->instance = result;
		return result;
	}

	std::shared_ptr<Declaration> DeclarationImpl::NullMarshalMethod::getDeclaration() const
	{
		return Null;
	}

	std::shared_ptr<MarshalMethod> DeclarationImpl::Map::createMarshalMethod() const
	{
		if (auto ins = instance.lock())
			return MarshalMethods::createMapMethod(ins, key->createMarshalMethod(), value->createMarshalMethod());
		else
			return nullptr;
	}

	std::shared_ptr<MarshalMethod> MarshalMethods::createBooleanMethod()
	{
		static std::shared_ptr<MarshalMethod> method = MarshalMethods::createSingleMarshalMethod(DeclarationImpl::Boolean, &Variant::getBooleanValue);
		return method;
	}
	std::shared_ptr<MarshalMethod> MarshalMethods::createInt8Method()
	{
		static std::shared_ptr<MarshalMethod> method = MarshalMethods::createSingleMarshalMethod(DeclarationImpl::Byte, &Variant::getByteValue);
		return method;
	}
	std::shared_ptr<MarshalMethod> MarshalMethods::createInt16Method()
	{
		static std::shared_ptr<MarshalMethod> method = MarshalMethods::createSingleMarshalMethod(DeclarationImpl::Short, &Variant::getShortValue);
		return method;
	}
	std::shared_ptr<MarshalMethod> MarshalMethods::createInt32Method()
	{
		static std::shared_ptr<MarshalMethod> method = MarshalMethods::createSingleMarshalMethod(DeclarationImpl::Int, &Variant::getIntValue);
		return method;
	}
	std::shared_ptr<MarshalMethod> MarshalMethods::createInt64Method()
	{
		static std::shared_ptr<MarshalMethod> method = MarshalMethods::createSingleMarshalMethod(DeclarationImpl::Long, &Variant::getLongValue);
		return method;
	}
	std::shared_ptr<MarshalMethod> MarshalMethods::createFloatMethod()
	{
		static std::shared_ptr<MarshalMethod> method = MarshalMethods::createSingleMarshalMethod(DeclarationImpl::Float, &Variant::getFloatValue);
		return method;
	}
	std::shared_ptr<MarshalMethod> MarshalMethods::createDoubleMethod()
	{
		static std::shared_ptr<MarshalMethod> method = MarshalMethods::createSingleMarshalMethod(DeclarationImpl::Double, &Variant::getDoubleValue);
		return method;
	}
	std::shared_ptr<MarshalMethod> MarshalMethods::createStringMethod()
	{
		static std::shared_ptr<MarshalMethod> method = MarshalMethods::createReferenceMarshalMethod(DeclarationImpl::String, &Variant::getStringValue);
		return method;
	}
	std::shared_ptr<MarshalMethod> MarshalMethods::createOctetsMethod()
	{
		static std::shared_ptr<MarshalMethod> method = MarshalMethods::createReferenceMarshalMethod(DeclarationImpl::Binary, &Variant::getOctetsValue);
		return method;
	}

	std::shared_ptr<MarshalMethod> MarshalMethods::createListMethod(std::shared_ptr<Declaration> list, std::shared_ptr<MarshalMethod> valueMethod)
	{
		return createCollectionMarshalMethod(list, valueMethod, &Variant::getListValue, &Variant::createList);
	}
	std::shared_ptr<MarshalMethod> MarshalMethods::createSetMethod(std::shared_ptr<Declaration> set, std::shared_ptr<MarshalMethod> valueMethod)
	{
		return createCollectionMarshalMethod(set, valueMethod, &Variant::getSetValue, &Variant::createSet);
	}
	std::shared_ptr<MarshalMethod> MarshalMethods::createVectorMethod(std::shared_ptr<Declaration> vector, std::shared_ptr<MarshalMethod> valueMethod)
	{
		return createCollectionMarshalMethod(vector, valueMethod, &Variant::getVectorValue, &Variant::createVector);
	}
	std::shared_ptr<MarshalMethod> MarshalMethods::createMapMethod(std::shared_ptr<Declaration> map, std::shared_ptr<MarshalMethod> keyMethod, std::shared_ptr<MarshalMethod> valueMethod)
	{
		return std::shared_ptr<MarshalMethod>(new MapMarshalMethod(map, keyMethod, valueMethod));
	}

} // namespace limax {

namespace limax {

	namespace variantdata {

		struct Data
		{
			Data() {}
			virtual ~Data() {}

			virtual VariantType getVariantType() const
			{
				return VariantType::Null;
			}

			virtual size_t hash_code() const {
				return 0;
			}

			virtual bool getBooleanValue() const
			{
				return 0;
			}
			virtual int8_t getByteValue() const
			{
				return 0;
			}
			virtual int16_t getShortValue() const
			{
				return 0;
			}
			virtual int32_t getIntValue() const
			{
				return 0;
			}
			virtual int64_t getLongValue() const
			{
				return 0;
			}
			virtual float getFloatValue() const
			{
				return 0.0;
			}
			virtual double getDoubleValue() const
			{
				return 0.0;
			}

			virtual const std::string& getStringValue() const
			{
				static std::string v;
				v.clear();
				return v;
			}
			virtual const Octets& getOctetsValue() const
			{
				static Octets v;
				v.clear();
				return v;
			}
			virtual const std::list<Variant>& getListValue() const
			{
				static std::list<Variant> l;
				l.clear();
				return l;
			}
			virtual const std::vector<Variant>& getVectorValue() const
			{
				static std::vector<Variant> l;
				l.clear();
				return l;
			}
			virtual const hashset<Variant>& getSetValue() const
			{
				static hashset<Variant> l;
				l.clear();
				return l;
			}
			virtual const hashmap<Variant, Variant>& getMapValue() const
			{
				static hashmap<Variant, Variant> m;
				m.clear();
				return m;
			}

			virtual std::list<Variant>& getRawListValue()
			{
				static std::list<Variant> l;
				l.clear();
				return l;
			}
			virtual std::vector<Variant>& getRawVectorValue()
			{
				static std::vector<Variant> l;
				l.clear();
				return l;
			}
			virtual hashset<Variant>& getRawSetValue()
			{
				static hashset<Variant> l;
				l.clear();
				return l;
			}
			virtual hashmap<Variant, Variant>& getRawMapValue()
			{
				static hashmap<Variant, Variant> m;
				m.clear();
				return m;
			}

			virtual void collectionInsert(Variant) {}
			virtual Variant mapInsert(Variant, Variant)
			{
				return Variant::Null;
			}

			inline bool getBoolean(const std::string& name) const
			{
				return getVariant(name).getBooleanValue();
			}
			inline int8_t getByte(const std::string& name) const
			{
				return getVariant(name).getByteValue();
			}
			inline int16_t getShort(const std::string& name) const
			{
				return getVariant(name).getShortValue();
			}
			inline int32_t getInt(const std::string& name) const
			{
				return getVariant(name).getIntValue();
			}
			inline int64_t getLong(const std::string& name) const
			{
				return getVariant(name).getLongValue();
			}
			inline float getFloat(const std::string& name) const
			{
				return getVariant(name).getFloatValue();
			}
			inline double getDouble(const std::string& name) const
			{
				return getVariant(name).getDoubleValue();
			}

			inline const std::string& getString(const std::string& name) const
			{
				return getVariant(name).getStringValue();
			}
			inline const Octets& getOctets(const std::string& name) const
			{
				return getVariant(name).getOctetsValue();
			}
			inline const std::list<Variant>& getList(const std::string& name) const
			{
				return getVariant(name).getListValue();
			}
			inline const std::vector<Variant>& getVector(const std::string& name) const
			{
				return getVariant(name).getVectorValue();
			}
			inline const hashset<Variant>& getSet(const std::string& name) const
			{
				return getVariant(name).getSetValue();
			}
			inline const hashmap<Variant, Variant>& getMap(const std::string& name) const
			{
				return getVariant(name).getMapValue();
			}

			inline std::list<Variant>& getRawList(const std::string& name)
			{
				return getVariant(name).getRawListValue();
			}
			inline std::vector<Variant>& getRawVector(const std::string& name)
			{
				return getVariant(name).getRawVectorValue();
			}
			inline hashset<Variant>& getRawSet(const std::string& name)
			{
				return getVariant(name).getRawSetValue();
			}
			inline hashmap<Variant, Variant>& getRawMap(const std::string& name)
			{
				return getVariant(name).getRawMapValue();
			}

			virtual Variant getVariant(const std::string&) const
			{
				return Variant::Null;
			}

			virtual void setVariant(const std::string&, Variant) {}

			virtual std::shared_ptr<Declaration> makeDeclaration() const
			{
				return DeclarationImpl::Null;
			}

			virtual bool equals(const Data& d) const
			{
				return VariantType::Null == d.getVariantType();
			}

			virtual int compare(const Data& d) const
			{
				return getVariantType() - d.getVariantType();
			}

			virtual std::string toString() const
			{
				return "Null";
			}
		};
		static std::shared_ptr<Data> nullData = std::shared_ptr<Data>(new Data());

		class BooleanData : public Data
		{
			bool			value;
		public:
			BooleanData(bool v)
				: value(v)
			{}
			virtual ~BooleanData() {}
		public:
			virtual VariantType getVariantType() const override
			{
				return VariantType::Boolean;
			}
			virtual size_t hash_code() const override
			{
				return limax::hash_code(value);
			}

			virtual bool getBooleanValue() const override
			{
				return value;
			}

			virtual std::shared_ptr<Declaration> makeDeclaration() const override
			{
				return DeclarationImpl::Boolean;
			}

			virtual bool equals(const Data& d) const override
			{
				if (VariantType::Boolean == d.getVariantType())
					return value == ((BooleanData*)&d)->value;
				else
					return false;
			}

			virtual int compare(const Data& d) const override
			{
				int c = Data::compare(d);
				if (0 != c)
					return c;
				return compareTo(value, ((BooleanData*)&d)->value);
			}

			virtual std::string toString() const override
			{
				return value ? "true" : "false";
			}
		};

		template<class Type> class NumberData : public Data
		{
			Type value;
			std::shared_ptr<Declaration> decl;
		public:
			NumberData(Type v, std::shared_ptr<Declaration> d)
				: value(v), decl(d)
			{}
			virtual ~NumberData() {}
		public:
			virtual VariantType getVariantType() const override
			{
				return decl->getType();
			}
			virtual size_t hash_code() const override
			{
				return limax::hash_code(value);
			}

			virtual bool getBooleanValue() const override
			{
				return 0 != aliasing_cast<int>(value);
			}
			virtual int8_t getByteValue() const override
			{
				return (int8_t)value;
			}
			virtual int16_t getShortValue() const override
			{
				return (int16_t)value;
			}
			virtual int32_t getIntValue() const override
			{
				return (int32_t)value;
			}
			virtual int64_t getLongValue() const override
			{
				return (int64_t)value;
			}
			virtual float getFloatValue() const override
			{
				return (float)value;
			}
			virtual double getDoubleValue() const override
			{
				return (double)value;
			}

			virtual std::shared_ptr<Declaration> makeDeclaration() const override
			{
				return decl;
			}

			virtual bool equals(const Data& d) const override
			{
				if (getVariantType() == d.getVariantType())
					return limax::equals(value, ((NumberData<Type>*)&d)->value);
				else
					return false;
			}

			virtual int compare(const Data& d) const override
			{
				int c = Data::compare(d);
				if (0 != c)
					return c;
				int64_t a = aliasing_cast<int64_t>(value);
				int64_t b = aliasing_cast<int64_t>(((NumberData<Type>*)&d)->value);
				return limax::compareTo(a, b);
			}

			virtual std::string toString() const override
			{
				return std::to_string(value);
			}
		};

		template<class Type> static inline Data* createNumberData(Type v, std::shared_ptr<Declaration> d)
		{
			return new NumberData<Type>(v, d);
		}

		class StringData : public Data
		{
			std::string			value;
		public:
			StringData(const std::string& v)
				: value(v)
			{}
			virtual ~StringData() {}
		public:
			virtual VariantType getVariantType() const override
			{
				return VariantType::String;
			}
			virtual size_t hash_code() const override
			{
				return limax::hash_code(value);
			}

			virtual const std::string& getStringValue() const override
			{
				return value;
			}

			virtual std::shared_ptr<Declaration> makeDeclaration() const override
			{
				return DeclarationImpl::String;
			}

			virtual bool equals(const Data& d) const override
			{
				if (VariantType::String == d.getVariantType())
					return limax::equals(value, ((StringData*)&d)->value);
				else
					return false;
			}

			virtual int compare(const Data& d) const override
			{
				int c = Data::compare(d);
				if (0 != c)
					return c;
				return value.compare(((StringData*)&d)->value);
			}

			virtual std::string toString() const override
			{
				return value;
			}
		};

		class OctetsData : public Data
		{
			Octets			value;
		public:
			OctetsData(const Octets& v)
				: value(v)
			{}
			virtual ~OctetsData() {}
		public:
			virtual VariantType getVariantType() const override
			{
				return VariantType::Binary;
			}
			virtual size_t hash_code() const override
			{
				return value.hash_code();
			}

			virtual const Octets& getOctetsValue() const override
			{
				return value;
			}

			virtual std::shared_ptr<Declaration> makeDeclaration() const override
			{
				return DeclarationImpl::Binary;
			}

			virtual bool equals(const Data& d) const override
			{
				if (VariantType::Binary == d.getVariantType())
					return value.equals(((OctetsData*)&d)->value);
				else
					return false;
			}

			virtual int compare(const Data& d) const override
			{
				int c = Data::compare(d);
				if (0 != c)
					return c;
				else
					return value.compare(((OctetsData*)&d)->value);
			}

			virtual std::string toString() const override
			{
				std::stringstream ss;
				ss << "[binary:" << value.size() << "]";
				return ss.str();
			}
		};

		namespace helper {

			template<class V> static inline int compare(const V& a, const V& b)
			{
				return a.compare(b);
			}
			template<class K, class V> static inline int compare(const std::pair<K, V>& a, const std::pair<K, V>& b)
			{
				int c = a.first.compare(b.first);
				if (0 != c)
					return c;
				c = a.second.compare(b.second);
				return c;
			}

			template<class CType> static inline int container_compare(const CType& a, const CType& b)
			{
				if (a.size() != b.size())
					return false;
				for (auto ait = a.begin(), aite = a.end(), bit = b.begin(); ait != aite; ++ait, ++bit)
				{
					const auto& va = *ait;
					const auto& vb = *bit;
					int c = compare(va, vb);
					if (0 != c)
						return c;
				}
				return 0;
			}

		} // namespace helper {

		template<class CType> class CollectionData : public Data
		{
		protected:
			CType		value;
		public:
			CollectionData() {}
			virtual ~CollectionData() {}
		public:
			virtual bool equals(const Data& d) const override
			{
				if (getVariantType() == d.getVariantType())
					return limax::equals(value, ((CollectionData<CType>*)&d)->value);
				else
					return false;
			}
			virtual size_t hash_code() const override
			{
				return limax::hash_code(value);
			}

			virtual int compare(const Data& d) const override
			{
				int c = Data::compare(d);
				if (0 != c)
					return c;
				else
					return helper::container_compare(value, ((CollectionData<CType>*)&d)->value);
			}
			virtual std::string toString() const override
			{
				std::ostringstream oss;
				oss << "[";
				for (const auto& v : value)
					oss << v.toString() << ",";
				oss << "]";

				return oss.str();
			}
		};

		class ListData : public CollectionData < std::list<Variant> >
		{
		public:
			ListData() {}
			virtual ~ListData() {}
		public:
			virtual VariantType getVariantType() const override
			{
				return VariantType::List;
			}

			virtual const std::list<Variant>& getListValue() const override
			{
				return value;
			}

			virtual std::list<Variant>& getRawListValue() override
			{
				return value;
			}

			virtual void collectionInsert(Variant v) override
			{
				value.push_back(v);
			}

			virtual std::shared_ptr<Declaration> makeDeclaration() const override
			{
				return DeclarationImpl::createList(DeclarationImpl::Object);
			}
		};

		class VectorData : public CollectionData < std::vector<Variant> >
		{
		public:
			VectorData() {}
			virtual ~VectorData() {}
		public:
			virtual VariantType getVariantType() const override
			{
				return VariantType::Vector;
			}

			virtual const std::vector<Variant>& getVectorValue() const override
			{
				return value;
			}

			virtual std::vector<Variant>& getRawVectorValue() override
			{
				return value;
			}

			virtual void collectionInsert(Variant v) override
			{
				value.push_back(v);
			}

			virtual std::shared_ptr<Declaration> makeDeclaration() const override
			{
				return DeclarationImpl::createVector(DeclarationImpl::Object);
			}
		};

		class SetData : public CollectionData < hashset<Variant> >
		{
		public:
			SetData() {}
			virtual ~SetData() {}
		public:
			virtual VariantType getVariantType() const override
			{
				return VariantType::Set;
			}

			virtual const hashset<Variant>& getSetValue() const override
			{
				return value;
			}
			virtual hashset<Variant>& getRawSetValue() override
			{
				return value;
			}

			virtual void collectionInsert(Variant v) override
			{
				value.insert(v);
			}

			virtual std::shared_ptr<Declaration> makeDeclaration() const override
			{
				return DeclarationImpl::createSet(DeclarationImpl::Object);
			}
		};

		class MapData : public Data
		{
			hashmap<Variant, Variant>	value;
		public:
			MapData() {}
			virtual ~MapData() {}
		public:
			virtual VariantType getVariantType() const override
			{
				return VariantType::Map;
			}
			virtual size_t hash_code() const override
			{
				return limax::hash_code(value);
			}

			virtual const hashmap<Variant, Variant>& getMapValue() const override
			{
				return value;
			}
			virtual hashmap<Variant, Variant>& getRawMapValue() override
			{
				return value;
			}
			virtual std::shared_ptr<Declaration> makeDeclaration() const override
			{
				return DeclarationImpl::createMap(DeclarationImpl::Object, DeclarationImpl::Object);
			}

			virtual Variant mapInsert(Variant k, Variant v) override
			{
				auto p = value.insert(std::make_pair(k, v));
				if (p.second)
					return Variant::Null;
				auto old = p.first->second;
				value[k] = v;
				return old;
			}

			virtual bool equals(const Data& d) const override
			{
				if (VariantType::Map == d.getVariantType())
					return limax::equals(value, ((MapData*)&d)->value);
				else
					return false;
			}

			virtual int compare(const Data& d) const override
			{
				int c = Data::compare(d);
				if (0 != c)
					return c;
				else
					return helper::container_compare(value, ((MapData*)&d)->value);
			}
			virtual std::string toString() const override
			{
				std::ostringstream oss;
				oss << "[";
				for (const auto& it : value)
				{
					const Variant& k = it.first;
					const Variant& v = it.second;
					oss << k.toString() << ',' << v.toString() << ";";
				}
				oss << "]";

				return oss.str();
			}
		};

		class StructData : public Data
		{
			hashmap<std::string, Variant> values;
		public:
			StructData() {}
			virtual ~StructData() {}
		public:
			virtual VariantType getVariantType() const override
			{
				return VariantType::Struct;
			}
			virtual size_t hash_code() const override
			{
				return limax::hash_code(values);
			}

			virtual Variant getVariant(const std::string& name) const override
			{
				auto it = values.find(name);
				if (it == values.end())
					return Variant::Null;
				else
					return it->second;
			}
			virtual void setVariant(const std::string& name, Variant value) override
			{
				values[name] = value;
			}

			virtual std::shared_ptr<Declaration> makeDeclaration() const override
			{
				StructDeclarationCreator creator;
				for (auto it = values.begin(), ite = values.end(); it != ite; ++it)
					creator.insertVariable(it->first, it->second.makeDeclaration());
				return creator.create();
			}

			virtual bool equals(const Data& d) const override
			{
				if (VariantType::Struct == d.getVariantType())
					return limax::equals(values, ((StructData*)&d)->values);
				else
					return false;
			}

			virtual int compare(const Data& d) const override
			{
				int c = Data::compare(d);
				if (0 != c)
					return c;
				else
					return helper::container_compare(values, ((StructData*)&d)->values);
			}
			virtual std::string toString() const override
			{
				std::ostringstream oss;
				oss << "[";
				for (const auto& it : values)
				{
					const auto& k = it.first;
					const auto& v = it.second;
					oss << k << ',' << v.toString() << ";";
				}
				oss << "]";

				return oss.str();
			}
		};

	} // namespace variantdata {

	Variant Variant::Null;
	Variant Variant::True(new variantdata::BooleanData(true));
	Variant Variant::False(new variantdata::BooleanData(false));

	Variant::Variant()
		: data(variantdata::nullData)
	{}

	Variant::Variant(variantdata::Data* _data)
		: data(_data)
	{}

	Variant::Variant(const Variant& src)
		: data(src.data)
	{}

	Variant::~Variant() {}

	Variant& Variant::operator=(const Variant& src)
	{
		data = src.data;
		return *this;
	}

	bool Variant::operator==(const Variant& dst) const
	{
		return data->equals(*dst.data.get());
	}
	bool Variant::operator!=(const Variant& dst) const
	{
		return !data->equals(*dst.data.get());
	}

	bool Variant::operator<(const Variant& dst) const
	{
		return data->compare(*dst.data.get()) < 0;
	}

	VariantType Variant::getVariantType() const
	{
		return data->getVariantType();
	}

	std::shared_ptr<Declaration> Variant::makeDeclaration() const
	{
		return data->makeDeclaration();
	}

	bool Variant::equals(const Variant& dst) const
	{
		return data->equals(*dst.data.get());
	}

	int Variant::compare(const Variant& dst) const
	{
		return data->compare(*dst.data.get()) < 0;
	}

	std::string Variant::toString() const
	{
		return data->toString();
	}

	size_t Variant::hash_code() const
	{
		return data->hash_code();
	}

	Variant Variant::create(bool v)
	{
		return v ? True : False;
	}

	Variant Variant::create(int8_t v)
	{
		return Variant(variantdata::createNumberData(v, DeclarationImpl::Byte));
	}

	Variant Variant::create(int16_t v)
	{
		return Variant(variantdata::createNumberData(v, DeclarationImpl::Short));
	}

	Variant Variant::create(int32_t v)
	{
		return Variant(variantdata::createNumberData(v, DeclarationImpl::Int));
	}

	Variant Variant::create(int64_t v)
	{
		return Variant(variantdata::createNumberData(v, DeclarationImpl::Long));
	}

	Variant Variant::create(float v)
	{
		return Variant(variantdata::createNumberData(v, DeclarationImpl::Float));
	}

	Variant Variant::create(double v)
	{
		return Variant(variantdata::createNumberData(v, DeclarationImpl::Double));
	}

	Variant Variant::create(const Octets& v)
	{
		return Variant(new variantdata::OctetsData(v));
	}

	Variant Variant::create(const char* v)
	{
		if (nullptr == v)
			return Null;
		else
			return create(std::string(v));
	}
	Variant Variant::create(const std::string& v)
	{
		return Variant(new variantdata::StringData(v));
	}

	Variant Variant::createList()
	{
		return Variant(new variantdata::ListData());
	}

	Variant Variant::createVector()
	{
		return Variant(new variantdata::VectorData());
	}

	Variant Variant::createSet()
	{
		return Variant(new variantdata::SetData());
	}

	Variant Variant::createMap()
	{
		return Variant(new variantdata::MapData());
	}

	Variant Variant::createStruct()
	{
		return Variant(new variantdata::StructData());
	}

	bool Variant::getBooleanValue() const
	{
		return data->getBooleanValue();
	}
	int8_t Variant::getByteValue() const
	{
		return data->getByteValue();
	}
	int16_t Variant::getShortValue() const
	{
		return data->getShortValue();
	}
	int32_t Variant::getIntValue() const
	{
		return data->getIntValue();
	}
	int64_t Variant::getLongValue() const
	{
		return data->getLongValue();
	}
	float Variant::getFloatValue() const
	{
		return data->getFloatValue();
	}
	double Variant::getDoubleValue() const
	{
		return data->getDoubleValue();
	}
	const std::string& Variant::getStringValue() const
	{
		return data->getStringValue();
	}
	const Octets& Variant::getOctetsValue() const
	{
		return data->getOctetsValue();
	}
	const std::list<Variant>& Variant::getListValue() const
	{
		return data->getListValue();
	}
	const std::vector<Variant>& Variant::getVectorValue() const
	{
		return data->getVectorValue();
	}
	const hashset<Variant>& Variant::getSetValue() const
	{
		return data->getSetValue();
	}
	const hashmap<Variant, Variant>& Variant::getMapValue() const
	{
		return data->getMapValue();
	}

	std::list<Variant>& Variant::getRawListValue()
	{
		return data->getRawListValue();
	}
	std::vector<Variant>& Variant::getRawVectorValue()
	{
		return data->getRawVectorValue();
	}
	hashset<Variant>& Variant::getRawSetValue()
	{
		return data->getRawSetValue();
	}
	hashmap<Variant, Variant>& Variant::getRawMapValue()
	{
		return data->getRawMapValue();
	}

	void Variant::collectionInsert(const Variant& v)
	{
		data->collectionInsert(v);
	}

	Variant Variant::mapInsert(const Variant& k, const Variant& v)
	{
		return data->mapInsert(k, v);
	}

	bool Variant::getBoolean(const std::string& name) const
	{
		return data->getBoolean(name);
	}
	int8_t Variant::getByte(const std::string& name) const
	{
		return data->getByte(name);
	}
	int16_t Variant::getShort(const std::string& name) const
	{
		return data->getShort(name);
	}
	int32_t Variant::getInt(const std::string& name) const
	{
		return data->getInt(name);
	}
	int64_t Variant::getLong(const std::string& name) const
	{
		return data->getLong(name);
	}
	float Variant::getFloat(const std::string& name) const
	{
		return data->getFloat(name);
	}
	double Variant::getDouble(const std::string& name) const
	{
		return data->getDouble(name);
	}
	const std::string& Variant::getString(const std::string& name) const
	{
		return data->getString(name);
	}
	const Octets& Variant::getOctets(const std::string& name) const
	{
		return data->getOctets(name);
	}
	const std::list<Variant>& Variant::getList(const std::string& name) const
	{
		return data->getList(name);
	}
	const std::vector<Variant>& Variant::getVector(const std::string& name) const
	{
		return data->getVector(name);
	}
	const hashset<Variant>& Variant::getSet(const std::string& name) const
	{
		return data->getSet(name);
	}
	const hashmap<Variant, Variant>& Variant::getMap(const std::string& name) const
	{
		return data->getMap(name);
	}
	std::list<Variant>& Variant::getRawList(const std::string& name)
	{
		return data->getRawList(name);
	}
	std::vector<Variant>& Variant::getRawVector(const std::string& name)
	{
		return data->getRawVector(name);
	}
	hashset<Variant>& Variant::getRawSet(const std::string& name)
	{
		return data->getRawSet(name);
	}
	hashmap<Variant, Variant>& Variant::getRawMap(const std::string& name)
	{
		return data->getRawMap(name);
	}

	Variant Variant::getVariant(const std::string& name) const
	{
		return data->getVariant(name);
	}

	void Variant::setValue(const std::string& name, const Variant& v)
	{
		data->setVariant(name, v);
	}

} // namespace limax {

namespace limax {

	ViewDefine::BindVarDefine::BindVarDefine(const std::string& _name, std::shared_ptr<MarshalMethod> _method)
		: name(_name), method(_method)
	{}
	ViewDefine::BindVarDefine::BindVarDefine() {}
	ViewDefine::BindVarDefine::~BindVarDefine() {}

	const std::string& ViewDefine::BindVarDefine::getName() const
	{
		return name;
	}
	std::shared_ptr<MarshalMethod> ViewDefine::BindVarDefine::getMarshalMethod() const
	{
		return method;
	}

	ViewDefine::VariableDefine::VariableDefine(int8_t _varIndex, bool _subscribe, bool _bind, const std::string& _name, std::shared_ptr<MarshalMethod> _method)
		: varIndex(_varIndex), subscribe(_subscribe), bind(_bind), name(_name), method(_method)
	{}
	ViewDefine::VariableDefine::VariableDefine() {}
	ViewDefine::VariableDefine::~VariableDefine() {}

	int8_t ViewDefine::VariableDefine::getIndex() const
	{
		return varIndex;
	}
	bool ViewDefine::VariableDefine::isSubscribe() const
	{
		return subscribe;
	}
	bool ViewDefine::VariableDefine::isBind() const
	{
		return bind;
	}
	const std::string& ViewDefine::VariableDefine::getName() const
	{
		return name;
	}
	std::shared_ptr<MarshalMethod> ViewDefine::VariableDefine::getMarshalMethod() const
	{
		return method;
	}

	const ViewDefine::BindVarDefine* ViewDefine::VariableDefine::getBindVarDefine(int8_t field) const
	{
		auto it = bindVars.find(field);
		if (it == bindVars.end())
			return nullptr;
		else
			return &it->second;
	}

	void ViewDefine::VariableDefine::addBindVarDefine(int8_t field, const BindVarDefine& var)
	{
		bindVars.insert(std::make_pair(field, var));
	}

	ViewDefine::ControlDefine::ControlDefine(int8_t _ctrlIndex, const std::string& _name, std::shared_ptr<MarshalMethod> _method)
		: ctrlIndex(_ctrlIndex), name(_name), method(_method)
	{}
	ViewDefine::ControlDefine::ControlDefine() {}
	ViewDefine::ControlDefine::~ControlDefine() {}

	int8_t ViewDefine::ControlDefine::getIndex() const
	{
		return ctrlIndex;
	}
	const std::string& ViewDefine::ControlDefine::getName() const
	{
		return name;
	}
	std::shared_ptr<MarshalMethod> ViewDefine::ControlDefine::getMarshalMethod() const
	{
		return method;
	}

	ViewDefine::ViewDefine()
		: classindex(-1), temporary(false)
	{}

	ViewDefine::ViewDefine(int16_t _classindex, const std::string& _viewName, bool _temporary)
		: viewName(_viewName), classindex(_classindex), temporary(_temporary)
	{}
	ViewDefine::~ViewDefine() {}

	int16_t ViewDefine::getClassIndex() const
	{
		return classindex;
	}
	const std::string& ViewDefine::getViewName() const
	{
		return viewName;
	}
	bool ViewDefine::isTemporary() const
	{
		return temporary;
	}
	void ViewDefine::addVaribaleDefine(const VariableDefine& vd)
	{
		vars.push_back(vd);
	}
	const std::vector<ViewDefine::VariableDefine>& ViewDefine::getVaribaleDefine() const
	{
		return vars;
	}
	void ViewDefine::addControlDefine(const ControlDefine& cd)
	{
		ctrls.push_back(cd);
	}
	const std::vector<ViewDefine::ControlDefine>& ViewDefine::getControlDefine() const
	{
		return ctrls;
	}

} // namespace limax {

namespace limax {

	VariantViewChangedEvent::VariantViewChangedEvent() {}
	VariantViewChangedEvent::~VariantViewChangedEvent() {}

	std::string VariantViewChangedEvent::toString() const
	{
		std::stringstream ss;
		ss << getView()->toString() << " " << getSessionId() << " " << getFieldName() << " " << getValue().toString() << " " << ViewChangedEvent::getViewChangedTypeName(getType());
		return ss.str();
	}

	VariantView::VariantView() {}
	VariantView::~VariantView() {}

	VariantView::Define::Define() {}
	VariantView::Define::~Define() {}

} // namespace limax {

namespace limax {

	TemporaryViewHandler::TemporaryViewHandler() {}
	TemporaryViewHandler::~TemporaryViewHandler() {}

} // namespace limax {

#include "xmlgeninc/xmlgen.h"
#include "viewdefineparser.h"

namespace limax {
	namespace variant {
		namespace viewdefineparser {

			VariantDefineParser::DeclarationStore::DeclarationStore()
			{
				basemap.insert(std::make_pair(limax::defines::VariantDefines::BASE_TYPE_BINARY, DeclarationImpl::Binary));
				basemap.insert(std::make_pair(limax::defines::VariantDefines::BASE_TYPE_BOOLEAN, DeclarationImpl::Boolean));
				basemap.insert(std::make_pair(limax::defines::VariantDefines::BASE_TYPE_BYTE, DeclarationImpl::Byte));
				basemap.insert(std::make_pair(limax::defines::VariantDefines::BASE_TYPE_DOUBLE, DeclarationImpl::Double));
				basemap.insert(std::make_pair(limax::defines::VariantDefines::BASE_TYPE_FLOAT, DeclarationImpl::Float));
				basemap.insert(std::make_pair(limax::defines::VariantDefines::BASE_TYPE_INT, DeclarationImpl::Int));
				basemap.insert(std::make_pair(limax::defines::VariantDefines::BASE_TYPE_LONG, DeclarationImpl::Long));
				basemap.insert(std::make_pair(limax::defines::VariantDefines::BASE_TYPE_SHORT, DeclarationImpl::Short));
				basemap.insert(std::make_pair(limax::defines::VariantDefines::BASE_TYPE_STRING, DeclarationImpl::String));
			}

			std::shared_ptr<Declaration> VariantDefineParser::DeclarationStore::getBase(int type, int typeKey, int typeValue)
			{
				switch (type)
				{
				case limax::defines::VariantDefines::BASE_TYPE_LIST:
					return DeclarationImpl::createList(get(typeValue));
				case limax::defines::VariantDefines::BASE_TYPE_MAP:
					return DeclarationImpl::createMap(get(typeKey), get(typeValue));
				case limax::defines::VariantDefines::BASE_TYPE_SET:
					return DeclarationImpl::createSet(get(typeValue));
				case limax::defines::VariantDefines::BASE_TYPE_VECTOR:
					return DeclarationImpl::createVector(get(typeValue));
				default:
					return basemap[type];
				}
			}

		} // namespace viewdefineparser {
	} // namespace variant {
} // namespace limax {

#include "viewcontextimpl.h"

namespace limax {

	VariantManager::VariantManager(limax::helper::VariantViewContextImpl* _impl)
		: impl(_impl)
	{}
	VariantManager::~VariantManager() {}

	void VariantManager::setTemporaryViewHandler(const std::string& name, TemporaryViewHandler* handler)
	{
		impl->setTemporaryViewHandler(name, handler);
	}

	TemporaryViewHandler* VariantManager::getTemporaryViewHandler(const std::string& name)
	{
		return impl->getTemporaryViewHandler(name);
	}

	const std::vector<std::string> VariantManager::getSessionOrGlobalViewNames() const
	{
		return impl->getSessionOrGlobalViewNames();
	}

	const std::vector<std::string> VariantManager::getTemporaryViewNames() const
	{
		return impl->getTemporaryViewNames();
	}

	VariantView* VariantManager::getSessionOrGlobalView(const std::string& name)
	{
		return impl->getSessionOrGlobalViewInstance(name);
	}

	VariantView* VariantManager::findTemporaryView(const std::string& name, int instanceindex)
	{
		return impl->findTemporaryViewInstance(name, instanceindex);
	}

	void VariantManager::sendMessage(VariantView* view, const std::string& msg)
	{
		impl->sendVariantViewMessage(view, msg);
	}

	VariantManager* VariantManager::getInstance(EndpointManager* endpoint, int32_t pvid)
	{
		auto vc = endpoint->getViewContext(pvid, ViewContext::Type::Variant);
		auto impl = dynamic_cast<limax::helper::VariantViewContextImpl*>(vc);
		return impl ? impl->getVariantManager() : nullptr;
	}

} // namespace limax {


