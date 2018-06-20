#pragma once

namespace limax {
	namespace http {
		struct LIMAX_DLL_EXPORT_API HttpConnection
		{
			enum : int
			{
				HTTP_WEBSOCKET = 101,
				HTTP_OK = 200,
				HTTP_CREATED = 201,
				HTTP_ACCEPTED = 202,
				HTTP_NOT_AUTHORITATIVE = 203,
				HTTP_NO_CONTENT = 204,
				HTTP_RESET = 205,
				HTTP_PARTIAL = 206,
				HTTP_MULT_CHOICE = 300,
				HTTP_MOVED_PERM = 301,
				HTTP_MOVED_TEMP = 302,
				HTTP_SEE_OTHER = 303,
				HTTP_NOT_MODIFIED = 304,
				HTTP_USE_PROXY = 305,
				HTTP_BAD_REQUEST = 400,
				HTTP_UNAUTHORIZED = 401,
				HTTP_PAYMENT_REQUIRED = 402,
				HTTP_FORBIDDEN = 403,
				HTTP_NOT_FOUND = 404,
				HTTP_BAD_METHOD = 405,
				HTTP_NOT_ACCEPTABLE = 406,
				HTTP_PROXY_AUTH = 407,
				HTTP_CLIENT_TIMEOUT = 408,
				HTTP_CONFLICT = 409,
				HTTP_GONE = 410,
				HTTP_LENGTH_REQUIRED = 411,
				HTTP_PRECON_FAILED = 412,
				HTTP_ENTITY_TOO_LARGE = 413,
				HTTP_REQ_TOO_LONG = 414,
				HTTP_UNSUPPORTED_TYPE = 415,
				HTTP_INTERNAL_ERROR = 500,
				HTTP_NOT_IMPLEMENTED = 501,
				HTTP_BAD_GATEWAY = 502,
				HTTP_UNAVAILABLE = 503,
				HTTP_GATEWAY_TIMEOUT = 504,
				HTTP_VERSION_ = 505,
			};
			struct LIMAX_DLL_EXPORT_API strless
			{
				bool operator()(const std::string& l, const std::string& r) const;
			};
			typedef std::map<std::string, std::list<std::string>, strless> PropertyMap;
			typedef std::function<limax::Octets()> GetContentFuncType;
			HttpConnection();
			virtual ~HttpConnection();
			virtual void setRequestMethod(const std::string&) = 0;
			virtual const std::string& getRequestMethod() const = 0;
			virtual void addRequestProperty(const std::string&, const std::string&) = 0;
			virtual void setRequestProperty(const std::string&, const std::string&) = 0;
			virtual void setOutputData(const void*, size_t) = 0;
			virtual const std::string& getRequestProperty(const std::string&) const = 0;
			virtual const PropertyMap& getRequestProperties() const = 0;
			virtual bool doRequest() = 0;
			virtual int getResponseCode() const = 0;
			virtual const std::string& getResponseMessage() const = 0;
			virtual GetContentFuncType getContent() const = 0;
			virtual size_t getContentLength() const = 0;
			virtual const std::string& getContentType() const = 0;
			virtual	const std::string& getHeaderField(const std::string&) const = 0;
			virtual const PropertyMap& getHeaderFields() const = 0;
		};
		struct LIMAX_DLL_EXPORT_API HttpConnectionFactory
		{
			HttpConnectionFactory();
			virtual ~HttpConnectionFactory();
			virtual std::shared_ptr<HttpConnection> create(const std::string&) = 0;
			static std::shared_ptr<HttpConnectionFactory> create(int, int, bool cached = true);
			static std::shared_ptr<HttpConnectionFactory> create(const std::string&, short, int, int, bool cached = true);
		};
		LIMAX_DLL_EXPORT_API std::string httpDownload(const std::string& url, int timeout, size_t maxsize, const std::string& cacheDir, bool staleEnable);
	} // namespace http {
} // namespace limax {
