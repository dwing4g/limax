#pragma once

namespace limax {

	class Marshal;
	template<typename Container> class STLContainer;
	template<typename Container> inline STLContainer<Container> MarshalContainer(const Container&);

	template<typename T> inline T& remove_const(const T &t)
	{
		return const_cast<T&>(t);
	}

	namespace __aliasing_cast_helper {
		template<typename U, typename V> union TypeAliasing
		{
			U u;
			V v;
			inline TypeAliasing(V _v)
				: v(_v)
			{}
			inline operator U() const
			{
				return u;
			}
		};
	} // namespace __aliasing_cast_helper {
	template<typename U, typename V> inline U aliasing_cast(V v)
	{
		__aliasing_cast_helper::TypeAliasing<U, V> data(v);
		return data;
	}

	struct MarshalException {};

	class LIMAX_DLL_EXPORT_API MarshalStreamSource
	{
	public:
		MarshalStreamSource();
		virtual ~MarshalStreamSource();
	public:
		virtual void pushbytes(const void*, size_t) = 0;
	};

	class LIMAX_DLL_EXPORT_API UnmarshalStreamSource
	{
	public:
		UnmarshalStreamSource();
		virtual ~UnmarshalStreamSource();
	public:
		enum Transaction { Begin, Commit, Rollback };
	public:
		virtual const void* pop(size_t) const = 0;
		virtual void transaction(Transaction) const = 0;
		virtual bool eos() const = 0;
	};

	class LIMAX_DLL_EXPORT_API MarshalStream
	{
		std::shared_ptr<MarshalStreamSource> source;
	private:
		template<typename T> inline MarshalStream& push_byte(T t)
		{
			source->pushbytes(&t, sizeof(t));
			return *this;
		}

		MarshalStream() = delete;
		MarshalStream(const MarshalStream&) = delete;
		MarshalStream& operator=(const MarshalStream&) = delete;
	public:
		MarshalStream(std::shared_ptr<MarshalStreamSource> src);
		MarshalStream(MarshalStreamSource& src);
		MarshalStream(Octets& src);
		~MarshalStream();
	public:
		MarshalStream& push(bool x);
		MarshalStream& push(int8_t x);
		MarshalStream& push(uint8_t x);
		MarshalStream& push(int16_t x);
		MarshalStream& push(uint16_t x);
		MarshalStream& push(int32_t x);
		MarshalStream& push(uint32_t x);
		MarshalStream& push(int64_t x);
		MarshalStream& push(uint64_t x);
		MarshalStream& push(float x);
		MarshalStream& push(double x);
		MarshalStream& push(const Marshal& x);
		MarshalStream& push(const Octets& x);
		MarshalStream& marshal_size(int32_t x);

		inline MarshalStream& operator << (bool x)
		{
			return push(x);
		}
		inline MarshalStream& operator << (int8_t x)
		{
			return push(x);
		}
		inline MarshalStream& operator << (uint8_t x)
		{
			return push(x);
		}
		inline MarshalStream& operator << (int16_t x)
		{
			return push(x);
		}
		inline MarshalStream& operator << (uint16_t x)
		{
			return push(x);
		}
		inline MarshalStream& operator << (int32_t x)
		{
			return push(x);
		}
		inline MarshalStream& operator << (uint32_t x)
		{
			return push(x);
		}
		inline MarshalStream& operator << (int64_t x)
		{
			return push(x);
		}
		inline MarshalStream& operator << (uint64_t x)
		{
			return push(x);
		}

		MarshalStream& operator << (float x)
		{
			return push(x);
		}
		MarshalStream& operator << (double x)
		{
			return push(x);
		}

		MarshalStream& operator << (const Marshal& x)
		{
			return push(x);
		}
		MarshalStream& operator << (const Octets& x)
		{
			return push(x);
		}

