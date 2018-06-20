#include "endpointinc.h"
namespace limax {

	int Protocol::typeHigh(Type type)
	{
		return (type >> 8) & 0xffffff;
	}
	int Protocol::typeLow(Type type)
	{
		return type & 0xff;
	}

	Protocol::Protocol(){}

	Protocol::Protocol(const Protocol& src) :type(src.type)
	{}

	Protocol::~Protocol() {}

	void Protocol::destroy()
	{
		delete this;
	}

	Transport* Protocol::getTransport() const
	{
		return transport;
	}
	void Protocol::setTransport(Transport *transport)
	{
		this->transport = transport;
	}
	void Protocol::send(Transport *transport)
	{
		transport->sendProtocol(*this);
	}

	void Protocol::encode(MarshalStream& ms) const
	{
		OctetsMarshalStream poms;
		poms.push(*this);
		ms << type << poms.getOctets();
	}

	Octets Protocol::encode() const
	{
		OctetsMarshalStream oms;
		encode(oms);
		return oms.getOctets();
	}

	std::ostream& Protocol::trace(std::ostream& os) const
	{
		return os;
	}

	Protocol::Type Protocol::getType() const
	{
		return type;
	}

	Protocol::Stub::Stub() {}
	Protocol::Stub::~Stub() {}

	bool Protocol::Stub::sizePolicy(size_t size)
	{
		size_t thisize = getSize();
		return (0 == thisize) || (size <= thisize);
	}

	State::State() {}
	State::~State() {}

	State::State(std::shared_ptr<State> s)
	{
		addStub(s);
	}

	hashset<int32_t> State::getProviderIds() const
	{
		hashset<int32_t> ids;
		for (auto it : stubmap)
		{
			auto ptype = it.first;
			auto pvid = Protocol::typeHigh(ptype);
			if (pvid)
				ids.insert(pvid);
		}
		return ids;
	}

	void State::addStub(std::shared_ptr<Protocol::Stub> s)
	{
		stubmap.insert(std::make_pair(s->getType(), s));
	}

	void State::addStub(std::shared_ptr<State> s)
	{
		if (s)
			stubmap.insert(s->stubmap.begin(), s->stubmap.end());
	}

	std::shared_ptr<Protocol::Stub> State::getStub(Protocol::Type t) const
	{
		auto it = stubmap.find(t);
		if (it == stubmap.end())
			return nullptr;
		else
			return it->second;
	}

} // namespace limax {
