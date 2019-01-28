#include "common.h"

namespace limax {
	namespace json_impl {
		JSONException::JSONException(int32_t _line)
			: message("JSONException __LINE__ = " + std::to_string(_line))
		{}

#define THROW_JSON_EXCEPTION throw JSONException(__LINE__)

		void JSONBuilder::_append(char c)
		{
			switch (c)
			{
			case '"':
				sb.append("\\\"");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			case '\b':
				sb.append("\\b");
				break;
			case '\f':
				sb.append("\\f");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			default:
				if (c < ' ') {
					sb.append("\\u00");
					sb.push_back((c >> 4) + '0');
					c &= 15;
					if (c < 10)
						sb.push_back(c + '0');
					else
						sb.push_back(c - 10 + 'a');
				}
				else
					sb.push_back(c);
			}
		}

		void JSONBuilder::_append(int8_t v)
		{
			sb.append(std::to_string(v));
		}

		void JSONBuilder::_append(uint8_t v)
		{
			sb.append(std::to_string(v));
		}

		void JSONBuilder::_append(int16_t v)
		{
			sb.append(std::to_string(v));
		}

		void JSONBuilder::_append(uint16_t v)
		{
			sb.append(std::to_string(v));
		}

		void JSONBuilder::_append(int32_t v)
		{
			sb.append(std::to_string(v));
		}

		void JSONBuilder::_append(uint32_t v)
		{
			sb.append(std::to_string(v));
		}

		void JSONBuilder::_append(int64_t v)
		{
			sb.append(std::to_string(v));
		}

		void JSONBuilder::_append(uint64_t v)
		{
			sb.append(std::to_string(v));
		}

		void JSONBuilder::_append(float v)
		{
			sb.append(std::to_string(v));
		}

		void JSONBuilder::_append(double v)
		{
			sb.append(std::to_string(v));
		}

		void JSONBuilder::_append(bool v)
		{
			_append(v ? "true" : "false");
		}

		void JSONBuilder::_append(const std::string& v)
		{
			for (auto c : v)
				_append(c);
		}

		void JSONBuilder::_append(const char *p)
		{
			sb.append(p);
		}

		JSONBuilder& JSONBuilder::append(const JSONMarshal& v)
		{
			return v.marshal(*this);
		}

		JSONBuilder& JSONBuilder::append(int8_t v)
		{
			_append(v);
			return *this;
		}

		JSONBuilder& JSONBuilder::append(uint8_t v)
		{
			_append(v);
			return *this;
		}

		JSONBuilder& JSONBuilder::append(int16_t v)
		{
			_append(v);
			return *this;
		}

		JSONBuilder& JSONBuilder::append(uint16_t v)
		{
			_append(v);
			return *this;
		}

		JSONBuilder& JSONBuilder::append(int32_t v)
		{
			_append(v);
			return *this;
		}

		JSONBuilder& JSONBuilder::append(uint32_t v)
		{
			_append(v);
			return *this;
		}

		JSONBuilder& JSONBuilder::append(int64_t v)
		{
			_append(v);
			return *this;
		}

		JSONBuilder& JSONBuilder::append(uint64_t v)
		{
			_append(v);
			return *this;
		}

		JSONBuilder& JSONBuilder::append(float v)
		{
			_append(v);
			return *this;
		}

		JSONBuilder& JSONBuilder::append(double v)
		{
			_append(v);
			return *this;
		}

		JSONBuilder& JSONBuilder::append(bool v)
		{
			_append(v);
			return *this;
		}

		JSONBuilder& JSONBuilder::append(char v)
		{
			sb.push_back('"');
			_append(v);
			sb.push_back('"');
			return *this;
		}

		JSONBuilder& JSONBuilder::append(const std::string& v)
		{
			sb.push_back('"');
			_append(v);
			sb.push_back('"');
			return *this;
		}

		JSONBuilder& JSONBuilder::append(const char *v)
		{
			return append(std::string(v));
		}

		JSONBuilder& JSONBuilder::begin()
		{
			sb.push_back('{');
			return *this;
		}