		template<typename T> inline MarshalStream& operator << (const std::basic_string<T>& x)
		{
			static_assert(sizeof(T) == 1, "wide char not support!");

			uint32_t bytes = (uint32_t)(x.length() * sizeof(T)); // count of bytes
			marshal_size(bytes);
			source->pushbytes((const void*)x.c_str(), bytes);
			return *this;
		}
		inline MarshalStream& push_byte(const char* x, size_t len)
		{
			source->pushbytes((const void*)x, len);
			return *this;
		}

		template<typename T1, typename T2> inline MarshalStream& operator << (const std::pair<T1, T2>& x)
		{
			return *this << x.first << x.second;
		}
		template<typename T> inline MarshalStream& operator << (const std::vector<T>& x)
		{
			return *this << (MarshalContainer(x));
		}
		template<typename T> inline MarshalStream& operator << (const std::list<T>& x)
		{
			return *this << (MarshalContainer(x));
		}
		template<typename T> inline MarshalStream& operator << (const std::deque<T>& x)
		{
			return *this << (MarshalContainer(x));
		}
		template<typename T1, typename T2> inline MarshalStream& operator << (const std::unordered_map<T1, T2>& x)
		{
			return *this << (MarshalContainer(x));
		}
	};

	class LIMAX_DLL_EXPORT_API UnmarshalStream
	{
		std::shared_ptr<UnmarshalStreamSource> source;
	private:
		template<typename T> inline void pop_byte(T &t) const
		{
			t = *(T*)(source->pop(sizeof(t)));
		}

		UnmarshalStream() = delete;
		UnmarshalStream(const UnmarshalStream&) = delete;
		UnmarshalStream& operator=(const UnmarshalStream&) = delete;
	public:
		UnmarshalStream(std::shared_ptr<UnmarshalStreamSource> src);
		UnmarshalStream(UnmarshalStreamSource& src);
		UnmarshalStream(Octets& src);
		~UnmarshalStream();
	public:
		uint8_t pop_byte_8() const;
		uint16_t pop_byte_16() const;
		uint32_t pop_byte_32() const;
		uint64_t pop_byte_64() const;

		bool unmarshal_bool() const;
		int8_t unmarshal_byte() const;
		uint8_t pop_uint_8() const;
		int16_t unmarshal_short() const;
		uint16_t pop_uint_16() const;
		int32_t unmarshal_int() const;
		uint32_t pop_uint_32() const;
		int64_t unmarshal_long() const;
		uint64_t pop_uint_64() const;
		int32_t unmarshal_size() const;

		float unmarshal_float() const;
		double unmarshal_double() const;
		void unmarshal(const Marshal& x) const;
		void unmarshal_Octets(Octets& x) const;
		void pop_byte(char* x, size_t len) const;

		void transaction(UnmarshalStreamSource::Transaction trans) const;

		inline const UnmarshalStream& operator >> (UnmarshalStreamSource::Transaction trans) const
		{
			transaction(trans);
			return *this;
		}

		inline const UnmarshalStream& operator >> (bool& x) const
		{
			x = unmarshal_bool();
			return *this;
		}
		inline const UnmarshalStream& operator >> (char& x) const
		{
			x = unmarshal_byte();
			return *this;
		}
		inline const UnmarshalStream& operator >> (int8_t& x) const
		{
			x = unmarshal_byte();
			return *this;
		}
		inline const UnmarshalStream& operator >> (uint8_t& x) const
		{
			x = pop_uint_8();
			return *this;
		}
		inline const UnmarshalStream& operator >> (int16_t& x) const
		{
			x = unmarshal_short();
			return *this;
		}
		inline const UnmarshalStream& operator >> (uint16_t& x) const
		{
			x = pop_uint_16();
			return *this;
		}
		inline const UnmarshalStream& operator >> (int32_t& x) const
		{
			x = unmarshal_int();
			return *this;
		}
		inline const UnmarshalStream& operator >> (uint32_t& x) const
		{
			x = pop_uint_32();
			return *this;
		}
		inline const UnmarshalStream& operator >> (int64_t& x) const
		{
			x = unmarshal_long();
			return *this;
		}
		inline const UnmarshalStream& operator >> (uint64_t& x) const
		{
			x = pop_uint_64();
			return *this;
		}

