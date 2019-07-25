#include "endpointinc.h"

#include "dh.h"
#include "xmlgeninc/xmlgen.h"
#include "endpointhelper.h"
#include "websocket.h"

namespace limax {

	WebSocketConnector::WebSocketConnector(std::shared_ptr<helper::LoginConfigImpl> _loginConfig, ScriptEngineHandlePtr _handle)
		: tcpclient(nullptr), loginConfig(_loginConfig), readyState(CONNECTING), datasrc(buffer)
		, handle(_handle), reportError(0), stage(0)
	{
	}
	void WebSocketConnector::process(int t, const std::string& p)
	{
		switch (handle->action(t, p))
		{
		case 2:
			Engine::execute(timer = DelayedRunnable([this](){ send(" "); }, 500, 100));
			break;
		case 3:
			close();
		}
	}

	void WebSocketConnector::onopen()
	{
	}

	void WebSocketConnector::onmessage(const std::string& message)
	{
		std::shared_ptr<WebSocketConnector> _this(self);
		if (isdigit(message[0]))
			reportError = atoi(message.c_str());
		else
			runOnUiThread([_this, message](){_this->process(1, message); });
	}

	void WebSocketConnector::onclose(CloseStatus status)
	{
		std::stringstream ss;
		ss << reportError;
		std::string message(ss.str());
		std::shared_ptr<WebSocketConnector> _this(self);
		runOnUiThread([_this, message](){ _this->process(2, message); });
	}

	void WebSocketConnector::onCreate(TcpClient *tcpclient)
	{
		this->tcpclient = tcpclient;
	}

	void WebSocketConnector::onOpen(const IPEndPoint& local, const IPEndPoint& peer)
	{
		std::stringstream request;
		request << "GET / HTTP/1.1\r\nConnection: Upgrade\r\nUpgrade: WebSocket\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: AQIDBAUGBwgJCgsMDQ4PEC==\r\nOrigin: null\r\n";
		dhcontext = createDHContext(DHGroup);
		const std::vector<unsigned char>& data = dhcontext->generateDHResponse();
		request << "X-Limax-Security: " << DHGroup << ';';
		Octets o = Base64Encode::transform(Octets(&data[0], data.size()));
		int8_t e = 0;
		o.insert(o.end(), &e, 1);
		request << (char *)o.begin() << "\r\n\r\n";
		auto r = request.str();
		tcpclient->send(r.c_str(), (int32_t)r.length());
		this->peer = peer;
	}

	void WebSocketConnector::onAbort(const IPEndPoint& sa)
	{
		readyState = CLOSED;
		onclose(CloseStatus::CONNECTION_FAIL);
	}

	void WebSocketConnector::onRecv(const void *data, int32_t size)
	{
		std::lock_guard<std::recursive_mutex> l(mutex);
		switch (readyState)
		{
		case CONNECTING:
			for (int32_t i = 0; i < size; i++)
			{
				char c = ((const char *)data)[i];
				sbhead.push_back(c);
				switch (stage)
				{
				case 0:
					stage = c == '\r' ? 1 : 0;
					break;
				case 1:
					stage = c == '\n' ? 2 : 0;
					break;
				case 2:
					stage = c == '\r' ? 3 : 0;
					break;
				case 3:
					if (c == '\n')
					{
						std::string security;
						for (std::string::size_type spos = 0, epos; security.empty() && (epos = sbhead.find_first_of('\n', spos)) != std::string::npos; spos = epos + 1)
						{
							std::string::size_type mpos = sbhead.find_last_of(':', epos);
							if (mpos != std::string::npos && mpos > spos)
							{
								spos = sbhead.find_first_not_of(" \t\r\n", spos);
								std::string key;
								for (auto c : sbhead.substr(spos, sbhead.find_last_not_of(" \t\r\n:", mpos) + 1 - spos))
									key.push_back(tolower(c));
								if (key.compare("x-limax-security") == 0)
								{
									spos = sbhead.find_first_not_of(" \t\r\n:", mpos);
									security = sbhead.substr(spos, sbhead.find_last_not_of(" \t\r\n", epos) + 1 - spos);
								}
							}
						}
						if (security.empty())
						{
							close();
							return;
						}
						Octets dh_data = Base64Decode::transform(Octets(&security[0], security.length()));
						const std::vector<unsigned char> material = dhcontext->computeDHKey((unsigned char*)dh_data.begin(), (int32_t)dh_data.size());
						socklen_t key_len;
						int8_t *key = (int8_t*)peer.getAddress(key_len);
						int32_t half = (int32_t)material.size() / 2;
						{
							HmacMD5 hmac(key, 0, key_len);
							hmac.update((int8_t*)&material[0], 0, half);
							osec = std::make_shared<RFC2118Encode>(std::make_shared<Encrypt>(std::make_shared<SinkOctets>(obuf), (int8_t*)hmac.digest(), 16));
						}
						{
							HmacMD5 hmac(key, 0, key_len);
							hmac.update((int8_t*)&material[0], half, (int32_t)material.size() - half);
							isec = std::make_shared<Decrypt>(std::make_shared<RFC2118Decode>(std::make_shared<SinkOctets>(ibuf)), (int8_t*)hmac.digest(), 16);
						}
						std::stringstream spvids;
						for (auto pvid : handle->getProviders())
							spvids << pvid << ',';
						std::string pvids(spvids.str());
						pvids.pop_back();
						std::stringstream skeys;
						skeys << ';';
						for (auto v : handle->getDictionaryCache()->keys())
							skeys << v << ';';
						std::string keys(skeys.str());
						keys.pop_back();
						std::string query;
						query.append("/?username=").append(loginConfig->getUsername()).append("&token=").append(loginConfig->getToken(Octets(&material[0], material.size()))).append("&platflag=").append(loginConfig->getPlatflag()).append(keys).append("&pvids=").append(pvids);
						send(query.c_str(), query.size());
						readyState = OPEN;
						onopen();
						return;
					}
					else
						stage = 0;
				}
			}
			break;
		case OPEN:
			if (true)
			{
				isec->update((int8_t *)data, 0, size);
				isec->flush();
				buffer.insert(buffer.end(), ibuf.begin(), ibuf.size());
				ibuf.clear();
				while (true)
				{
					datasrc.transaction(datasrc.Begin);
					try
					{
						UnmarshalStream is(datasrc);
						int opcode = is.pop_byte_8();
						size_t len = is.pop_byte_8() & 0x7f;
						switch (len)
						{
						case 126:
							len = is.pop_byte_16();
							break;
						case 127:
							len = (size_t)is.pop_byte_64();
						}
						char *data = new char[len];
						is.pop_byte(data, len);
						if (opcode == 0x81)
							onmessage(std::string(data, len));
						delete[] data;
						datasrc.transaction(datasrc.Commit);
					}
					catch (MarshalException)
					{
						datasrc.transaction(datasrc.Rollback);
						break;
					}
				}
			}
		default:
			break;
		}
	}