		JSONBuilder& JSONBuilder::end()
		{
			if (sb.back() == ',')
				sb.pop_back();
			sb.push_back('}');
			return *this;
		}

		JSONBuilder& JSONBuilder::comma()
		{
			sb.push_back(',');
			return *this;
		}

		JSONBuilder& JSONBuilder::colon()
		{
			sb.push_back(':');
			return *this;
		}

		JSONBuilder& JSONBuilder::null()
		{
			sb.append("null");
			return *this;
		}

		std::string JSONBuilder::toString() const
		{
			return sb;
		}

		const JSON::Object JSON::Undefined(new JSON::_Object());
		const JSON::Object JSON::Null(new JSON::_Object());
		const JSON::Object JSON::True(new JSON::_Object());
		const JSON::Object JSON::False(new JSON::_Object());

		std::string JSON::make_string(const char *p) const
		{
			std::string s;
			size_t l = strlen(p);
			for (size_t i = 0; i < l; i++)
				s.push_back(p[i]);
			return s;
		}

		bool JSON::tryLong(std::string s, int64_t& r) const
		{
			try
			{
				size_t idx;
				r = std::stoll(s, &idx);
				if (idx == s.length())
					return true;
			}
			catch (...){}
			return false;
		}

		bool JSON::tryDouble(std::string s, double& r) const
		{
			try
			{
				size_t idx;
				r = std::stod(s, &idx);
				if (idx == s.length())
					return true;
			}
			catch (...){}
			return false;
		}

		JSONBuilder& JSON::marshal(JSONBuilder &jb, Object data)
		{
			if (data == Undefined)
				THROW_JSON_EXCEPTION;
			else if (data == Null)
				jb._append("null");
			else if (data == True)
				jb._append("true");
			else if (data == False)
				jb._append("false");
			else if (Number n = cast<Number>(data))
				jb._append(*n);
			else if (String s = cast<String>(data))
				jb.append(*s);
			else if (List list = cast<List>(data))
			{
				const char *comma = "";
				jb._append("[");
				for (auto o : *list)
				{
					jb._append(comma);
					marshal(jb, o);
					comma = ",";
				}
				jb._append("]");
			}
			else if (Map map = cast<Map>(data))
			{
				const char *comma = "";
				jb._append("{");
				for (auto e : *map)
				{
					jb._append(comma);
					jb._append("\"");
					jb._append(*e.first);
					jb._append("\":");
					marshal(jb, e.second);
					comma = ",";
				}
				jb._append("}");
			}
			return jb;
		}

		std::shared_ptr<JSON> JSON::get(const std::string& key) const
		{
			Map map = cast<Map>();
			auto it = map->find(std::make_shared<typename String::element_type>(key));
			return std::shared_ptr<JSON>(new JSON(it == map->end() ? Undefined : (*it).second));
		}

		std::vector<std::string> JSON::keySet() const
		{
			Map map = cast<Map>();
			std::vector<std::string> r;
			for (auto e : *map)
				r.push_back(*e.first);
			return r;
		}

		std::shared_ptr<JSON> JSON::get(size_t index) const
		{
			List list = cast<List>();
			return std::shared_ptr<JSON>(new JSON(index >= list->size() ? Undefined : (*list)[index]));
		}

		std::vector<std::shared_ptr<JSON>> JSON::toArray() const
		{
			List list = cast<List>();
			std::vector<std::shared_ptr<JSON>> r;
			for (auto e : *list)
				r.push_back(std::shared_ptr<JSON>(new JSON(e)));
			return r;
		}

		std::string JSON::toString() const
		{
			if (data == Undefined)
				return make_string("undefined");
			if (data == Null)
				return make_string("null");
			if (data == True)
				return make_string("true");
			if (data == False)
				return make_string("false");
			if (std::dynamic_pointer_cast<typename Map::element_type>(data))
				return make_string("<Object>");
			if (std::dynamic_pointer_cast<typename List::element_type>(data))
				return make_string("<Array>");
			return *std::dynamic_pointer_cast<typename String::element_type>(data);
		}

