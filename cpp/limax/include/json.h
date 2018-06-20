#pragma once

namespace limax {
	namespace json_impl {
		struct LIMAX_DLL_EXPORT_API JSONException
		{
			std::string message;
			JSONException(int32_t _line);
		};

		class JSONBuilder;
		class LIMAX_DLL_EXPORT_API JSONMarshal
		{
		public:
			virtual ~JSONMarshal() {}
			virtual JSONBuilder& marshal(JSONBuilder &jb) const = 0;
		};

		class LIMAX_DLL_EXPORT_API JSONBuilder
		{
			friend class JSON;
			std::string sb;
			void _append(char c);
			void _append(int8_t v);
			void _append(uint8_t v);
			void _append(int16_t v);
			void _append(uint16_t v);
			void _append(int32_t v);
			void _append(uint32_t v);
			void _append(int64_t v);
			void _append(uint64_t v);
			void _append(float v);
			void _append(double v);
			void _append(bool v);
			void _append(const std::string& v);
			template<typename II>
			void _append(II it, II ie, char c0, char c1)
			{
				const char* comma = "";
				sb.push_back(c0);
				for (; it != ie; ++it)
				{
					sb.append(comma);
					append(*it);
					comma = ",";
				}
				sb.push_back(c1);
			}
			void _append(const char *p);
		public:
			JSONBuilder& append(const JSONMarshal& v);
			JSONBuilder& append(int8_t v);
			JSONBuilder& append(uint8_t v);
			JSONBuilder& append(int16_t v);
			JSONBuilder& append(uint16_t v);
			JSONBuilder& append(int32_t v);
			JSONBuilder& append(uint32_t v);
			JSONBuilder& append(int64_t v);
			JSONBuilder& append(uint64_t v);
			JSONBuilder& append(float v);
			JSONBuilder& append(double v);
			JSONBuilder& append(bool v);
			JSONBuilder& append(char v);
			JSONBuilder& append(const std::string& v);
			JSONBuilder& append(const char *v);
			template<typename T, typename A>
			JSONBuilder& append(const std::vector<T, A>& v)
			{
				_append(v.begin(), v.end(), '[', ']');
				return *this;
			}
			template<typename T, typename A>
			JSONBuilder& append(const std::list<T, A>& v)
			{
				_append(v.begin(), v.end(), '[', ']');
				return *this;
			}
			template<typename T, typename A>
			JSONBuilder& append(const std::deque<T, A>& v)
			{
				_append(v.begin(), v.end(), '[', ']');
				return *this;
			}
			template<typename K, typename P, typename A>
			JSONBuilder& append(const std::set<K, P, A> & v)
			{
				_append(v.begin(), v.end(), '[', ']');
				return *this;
			}
			template<typename K, typename H, typename E, typename A>
			JSONBuilder& append(const std::unordered_set<K, H, E, A>& v)
			{
				_append(v.begin(), v.end(), '[', ']');
				return *this;
			}
			template<typename K, typename T, typename P, typename A>
			JSONBuilder& append(const std::map<K, T, P, A>& v)
			{
				_append(v.begin(), v.end(), '{', '}');
				return *this;
			}
			template<typename K, typename V, typename H, typename E, typename A>
			JSONBuilder& append(const std::unordered_map<K, V, H, E, A>& v)
			{
				_append(v.begin(), v.end(), '{', '}');
				return *this;
			}
			template<typename T1, typename T2>
			JSONBuilder& append(const std::pair<T1, T2>& v)
			{
				sb.push_back('"');
				_append(v.first);
				sb.append("\":");
				append(v.second);
				return *this;
			}
			JSONBuilder& begin();
			JSONBuilder& end();
			JSONBuilder& comma();
			JSONBuilder& colon();
			JSONBuilder& null();
			std::string toString() const;
		};

