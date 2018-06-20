#pragma once

#include "osdefine.h"
#include "dlldefines.h"
#include "osincs.h"

#ifdef LIMAX_ANDROID_NEED_STL_ADDTION

namespace std {

	template <typename T> inline std::string to_string(T value)
	{
		std::ostringstream os ;
		os << value ;
		return os.str() ;
	}

	inline int stoi(const std::string& __str, size_t* __idx = 0, int __base = 10)
	{
		const char* p = __str.c_str();
		char* end;
		int x = (int)strtol(p, &end, __base);
		if (__idx != nullptr) {
			*__idx = static_cast<std::size_t>(end - p);
		}
		return x;
	}

	inline long stol(const std::string& __str, size_t* __idx = 0, int __base = 10)
	{
		const char* p = __str.c_str();
		char* end;
		long x = strtol(p, &end, __base);
		if (__idx != nullptr) {
			*__idx = static_cast<std::size_t>(end - p);
		}
		return x;
	}

	inline unsigned long stoul(const std::string& __str, size_t* __idx = 0, int __base = 10)
	{
		const char* p = __str.c_str();
		char* end;
		unsigned long x = strtoul(p, &end, __base);
		if (__idx != nullptr) {
			*__idx = static_cast<std::size_t>(end - p);
		}
		return x;
	}

	inline long long stoll(const std::string& __str, size_t* __idx = 0, int __base = 10)
	{
		const char* p = __str.c_str();
		char* end;
		long long x = strtoll(p, &end, __base);
		if (__idx != nullptr) {
			*__idx = static_cast<std::size_t>(end - p);
		}
		return x;
	}

	inline unsigned long long stoull(const std::string& __str, size_t* __idx = 0, int __base = 10)
	{
		const char* p = __str.c_str();
		char* end;
		unsigned long long x = strtoull(p, &end, __base);
		if (__idx != nullptr) {
			*__idx = static_cast<std::size_t>(end - p);
		}
		return x;
	}

	inline float stof (const std::string& __str, size_t* __idx = 0)
	{
		const char* p = __str.c_str();
		char* end;
		float x = strtof(p, &end);
		if (__idx != nullptr) {
			*__idx = static_cast<std::size_t>(end - p);
		}
		return x;
	}

	inline double stod (const std::string& __str, size_t* __idx = 0)
	{
		const char* p = __str.c_str();
		char* end;
		double x = strtod(p, &end);
		if (__idx != nullptr) {
			*__idx = static_cast<std::size_t>(end - p);
		}
		return x;
	}

}

#endif // #ifdef LIMAX_ANDROID_NEED_STL_ADDTION

namespace limax {

	union IPEndPoint
	{
		struct sockaddr_in ipv4;
		struct sockaddr_in6 ipv6;
		struct sockaddr addr;

		IPEndPoint()
		{
			memset(this, 0, sizeof(*this));
		}
		IPEndPoint(const std::string& ip, int port)
		{
			memset(this, 0, sizeof(*this));
			char buff[sizeof(struct in6_addr)];
			if (inet_pton(AF_INET, ip.c_str(), buff) == 1)
			{
				ipv4.sin_family = AF_INET;
				ipv4.sin_port = htons(port);
				memcpy(&ipv4.sin_addr, buff, sizeof(ipv4.sin_addr));
			}
			else if (inet_pton(AF_INET6, ip.c_str(), buff) == 1)
			{
				ipv6.sin6_family = AF_INET6;
				ipv6.sin6_port = htons(port);
				memcpy(&ipv6.sin6_addr, buff, sizeof(ipv6.sin6_addr));
			}
		}

		std::string toString() const
		{
			char buff[INET6_ADDRSTRLEN];
			uint16_t port;
			if (getFamily() == AF_INET)
			{
				inet_ntop(AF_INET, (void *)&ipv4.sin_addr, buff, sizeof(buff));
				port = ntohs(ipv4.sin_port);
			}
			else
			{
				inet_ntop(AF_INET6, (void*)&ipv6.sin6_addr, buff, sizeof(buff));
				port = ntohs(ipv6.sin6_port);
			}
			return std::string(buff) + "/" + std::to_string(port);
		}

		const void* getAddress(socklen_t& len) const
		{
			if (getFamily() == AF_INET)
			{
				len = sizeof(ipv4.sin_addr);
				return (const void *)&ipv4.sin_addr;
			}
			else
			{
				len = sizeof(ipv6.sin6_addr);
				return (const void *)&ipv6.sin6_addr;
			}
		}

		int getFamily() const
		{
			return addr.sa_family;
		}

		uint16_t getPort() const
		{
			return ntohs(getFamily() == AF_INET ? ipv4.sin_port : ipv6.sin6_port);
		}

		operator sockaddr*() const
		{
			return (sockaddr*)this;
		}

		size_t size() const
		{
			if (addr.sa_family == AF_INET)
				return sizeof(ipv4);
			else if (addr.sa_family == AF_INET6)
				return sizeof(ipv6);
			else
				return sizeof(addr);
		}
	};

} // namespace limax {