		bool JSON::booleanValue() const
		{
			if (data == True) return true;
			if (data == False || data == Null || data == Undefined) return false;
			try
			{
				std::string& s = *cast<String>();
				if (s.length() == 0)
					return false;
				int64_t lv;
				if (tryLong(s, lv))
					return lv != 0L;
				double dv;
				if (tryDouble(s, dv))
					return dv != 0.0;
			}
			catch (JSONException){}
			return true;
		}

		int32_t JSON::intValue() const
		{
			return (int32_t)doubleValue();
		}

		int64_t JSON::longValue() const
		{
			if (data == True) return 1L;
			if (data == False || data == Null) return 0L;
			std::string&s = *cast<String>();
			if (s.length() > 0)
			{
				int64_t lv;
				if (tryLong(s, lv))
					return lv;
				double dv;
				if (tryDouble(s, dv))
					return (int64_t)dv;
			}
			THROW_JSON_EXCEPTION;
		}

		double JSON::doubleValue() const
		{
			if (data == True) return 1;
			if (data == False || data == Null) return 0;
			std::string&s = *cast<String>();
			if (s.length() > 0)
			{
				double dv;
				if (tryDouble(s, dv))
					return dv;
			}
			THROW_JSON_EXCEPTION;
		}

		bool JSON::isUndefined() const
		{
			return data == Undefined;
		}

		bool JSON::isNull() const
		{
			return data == Null;
		}

		bool JSON::isBoolean() const
		{
			return data == True || data == False;
		}

		bool JSON::isString() const
		{
			try
			{
				cast<String>();
				return !isNumber();
			}
			catch (JSONException) {}
			return false;
		}

		bool JSON::isNumber() const
		{
			try
			{
				cast<Number>();
				return true;
			}
			catch (JSONException) {}
			return false;
		}

		bool JSON::isObject() const
		{
			try
			{
				cast<Map>();
				return true;
			}
			catch (JSONException) {}
			return false;
		}

		bool JSON::isArray() const
		{
			try
			{
				cast<List>();
				return true;
			}
			catch (JSONException) {}
			return false;
		}

		JSONBuilder& JSON::marshal(JSONBuilder &jb) const
		{
			return marshal(jb, data);
		}

		std::shared_ptr<JSON> JSON::parse(const std::string& text)
		{
			JSONDecoder decoder;
			for (auto c : text)
				decoder.accept(c);
			decoder.flush();
			return decoder.get();
		}

		std::string JSON::stringify(const char* obj)
		{
			return JSONBuilder().append(std::string(obj)).toString();
		}

		std::string JSON::stringify(std::shared_ptr<JSON> obj)
		{
			return stringify(*obj);
		}

		JSONDecoder::JSONDecoder(JSONConsumer _consumer)
			: consumer(_consumer), root(std::shared_ptr<_JSONRoot>(new _JSONRoot(*this))), current(root)
		{}

		JSONDecoder::JSONDecoder()
			: root(std::shared_ptr<_JSONRoot>(new _JSONRoot(*this))), current(root)
		{}

		JSONDecoder::_JSONRoot::_JSONRoot(JSONDecoder& _decoder)
			: decoder(_decoder)
		{}

		bool JSONDecoder::_JSONRoot::accept(char c)
		{
			if (isspace(c))
				return true;
			if (decoder.json)
				THROW_JSON_EXCEPTION;
			return false;
		}

		void JSONDecoder::_JSONRoot::reduce(Object v)
		{
			if (decoder.consumer)
				decoder.consumer(std::make_shared<JSON>(v));
			else
				decoder.json = std::make_shared<JSON>(v);
		}

		JSONDecoder::_JSONObject::_JSONObject(JSONDecoder& _decoder)
			: decoder(_decoder), parent(_decoder.current), map(std::make_shared<typename Map::element_type>())
		{}