		class LIMAX_DLL_EXPORT_API JSON : public JSONMarshal
		{
			friend class JSONDecoder;
			struct LIMAX_DLL_EXPORT_API _Object{
				virtual ~_Object(){}
			};
		public:
			typedef std::shared_ptr<_Object> Object;
		private:
			struct _String : public _Object, public std::string
			{
				_String() {}
				_String(const char *p) : std::string(p) {}
				_String(const std::string& p) : std::string(p) {}
			};
			struct _Number : public _String {};
			typedef std::shared_ptr<_String> String;
			typedef std::shared_ptr<_Number> Number;
			struct StringHash
			{
				size_t operator()(const String& a) const { return std::hash<std::string>()(*a); }
			};
			struct StringEqual
			{
				bool operator()(const String& a, const String& b) const { return *a == *b; }
			};
			struct _Map : public _Object, public std::unordered_map<String, Object, StringHash, StringEqual>
			{
			};
			typedef std::shared_ptr<_Map> Map;
			class _List : public _Object, public std::vector <Object> {};
			typedef std::shared_ptr<_List> List;
			Object data;
		public:
			static const Object Undefined;
			static const Object Null;
			static const Object True;
			static const Object False;
			JSON(Object _data) : data(_data){}
		private:
			template<class R>
			static R cast(Object data)
			{
				return std::dynamic_pointer_cast<typename R::element_type>(data);
			}
			template<class R> 
			R cast() const
			{
				if (R r = cast<R>(data))
					return r;
				throw JSONException(__LINE__);
			}
			std::string make_string(const char *p) const;
			bool tryLong(std::string s, int64_t& r) const;
			bool tryDouble(std::string s, double& r) const;
			static JSONBuilder& marshal(JSONBuilder &jb, Object data);
		public:
			std::shared_ptr<JSON> get(const std::string& key) const;
			std::vector<std::string> keySet() const;
			std::shared_ptr<JSON> get(size_t index) const;
			std::vector<std::shared_ptr<JSON>> toArray() const;
			std::string toString() const;
			bool booleanValue() const;
			int32_t intValue() const;
			int64_t longValue() const;
			double doubleValue() const;
			bool isUndefined() const;
			bool isNull() const;
			bool isBoolean() const;
			bool isString() const;
			bool isNumber() const;
			bool isObject() const;
			bool isArray() const;
			JSONBuilder& marshal(JSONBuilder &jb) const;
			static std::shared_ptr<JSON> parse(const std::string& text);
			template<typename T>
			static std::string stringify(const T& obj)
			{
				return JSONBuilder().append(obj).toString();
			}
			static std::string stringify(const char* obj);
			static std::string stringify(std::shared_ptr<JSON> obj);
		};

		typedef std::function<void(std::shared_ptr<JSON>)> JSONConsumer;

		class LIMAX_DLL_EXPORT_API JSONDecoder
		{
			friend class JSON;
		private:
			typedef JSON::Object Object;
			typedef JSON::Map Map;
			typedef JSON::List List;
			typedef JSON::String String;
			typedef JSON::Number Number;
			struct _JSONValue
			{
				virtual ~_JSONValue() {}
				virtual bool accept(char c) = 0;
				virtual void reduce(Object v) {}
			};
			struct _JSONRoot : public _JSONValue
			{
				JSONDecoder &decoder;
				_JSONRoot(JSONDecoder& _decoder);
				bool accept(char c) override;
				void reduce(Object v) override;
			};
			typedef std::shared_ptr<_JSONRoot> JSONRoot;
			typedef std::shared_ptr<_JSONValue> JSONValue;
			struct _JSONObject : public _JSONValue
			{
				JSONDecoder &decoder;
				JSONValue parent;
				Map map;
				String key;
				int stage = 0;
				_JSONObject(JSONDecoder& _decoder);
				bool accept(char c) override;
				void reduce(Object v) override;
			};
			struct _JSONArray : public _JSONValue
			{
				JSONDecoder &decoder;
				JSONValue parent;
				List list;
				int stage = 0;
				_JSONArray(JSONDecoder& _decoder);
				bool accept(char c) override;
				void reduce(Object v) override;
			};
			struct _JSONString : public _JSONValue
			{
				JSONDecoder &decoder;
				JSONValue parent;
				String sb;
				int stage = 0;
				int cp = 0;
				_JSONString(JSONDecoder& _decoder);
				bool accept(char c) override;
			};
			struct _JSONNumber : public _JSONValue
			{
				JSONDecoder &decoder;
				JSONValue parent;
				Number sb;
				_JSONNumber(JSONDecoder& _decoder);
				bool accept(char c) override;
			};
			struct _JSONConst : public _JSONValue
			{
				JSONDecoder& decoder;
				JSONValue parent;
				std::string match;
				Object value;
				int stage = 0;
				_JSONConst(JSONDecoder& _decoder, std::string _match, Object _value);
				bool accept(char c) override;
			};
			typedef std::shared_ptr<_JSONObject> JSONObject;
			typedef std::shared_ptr<_JSONArray> JSONArray;
			typedef std::shared_ptr<_JSONString> JSONString;
			typedef std::shared_ptr<_JSONNumber> JSONNumber;
			typedef std::shared_ptr<_JSONConst> JSONConst;
			JSONConsumer consumer;
			JSONRoot root;
			JSONValue current;
			JSONValue change;
			std::shared_ptr<JSON> json;
			void flush();
		public:
			JSONDecoder(JSONConsumer _consumer);
			JSONDecoder();
			void accept(char c);
			std::shared_ptr<JSON> get();
		};
	}
	typedef json_impl::JSONException JSONException;
	typedef json_impl::JSONBuilder JSONBuilder;
	typedef json_impl::JSONMarshal JSONMarshal;
	typedef json_impl::JSONDecoder JSONDecoder;
	typedef json_impl::JSONConsumer JSONConsumer;
	typedef json_impl::JSON JSON;
} // namespace limax {