	void WebSocketConnector::onClose(int status)
	{
		std::lock_guard<std::recursive_mutex> l(mutex);
		onclose(readyState == ReadyState::CLOSING ? CloseStatus::ACTIVE_CLOSED : CloseStatus::PASSIVE_CLOSED);
		readyState = ReadyState::CLOSED;
	}

	void WebSocketConnector::send(const char *p, size_t _len)
	{
		int64_t len = (int64_t)_len;
		osec->update((int8_t)0x81);
		if (len < 126)
			osec->update((int8_t)(len | 0x80));
		else if (len < 65536)
		{
			osec->update((int8_t)254);
			osec->update((int8_t)(len >> 8));
			osec->update((int8_t)len);
		}
		else
		{
			osec->update((int8_t)255);
			osec->update((int8_t)(len >> 56));
			osec->update((int8_t)(len >> 48));
			osec->update((int8_t)(len >> 40));
			osec->update((int8_t)(len >> 32));
			osec->update((int8_t)(len >> 24));
			osec->update((int8_t)(len >> 16));
			osec->update((int8_t)(len >> 8));
			osec->update((int8_t)len);
		}
		osec->update(0);
		osec->update(0);
		osec->update(0);
		osec->update(0);
		osec->update((int8_t*)p, 0, (int32_t)len);
		osec->flush();
		tcpclient->send(Octets(obuf));
		obuf.clear();
	}

	void WebSocketConnector::send(const std::string& utf8message)
	{
		std::lock_guard<std::recursive_mutex> l(mutex);
		if (readyState != ReadyState::OPEN)
			return;
		send(utf8message.c_str(), utf8message.length());
	}

	void WebSocketConnector::_close()
	{
		{
			std::lock_guard<std::recursive_mutex> l(mutex);
			if (readyState != ReadyState::OPEN && readyState != ReadyState::CONNECTING)
				return;
			readyState = ReadyState::CLOSING;
		}
		tcpclient->destroy();
		timer.cancel();
		timer.join();
	}

	void WebSocketConnector::close()
	{
		std::shared_ptr<WebSocketConnector> _this(self);
		if (_this)
			Engine::remove(_this);
	}


	std::shared_ptr<Closeable> createWebSocketConnector(const std::string& ip, short port, LoginConfigPtr _loginConfig, ScriptEngineHandlePtr handle)
	{
		std::shared_ptr<helper::LoginConfigImpl> loginConfig = std::dynamic_pointer_cast<helper::LoginConfigImpl>(_loginConfig);
		std::shared_ptr<WebSocketConnector> ws(new WebSocketConnector(loginConfig, handle));
		std::weak_ptr<WebSocketConnector> self = std::weak_ptr<WebSocketConnector>(ws);
		ws->self = self;
		handle->registerScriptSender([self](const std::string& data)
		{
			std::shared_ptr<WebSocketConnector> wsc(self);
			if (!wsc)
				return false;
			wsc->send(data);
			return true;
		});
		handle->registerLmkDataReceiver([self](const std::string& lmkdata, Runnable done)
		{
			std::shared_ptr<WebSocketConnector> wsc(self);
			if (!wsc)
				return;
			if (lmkdata.size()) 
				if (LmkUpdater lmkUpdater = wsc->loginConfig->getLmkUpdater())
					lmkUpdater(Base64Decode::transform(Octets(lmkdata.c_str(), lmkdata.size())), done);
		});
		if (loginConfig->getProviderLoginDataManager())
			handle->registerProviderLoginDataManager(loginConfig->getProviderLoginDataManager());
		TcpClient::createAsync(TcpClient::createWeakListener(ws), ip, port);
		Engine::add(ws);
		return ws;
	}
} // namespace limax