		inline const UnmarshalStream& operator >> (float &x) const
		{
			x = unmarshal_float();
			return *this;
		}
		inline const UnmarshalStream& operator >> (double &x) const
		{
			x = unmarshal_double();
			return *this;
		}
		const UnmarshalStream& operator >> (const Marshal& x) const
		{
			unmarshal(x);
			return *this;
		}
		const UnmarshalStream& operator >> (Octets& x) const
		{
			unmarshal_Octets(x);
			return *this;
		}
		template<typename T> inline const UnmarshalStream& operator >> (std::basic_string<T>& x) const
		{
			static_assert(sizeof(T) == 1, "wide char not support!");

			size_t bytes = (size_t)unmarshal_size();
			if (bytes % sizeof(T))
				throw MarshalException(); // invalid length
			const void* data = source->pop(bytes);
			x.assign((T*)data, bytes / sizeof(T));
			return *this;
		}
		template<typename T1, typename T2> inline const UnmarshalStream& operator >> (std::pair<T1, T2>& x) const
		{
			return *this >> x.first >> x.second;
		}
		template<typename T1, typename T2> inline const UnmarshalStream& operator >> (std::pair<const T1, T2>& x) const
		{
			return *this >> remove_const(x.first) >> x.second;
		}
		template<typename T> inline const UnmarshalStream& operator >> (std::vector<T>& x) const
		{
			return *this >> (MarshalContainer(x));
		}
		template<typename T> inline const UnmarshalStream& operator >> (std::deque<T>& x) const
		{
			return *this >> (MarshalContainer(x));
		}
		template<typename T> inline const UnmarshalStream& operator >> (std::list<T>& x) const
		{
			return *this >> (MarshalContainer(x));
		}
		template<typename T1, typename T2> inline const UnmarshalStream& operator >> (std::unordered_map< T1, T2>& x) const
		{
			return *this >> (MarshalContainer(x));
		}
	};

} // namespace limax {

namespace limax {

	class LIMAX_DLL_EXPORT_API Marshal
	{
	public:
		typedef UnmarshalStreamSource::Transaction Transaction;
	public:
		Marshal();
		virtual ~Marshal();
	public:
		virtual MarshalStream& marshal(MarshalStream&) const = 0;
		virtual const UnmarshalStream& unmarshal(const UnmarshalStream&) = 0;
		virtual std::ostream& trace(std::ostream& os) const;
	};

	template<typename Container> class STLContainer : public Marshal
	{
		Container& container;
	public:
		explicit inline STLContainer(Container& c) : container(c)
		{}
		MarshalStream& marshal(MarshalStream& ms) const
		{
			ms.marshal_size((int32_t)container.size());
			for (const auto& i : container)
				ms << i;
			return ms;
		}
		const UnmarshalStream& unmarshal(const UnmarshalStream& us)
		{
			container.clear();
			for (size_t size = (size_t)us.unmarshal_size(); size > 0; --size)
			{
				typename Container::value_type tmp;
				us >> tmp;
				container.insert(container.end(), tmp);
			}
			return us;
		}
	};

	template<typename Container> inline STLContainer<Container> MarshalContainer(const Container& c)
	{
		return STLContainer<Container>(remove_const(c));
	}

	/////////////////////////////////////////////////////////////////////////////
	// trace
	inline std::ostream& operator << (std::ostream & os, const Marshal& s)
	{
		return s.trace(os);
	}

	template<class _T1, class _T2> inline std::ostream& operator<<(std::ostream& os, const std::pair<_T1, _T2>& __x)
	{
		return os << __x.first << '=' << __x.second;
	}

	template<typename _CType> inline std::ostream& TraceContainer(std::ostream& os, const _CType& c)
	{
		os << '[';
		auto i = c.begin(), e = c.end();
		if (i != e)
		{
			os << *i;
			for (++i; i != e; ++i)
				os << ',' << *i;
		}
		os << ']';
		return os;
	}

