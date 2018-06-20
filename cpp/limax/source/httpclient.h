#pragma once

namespace limax {
	namespace http {

		struct util
		{
			static int stringcompare(const char*, const char*);
			static int stringncompare(const char*, const char*, size_t);
		};

		struct HttpCallback
		{
			HttpCallback() {}
			virtual ~HttpCallback() {}
			virtual bool onHttpRequestLine(char*, size_t) = 0;
			virtual bool onHttpHeaderField(char*, char*) = 0;
			virtual bool onHttpHeadersEnd() = 0;
			virtual bool onHttpRequestEnd(char*, size_t) = 0;
			virtual size_t onHttpContent(char*, size_t) = 0;
		};

		struct HttpBuffer
		{
			limax::Octets m_data;

			inline bool empty() const
			{
				return 0 == m_data.size();
			}
			inline size_t size() const
			{
				return m_data.size();
			}
			inline char* data()
			{
				return (char*)m_data.begin();
			}
			inline void erase(size_t pos, size_t size)
			{
				m_data.erase(pos, size);
			}
		};

		class HttpParser
		{
		public:
			enum BodyType : char
			{
				BODY_NOCONTENT = '1',
				BODY_CHUNKED = '2',
				BODY_LENGTH = '3',
				BODY_MULTIPART = '4',
				BODY_CONNECTION_CLOSE = '5',
			};
			enum ParseFlag : short
			{
				FLG_MULTIPART_END = 1
			};
		private:
			enum State : char
			{
				HTTP_REQUEST_LINE,
				HTTP_HEADER_FIELDS,
				HTTP_PARSE_CHUNKSIZE,
				HTTP_PROCESS_CONTENT,
				HTTP_FINISH_CHUNK,
				HTTP_PARSE_TRAILER,
				HTTP_NEEDMOREDATA,
				HTTP_PARSE_ERROR,
			};
			BodyType m_http_body_type;
			State m_http_parse_state;
			short m_parse_flag;
			size_t m_content_length;
			size_t m_chunk_size;
			size_t m_chunk_offset;
		public:
			inline HttpParser() { reset(); }
			inline ~HttpParser() {}
		public:
			inline int parse(HttpBuffer& buffer, HttpCallback& cb)
			{
				while (!buffer.empty())
				{
					auto n = HTTP_PARSE_ERROR;
					switch (m_http_parse_state)
					{
					case HTTP_PROCESS_CONTENT:
						n = http_process_content(buffer, cb);
						break;
					case HTTP_PARSE_CHUNKSIZE:
						n = http_parse_chunksize(buffer, cb);
						break;
					case HTTP_FINISH_CHUNK:
						n = http_finish_chunk(buffer, cb);
						break;
					case HTTP_REQUEST_LINE:
						n = http_request_line(buffer, cb);
						break;
					case HTTP_HEADER_FIELDS:
						n = http_header_fields(buffer, cb);
						break;
					case HTTP_PARSE_TRAILER:
						n = http_parse_trailer(buffer, cb);
						break;
					default:
						break;
					}
					if (n == HTTP_PARSE_ERROR)
						return -1;
					if (n == HTTP_NEEDMOREDATA)
						break;
					if (n != HTTP_REQUEST_LINE)
						m_http_parse_state = n;
					else if (n != m_http_parse_state)
						reset();
				}
				return 0;
			}
			inline bool length_detectable() const
			{
				return m_http_body_type < BODY_MULTIPART;
			}
			inline size_t content_length() const
			{
				return m_content_length;
			}
			inline BodyType body_type() const
			{
				return m_http_body_type;
			}
			inline void set_parse_flag(short c)
			{
				m_parse_flag |= c;
			}
			inline void reset()
			{
				m_http_parse_state = HTTP_REQUEST_LINE;
				m_http_body_type = BODY_NOCONTENT;
				m_content_length = 0;
				m_chunk_size = 0;
				m_chunk_offset = 0;
				m_parse_flag = 0;
			}
		private:
			inline static size_t find(const char * s, size_t l, char c = '\n')
			{
				if (auto f = (const char *)memchr(s, c, l))
					return f - s + 1;
				else
					return 0;
			}
			inline static size_t headersEnd(const char* mime, size_t l)
			{
				size_t e = 0;
				int state = 0;
				while (e < l && state < 3)
				{
					switch (state)
					{
					case 0:
						if ('\n' == mime[e])
							state = 1;
						break;
					case 1:
						if ('\r' == mime[e])
							state = 2;
						else if ('\n' == mime[e])
							state = 3;
						else
							state = 0;
						break;
					case 2:
						if ('\r' == mime[e])	/* ignore repeated CR */
							(void)0;
						else if ('\n' == mime[e])
							state = 3;
						else
							state = 0;
						break;
					default:
						break;
					}
					e++;
				}
				if (3 == state)
					return e;
				return 0;
			}
			inline State http_request_line(HttpBuffer& buffer, HttpCallback& cb)
			{
				auto data = buffer.data();
				auto n = find(data, buffer.size(), '\n');
				if (n == 0)
					return HTTP_NEEDMOREDATA;
				auto size = n;
				for (; size > 0 && isspace(*data); ++data, --size) {}
				buffer.erase(0, n - size);
				if (buffer.empty())
					return HTTP_NEEDMOREDATA;
				if (0 == size)
					return HTTP_REQUEST_LINE;
				if (!cb.onHttpRequestLine(buffer.data(), size))
					return HTTP_PARSE_ERROR;
				buffer.erase(0, size - 1);
				buffer.data()[0] = '\n';
				return HTTP_HEADER_FIELDS;
			}
			inline State http_header_fields(HttpBuffer& buffer, HttpCallback& cb)
			{
				auto n = headersEnd(buffer.data(), buffer.size());
				if (n == 0)
					return HTTP_NEEDMOREDATA;
				if (!parse_header_fields(buffer.data(), n, cb))
					return HTTP_PARSE_ERROR;
				buffer.erase(0, n);
				if (!cb.onHttpHeadersEnd())
					return HTTP_PARSE_ERROR;
				switch (body_type())
				{
				case BODY_CHUNKED:
					return HTTP_PARSE_CHUNKSIZE;
				case BODY_NOCONTENT:
					assert(end_of_content());
				case BODY_LENGTH:
					if (end_of_content())
					{
						if (!cb.onHttpRequestEnd((char*)"", 0))
							return HTTP_PARSE_ERROR;
						return HTTP_REQUEST_LINE;
					}
					m_chunk_size = content_length();
				default:
					return HTTP_PROCESS_CONTENT;
				}
			}
			inline bool parse_header_fields(char* header, size_t size, HttpCallback& cb)
			{
				for (; size > 0 && isspace(*header); ++header, --size) {}
				if (size == 0)
					return true;
				auto field = header;
				auto fieldsize = find(header, size, '\n');
				header += fieldsize;
				size -= fieldsize;
				while (size > 0)
				{
					auto n = find(header, size, '\n');
					if (isspace(*header))
						fieldsize += n;
					else
					{
						if (!parse_field(field, fieldsize, cb))
							return false;
						field = header;
						fieldsize = n;
					}
					header += n;
					size -= n;
				}
				return parse_field(field, fieldsize, cb);
			}
			inline bool parse_field(char* data, size_t size, HttpCallback& cb)
			{
				auto n = find(data, size, ':');
				if (n == 0)
					return true;
				data[n - 1] = 0;
				data[size - 1] = 0;
				auto name = data;
				auto value = name + n;
				for (; *value && isspace(*value); ++value);
				if (0 == util::stringcompare(name, "content-length"))
				{
					if (body_type() != BODY_CHUNKED)
						m_http_body_type = BODY_LENGTH;
					m_content_length = atoi(value);
					cb.onHttpHeaderField(name, value);
					return true;
				}
				else if (0 == util::stringcompare(name, "transfer-encoding"))
				{
					if (0 == util::stringncompare(value, "chunked", 7))
						m_http_body_type = BODY_CHUNKED;
					else
						return false;
					cb.onHttpHeaderField(name, value);
					return true;
				}
				if (0 == util::stringcompare(name, "connection"))
				{
					if (BODY_NOCONTENT == body_type() && 0 == util::stringncompare(value, "close", 5))
						m_http_body_type = BODY_CONNECTION_CLOSE;
				}
				else if (0 == util::stringcompare(name, "content-type"))
				{
					if ((BODY_NOCONTENT == body_type() || BODY_CONNECTION_CLOSE == body_type())
						&& 0 == util::stringncompare(value, "multipart", 9))
						m_http_body_type = BODY_MULTIPART;
				}
				return cb.onHttpHeaderField(name, value);
			}
			inline State http_parse_chunksize(HttpBuffer& buffer, HttpCallback& cb)
			{
				auto data = buffer.data() + m_chunk_offset;
				auto size = buffer.size() - m_chunk_offset;
				auto n = find(data, size, '\n');
				if (n == 0)
					return HTTP_NEEDMOREDATA;
				data[n - 1] = 0;
				char* endptr;
				size_t chunk_size = strtoul(data, &endptr, 16);
				if (chunk_size == 0)
				{
					m_chunk_size = n;
					return HTTP_PARSE_TRAILER;
				}
				buffer.erase(m_chunk_offset, n);
				m_chunk_size = chunk_size + m_chunk_offset;
				m_chunk_offset = 0;
				return HTTP_PROCESS_CONTENT;
			}
			inline State http_process_content(HttpBuffer& buffer, HttpCallback& cb)
			{
				auto cl = buffer.size();
				if (length_detectable() && m_chunk_size < cl)
					cl = m_chunk_size;
				auto n = cb.onHttpContent(buffer.data(), cl);
				if ((size_t)-1 == n)
					return HTTP_PARSE_ERROR;
				buffer.erase(0, n);
				if (length_detectable())
					m_chunk_size -= n;
				switch (body_type())
				{
				case BODY_CHUNKED:
					if (m_chunk_size == 0 || m_chunk_size < buffer.size())
					{
						m_chunk_offset = m_chunk_size;
						m_chunk_size = 0;
						return HTTP_FINISH_CHUNK;
					}
					break;
				case BODY_LENGTH:
					if (m_chunk_size <= buffer.size())
					{
						bool keep = cb.onHttpRequestEnd(buffer.data(), m_chunk_size);
						buffer.erase(0, m_chunk_size);
						return keep ? HTTP_REQUEST_LINE : HTTP_PARSE_ERROR;
					}
					break;
				case BODY_MULTIPART:
					if (is_multipart_end())
						return HTTP_REQUEST_LINE;
					break;
				case BODY_CONNECTION_CLOSE:
					break;
				default:
					assert(false);
				}
				return HTTP_NEEDMOREDATA;
			}
			inline State http_parse_trailer(HttpBuffer& buffer, HttpCallback& cb)
			{
				auto line = buffer.data() + m_chunk_size + m_chunk_offset;
				auto size = buffer.size() - m_chunk_offset - m_chunk_size;
				size_t n;
				while ((n = find(line, size, '\n')) > 0)
				{
					auto c = *line;
					m_chunk_size += n; line += n; size -= n;
					if (n == 1 || (n == 2 && c == '\r')) // empty line: '\n' or "\r\n"
					{
						auto keep = cb.onHttpRequestEnd(buffer.data(), m_chunk_offset);
						buffer.erase(0, m_chunk_offset + m_chunk_size);
						return keep ? HTTP_REQUEST_LINE : HTTP_PARSE_ERROR;
					}
				}
				return HTTP_NEEDMOREDATA;
			}
			inline State http_finish_chunk(HttpBuffer& buffer, HttpCallback& cb)
			{
				auto n = find(buffer.data() + m_chunk_offset, buffer.size() - m_chunk_offset, '\n');
				if (n == 0)
					return HTTP_NEEDMOREDATA;
				buffer.erase(m_chunk_offset, n);
				return HTTP_PARSE_CHUNKSIZE;
			}
			inline bool is_multipart_end() const
			{
				return (m_parse_flag & FLG_MULTIPART_END);
			}
			inline bool end_of_content() const
			{
				return content_length() == 0;
			}
		};

	} // namespace http {

	namespace http {

		struct ConnectionPool;
		class ConnectionPoolMap;

		bool ParseUrl(const std::string&, std::string&, std::string&, std::string&);

		class ConnectionFactoryImpl : public HttpConnectionFactory
		{
			ConnectionPoolMap*	map;
			int		contimeout;
			int		rwtimeout;
 		public:
			ConnectionFactoryImpl(int, int, bool);
			virtual ~ConnectionFactoryImpl();
		public:
			virtual std::shared_ptr<HttpConnection> create(const std::string&) override;
		};

		class ProxyConnectionFactoryImpl : public HttpConnectionFactory
		{
			std::shared_ptr<ConnectionPool> cpool;
		public:
			ProxyConnectionFactoryImpl(const std::string&, short, int, int, bool);
			virtual ~ProxyConnectionFactoryImpl();
		public:
			virtual std::shared_ptr<HttpConnection> create(const std::string&) override;
		};

	} // namespace http {
} // namespace limax {
