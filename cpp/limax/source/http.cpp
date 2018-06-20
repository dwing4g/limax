#include "common.h"
#include "httpclient.h"
#include "script.h"
namespace limax {
	namespace http {
		bool HttpConnection::strless::operator()(const std::string& l, const std::string& r) const
		{
			return http::util::stringcompare(l.c_str(), r.c_str()) < 0;
		}
		HttpConnection::HttpConnection() {}
		HttpConnection::~HttpConnection() {}
		HttpConnectionFactory::HttpConnectionFactory() {}
		HttpConnectionFactory::~HttpConnectionFactory() {}
		std::shared_ptr<HttpConnectionFactory> HttpConnectionFactory::create(int contimeout, int rwtimeout, bool cached)
		{
			return std::make_shared<http::ConnectionFactoryImpl>(contimeout, rwtimeout, cached);
		}
		std::shared_ptr<HttpConnectionFactory> HttpConnectionFactory::create(const std::string& s, short p, int contimeout, int rwtimeout, bool cached)
		{
			return std::make_shared<http::ProxyConnectionFactoryImpl>(s, p, contimeout, rwtimeout, cached);
		}
		namespace helper {
			static const char HEX_CHARS[] = "0123456789abcdef";
			inline static std::string toFileNameString(const std::string& s)
			{
				std::ostringstream oss;
				for (auto c : s)
				{
					if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))
						oss << c;
					else
						oss << '-' << HEX_CHARS[c >> 4 & 0xf] << HEX_CHARS[c & 0xf];
				}
				return oss.str();
			}
			inline static std::string toFullFileNameString(const std::string& cacheDir, const std::string& s)
			{
				if (cacheDir.back() == '/' || cacheDir.back() == '\\')
					return cacheDir + toFileNameString(s);
				else
					return cacheDir + "/" + toFileNameString(s);
			}
		} // namespace helper {
		std::string httpDownload(const std::string& url, int timeout, size_t maxsize, const std::string& cacheDir, bool staleEnable)
		{
			std::string etag;
			std::string content;
			if (!cacheDir.empty())
			{
				try
				{
					FileUnmarshalStreamSource	fuss(helper::toFullFileNameString(cacheDir, url));
					UnmarshalStream us(fuss);
					us >> etag >> content;
				}
				catch (MarshalException&)
				{
					etag.clear();
					content.clear();
				}
			}
			auto connection = HttpConnectionFactory::create(timeout, timeout)->create(url);
			if (!etag.empty())
				connection->setRequestProperty("If-None-Match", etag);
			if (!connection->doRequest())
			{
				if (Trace::isWarnEnabled())
				{
					std::ostringstream oss;
					oss << "httpDownload doRequest return false : url = " << url;
					Trace::warn(oss.str());
				}
				return staleEnable ? content : "";
			}
			const auto responseCode = connection->getResponseCode();
			if (responseCode == HttpConnection::HTTP_NOT_MODIFIED)
				return content;
			if (responseCode != HttpConnection::HTTP_OK)
				return staleEnable ? content : "";
			content.clear();
			auto getdata = connection->getContent();
			while (true)
			{
				auto pd = getdata();
				if (0 == pd.size())
					break;
				if (pd.size() + content.size() > maxsize)
				{
					if (Trace::isWarnEnabled())
					{
						std::ostringstream oss;
						oss << "httpDownload  size > maxsize : url = " << url << " maxsize = " << maxsize;
						Trace::warn(oss.str());
					}
					return "";
				}
				content.append((const char*)pd.begin(), pd.size());
			}
			if (!cacheDir.empty())
			{
				etag = connection->getRequestProperty("ETag");
				FileMarshalStreamSource	fmss(helper::toFullFileNameString(cacheDir, url));
				MarshalStream us(fmss);
				us << etag << content;
			}
			return content;
		}
	} // namespace http {
} // namespace limax {