	template<typename T> inline std::ostream& operator << (std::ostream& os, const std::vector<T>& x)
	{
		return TraceContainer(os, x);
	}

	template<typename T> inline std::ostream& operator << (std::ostream& os, const std::list<T>& x)
	{
		return TraceContainer(os, x);
	}

	template<typename T> inline std::ostream& operator << (std::ostream& os, const std::deque<T>& x)
	{
		return TraceContainer(os, x);
	}

	template<typename T1, typename T2> inline std::ostream& operator << (std::ostream& os, const std::unordered_map<T1, T2>& x)
	{
		return TraceContainer(os, x);
	}

} // namespace limax {

namespace limax {

	class LIMAX_DLL_EXPORT_API OctetsMarshalStreamSource : public MarshalStreamSource
	{
		Octets&		data;

		OctetsMarshalStreamSource() = delete;
		OctetsMarshalStreamSource(const OctetsMarshalStreamSource&) = delete;
		OctetsMarshalStreamSource& operator=(const OctetsMarshalStreamSource&) = delete;
	public:
		OctetsMarshalStreamSource(Octets& src);
		virtual ~OctetsMarshalStreamSource();
	public:
		virtual void pushbytes(const void*, size_t) override;
	};

	class LIMAX_DLL_EXPORT_API OctetsUnmarshalStreamSource : public UnmarshalStreamSource
	{
		enum { MAXSPARE = 16384 };

		mutable size_t pos;
		mutable size_t tranpos;

		const Octets&	data;

		OctetsUnmarshalStreamSource() = delete;
		OctetsUnmarshalStreamSource(const OctetsUnmarshalStreamSource&) = delete;
		OctetsUnmarshalStreamSource& operator=(const OctetsUnmarshalStreamSource&) = delete;
	public:
		OctetsUnmarshalStreamSource(const Octets& src);
		virtual ~OctetsUnmarshalStreamSource();
	public:
		virtual const void* pop(size_t) const override;
		virtual void transaction(Transaction) const override;
		virtual bool eos() const override;
	public:
		size_t position() const;
	};

	class LIMAX_DLL_EXPORT_API OctetsMarshalStream : public MarshalStream
	{
		Octets		data;
		OctetsMarshalStreamSource	source;

		OctetsMarshalStream(const OctetsMarshalStream&) = delete;
		OctetsMarshalStream& operator=(const OctetsMarshalStream&) = delete;
	public:
		OctetsMarshalStream();
		~OctetsMarshalStream();
	public:
		Octets getOctets() const;
	};

	namespace helper { struct FileIO; }
	class LIMAX_DLL_EXPORT_API FileMarshalStreamSource : public MarshalStreamSource
	{
		helper::FileIO* file;
		FileMarshalStreamSource() = delete;
		FileMarshalStreamSource(const FileMarshalStreamSource&) = delete;
		FileMarshalStreamSource& operator=(const FileMarshalStreamSource&) = delete;
	public:
		FileMarshalStreamSource(const std::string&);
		virtual ~FileMarshalStreamSource();
	public:
		virtual void pushbytes(const void*, size_t) override;
	};
	class LIMAX_DLL_EXPORT_API FileUnmarshalStreamSource : public UnmarshalStreamSource
	{
		mutable size_t tranpos;
		mutable Octets buffer;
		helper::FileIO* file;
		FileUnmarshalStreamSource() = delete;
		FileUnmarshalStreamSource(const FileUnmarshalStreamSource&) = delete;
		FileUnmarshalStreamSource& operator=(const FileUnmarshalStreamSource&) = delete;
	public:
		FileUnmarshalStreamSource(const std::string&);
		virtual ~FileUnmarshalStreamSource();
	public:
		virtual const void* pop(size_t) const override;
		virtual void transaction(Transaction) const override;
		virtual bool eos() const override;
	};

} // namespace limax {
