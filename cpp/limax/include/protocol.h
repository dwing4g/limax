#pragma once

namespace limax {
	struct LIMAX_DLL_EXPORT_API Transport;
	class LIMAX_DLL_EXPORT_API Protocol : public Marshal
	{
	public:
		typedef int Type;
	private:
		Transport*	transport;
	protected:
		Type type;
	public:
		struct Exception {};
		static int typeHigh(Type type);
		static int typeLow(Type type);
		virtual void process() = 0;
		Transport* getTransport() const;
		void setTransport(Transport* transport);
		void send(Transport *);
		virtual ~Protocol();
		Protocol();
		Protocol(const Protocol&);
		void encode(MarshalStream&) const;
		virtual Octets encode() const;
		virtual Protocol* clone() const = 0;
		virtual std::ostream& trace(std::ostream&) const;
		virtual Type getType() const;
		virtual void destroy();

		struct LIMAX_DLL_EXPORT_API Stub
		{
			Stub();
			virtual ~Stub();
			bool sizePolicy(size_t size);
			virtual size_t getSize() = 0;
			virtual Type getType() = 0;
			virtual Protocol* create() = 0;
		};

		template<typename T> class TStub : public Stub
		{
			size_t	size;
		public:
			inline TStub(size_t s) : size(s){}
			virtual ~TStub() {}
			virtual size_t getSize() override
			{
				return size;
			}
			virtual Type getType() override
			{
				return T::TYPE;
			}
			virtual Protocol* create() override
			{
				return new T();
			}
		};
	};

	class LIMAX_DLL_EXPORT_API State
	{
		hashmap<Protocol::Type, std::shared_ptr<Protocol::Stub> > stubmap;
	public:
		State();
		State(std::shared_ptr<State>);
		~State();
		hashset<int32_t> getProviderIds() const;
		void addStub(std::shared_ptr<Protocol::Stub>);
		void addStub(std::shared_ptr<State>);
		std::shared_ptr<Protocol::Stub> getStub(Protocol::Type) const;
	};

} // namespace limax {