		bool JSONDecoder::_JSONObject::accept(char c)
		{
			switch (stage)
			{
			case 0:
				stage = 1;
				return true;
			case 1:
				if (isspace(c))
					return true;
				if (c == '}')
				{
					(decoder.change = parent)->reduce(map);
					return true;
				}
				return false;
			case 2:
				if (isspace(c))
					return true;
				if (c == ':' || c == '=')
				{
					stage = 3;
					return true;
				}
				THROW_JSON_EXCEPTION;
			case 4:
				if (isspace(c))
					return true;
				if (c == ',' || c == ';')
				{
					stage = 1;
					return true;
				}
				if (c == '}')
				{
					(decoder.change = parent)->reduce(map);
					return true;
				}
				THROW_JSON_EXCEPTION;
			}
			return isspace(c) ? true : false;
		}

		void JSONDecoder::_JSONObject::reduce(Object v) {
			if (stage == 1) {
				key = std::dynamic_pointer_cast<typename String::element_type>(v);
				if (!key)
					THROW_JSON_EXCEPTION;
				stage = 2;
			}
			else {
				map->insert(std::make_pair(key, v));
				stage = 4;
			}
		}

		JSONDecoder::_JSONArray::_JSONArray(JSONDecoder& _decoder)
			: decoder(_decoder), parent(_decoder.current), list(std::make_shared<typename List::element_type>())
		{}

		bool JSONDecoder::_JSONArray::accept(char c)
		{
			switch (stage)
			{
			case 0:
				stage = 1;
				return true;
			case 1:
				if (isspace(c))
					return true;
				if (c == ']')
				{
					(decoder.change = parent)->reduce(list);
					return true;
				}
				return false;
			default:
				if (isspace(c))
					return true;
				if (c == ',' || c == ';')
				{
					stage = 1;
					return true;
				}
				if (c == ']')
				{
					(decoder.change = parent)->reduce(list);
					return true;
				}
				THROW_JSON_EXCEPTION;
			}
		}

		void JSONDecoder::_JSONArray::reduce(Object v)
		{
			list->push_back(v);
			stage = 2;
		}

		JSONDecoder::_JSONString::_JSONString(JSONDecoder& _decoder)
			: decoder(_decoder), parent(_decoder.current), sb(std::make_shared<typename String::element_type>())
		{}

		static int hex(char c)
		{
			if (c >= '0' && c <= '9')
				return c - '0';
			if (c >= 'A' && c <= 'F')
				return c - 'A' + 10;
			if (c >= 'a' && c <= 'f')
				return c - 'a' + 10;
			THROW_JSON_EXCEPTION;
		}

		bool JSONDecoder::_JSONString::accept(char c)
		{
			if (stage < 0)
			{
				stage = (stage << 4) | hex(c);
				if ((stage & 0xffff0000) == 0xfff00000)
				{
					stage &= 0xffff;
					if (cp) {
						cp = (((cp - 0xd800) << 10) | (stage - 0xdc00)) + 0x10000;
						sb->push_back((char)((cp >> 18) | 0xf0));
						sb->push_back((char)(((cp >> 12) & 0x3f) | 0x80));
						sb->push_back((char)(((cp >> 6) & 0x3f) | 0x80));
						sb->push_back((char)((cp & 0x3f) | 0x80));
						cp = 0;
						stage = 0x40000000;
					}
					else {
						if (stage < 0x80) {
							sb->push_back((char)stage);
							stage = 0x40000000;
						}
						else if (stage >= 0x80 && stage <= 0x7ff) {
							sb->push_back((char)(((stage >> 6) & 0x1f) | 0xc0));
							sb->push_back((char)((stage & 0x3f) | 0x80));
							stage = 0x40000000;
						}
						else	 if (stage >= 0xd800 && stage <= 0xdbff)
						{
							cp = stage;
							stage = 0x10000000;
						}
						else {
							sb->push_back((char)(((stage >> 12) & 0x0f) | 0xe0));
							sb->push_back((char)(((stage >> 6) & 0x3f) | 0x80));
							sb->push_back((char)((stage & 0x3f) | 0x80));
							stage = 0x40000000;
						}
					}
				}
			}
			else if (stage & 0x20000000)
			{
				switch (c)
				{
				case '"':
				case '\\':
				case '/':
					sb->push_back(c);
					break;
				case 'b':
					sb->push_back('\b');
					break;
				case 'f':
					sb->push_back('\f');
					break;
				case 'n':
					sb->push_back('\n');
					break;
				case 'r':
					sb->push_back('\r');
					break;
				case 't':
					sb->push_back('\t');
					break;
				case 'u':
					stage = -16;
					break;
				default:
					THROW_JSON_EXCEPTION;
				}
				stage &= ~0x20000000;
			}
			else if (stage & 0x10000000)
			{
				if (c != '\\')
					THROW_JSON_EXCEPTION;
				stage = (stage & ~0x10000000) | 0x8000000;
			}
			else if (stage & 0x8000000)
			{
				if (c != 'u')
					THROW_JSON_EXCEPTION;
				stage = -16;
			}
			else if (c == '"')
			{
				if (stage & 0x40000000)
					(decoder.change = parent)->reduce(sb);
				stage = 0x40000000;
			}
			else if (c == '\\')
				stage |= 0x20000000;
			else
				sb->push_back(c);
			return true;
		}

		JSONDecoder::_JSONNumber::_JSONNumber(JSONDecoder& _decoder)
			: decoder(_decoder), parent(_decoder.current), sb(std::make_shared<typename Number::element_type>())
		{}

		bool JSONDecoder::_JSONNumber::accept(char c)
		{
			switch (c)
			{
			case '+':
			case '-':
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
			case 'E':
			case 'e':
			case '.':
				sb->push_back(c);
				return true;
			}
			size_t idx;
			try{ std::stod(*sb, &idx); }
			catch (...){ THROW_JSON_EXCEPTION; }
			if (idx != sb->length())
				THROW_JSON_EXCEPTION;
			(decoder.change = parent)->reduce(sb);
			return parent->accept(c);
		}

		JSONDecoder::_JSONConst::_JSONConst(JSONDecoder& _decoder, std::string _match, Object _value)
			: decoder(_decoder), parent(_decoder.current), match(_match), value(_value)
		{}

		bool JSONDecoder::_JSONConst::accept(char c)
		{
			if (tolower(c) != match[stage++])
				THROW_JSON_EXCEPTION;
			if ((size_t)stage == match.size())
				(decoder.change = parent)->reduce(value);
			return true;
		}

		void JSONDecoder::accept(char c)
		{
			while (true)
			{
				bool accept = current->accept(c);
				if (change)
				{
					current = change;
					change.reset();
				}
				if (accept)
					break;
				switch (c)
				{
				case '{':
					current = std::make_shared<typename JSONObject::element_type>(*this);
					break;
				case '[':
					current = std::make_shared<typename JSONArray::element_type>(*this);
					break;
				case '"':
					current = std::make_shared<typename JSONString::element_type>(*this);
					break;
				case '-':
				case '0':
				case '1':
				case '2':
				case '3':
				case '4':
				case '5':
				case '6':
				case '7':
				case '8':
				case '9':
					current = std::make_shared<typename JSONNumber::element_type>(*this);
					break;
				case 't':
				case 'T':
					current = std::shared_ptr<_JSONConst>(new _JSONConst(*this, "true", JSON::True));
					break;
				case 'f':
				case 'F':
					current = std::shared_ptr<_JSONConst>(new _JSONConst(*this, "false", JSON::False));
					break;
				case 'n':
				case 'N':
					current = std::shared_ptr<_JSONConst>(new _JSONConst(*this, "null", JSON::Null));
					break;
				default:
					THROW_JSON_EXCEPTION;
				}
			}
		}

		void JSONDecoder::flush()
		{
			accept(' ');
		}

		std::shared_ptr<JSON> JSONDecoder::get()
		{
			return json;
		}

#undef THROW_JSON_EXCEPTION
	}
}