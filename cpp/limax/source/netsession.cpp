#include "common.h"

namespace limax {

	SinkTcpClient::SinkTcpClient(TcpClient* _tcpclient) : tcpclient(_tcpclient){}

	void SinkTcpClient::update(int8_t c){ buffer.insert(buffer.end(), &c, sizeof(c)); }

	void SinkTcpClient::update(int8_t data[], int32_t off, int32_t len)	{ buffer.insert(buffer.end(), data + off, len); }

	void SinkTcpClient::flush()
	{
		Octets o;
		o.swap(buffer);
		tcpclient->send(o);
	}

	NetSession::NetSession(Listener* _listener, Transport *_transport) : listener(_listener), transport(_transport), source(inbuffer)
	{
	}

	void NetSession::create(Listener* listener, Transport* transport, std::string ip, short port)
	{
		TcpClient::createAsync(new NetSession(listener, transport), ip, port);
	}

	void NetSession::setInputSecurity(bool compress, const Octets& key)
	{
		std::shared_ptr<Codec> codec(new SinkOctets(inbuffer));
		if (compress)
		{
			codec = std::shared_ptr<Codec>(new BufferedSink(codec));
			codec = std::shared_ptr<Codec>(new RFC2118Decode(codec));
		}
		if (key.size() > 0)
		{
			codec = std::shared_ptr<Codec>(new BufferedSink(codec));
			codec = std::shared_ptr<Codec>(new Decrypt(codec, (int8_t*)key.begin(), (int32_t)key.size()));
		}
		std::lock_guard<std::mutex> scoped(mutex);
		is_codec = codec;
	}

	void NetSession::setOutputSecurity(bool compress, const Octets& key)
	{
		std::shared_ptr<Codec> codec(new SinkTcpClient(tcpclient));
		if (key.size() > 0)
		{
			codec = std::shared_ptr<Codec>(new BufferedSink(codec));
			codec = std::shared_ptr<Codec>(new Encrypt(codec, (int8_t*)key.begin(), (int32_t)key.size()));
		}
		if (compress)
		{
			codec = std::shared_ptr<Codec>(new BufferedSink(codec));
			codec = std::shared_ptr<Codec>(new RFC2118Encode(codec));
		}
		std::lock_guard<std::mutex> scoped(mutex);
		os_codec = codec;
	}

	void NetSession::onCreate(TcpClient *tcpclient)
	{
		this->tcpclient = tcpclient;
	}

	void NetSession::onOpen(const IPEndPoint& local, const IPEndPoint& peer)
	{
		listener->onAddSession(this, local, peer);
	}

	void NetSession::onClose(int status)
	{
		listener->onDelSession(this);
		destroy();
	}

	void NetSession::onAbort(const IPEndPoint &sa)
	{
		listener->onAbortSession(this);
		destroy();
	}

	void NetSession::onRecv(const void *data, int32_t insize)
	{
		{
			auto oldsize = inbuffer.size();
			int8_t* indata = (int8_t*)data;
			std::lock_guard<std::mutex> scoped(mutex);
			is_codec->update(indata, (int32_t)0, insize);
			is_codec->flush();
			if (Trace::isDebugEnabled())
			{
				auto outsize = inbuffer.size() - oldsize;
				std::stringstream ss;
				ss << "NetSession::onRecv  size " << insize << " => " << outsize;
				auto log = ss.str();
				runOnUiThread([log](){ Trace::debug(log); });
			}
		}
		try
		{
			while (Protocol* p = decodeProtocol())
				listener->onProtocol(p);
		}
		catch (MarshalException&){ tcpclient->close(); }
		catch (Protocol::Exception&){ tcpclient->close(); }
	}

	Protocol* NetSession::decodeProtocol()
	{
		while (true)
		{
			if (source.eos())
				return nullptr;
			UnmarshalStream is(source);
			Protocol* protocol = nullptr;
			Protocol::Type type = 0;
			uint32_t size = 0;
			try
			{
				is.transaction(UnmarshalStreamSource::Begin);
				type = is.pop_byte_32();
				size = is.unmarshal_size();
				is.transaction(UnmarshalStreamSource::Rollback);
				auto stub = state->getStub(type);
				if (stub)
				{
					if (!stub->sizePolicy(size))
						throw Protocol::Exception();
					Octets data(size);
					is >> UnmarshalStreamSource::Begin >> type >> data >> UnmarshalStreamSource::Commit;
					OctetsUnmarshalStreamSource sbind(data);
					UnmarshalStream ous(sbind);
					ous >> *(protocol = stub->create());
					protocol->setTransport(transport);
				}
				else
				{
					listener->onCheckUnknownProtocol(this, type, size);
				}
			}
			catch (MarshalException&)
			{
				if (protocol)
				{
					delete protocol;
					throw Protocol::Exception();
				}
				is >> UnmarshalStreamSource::Rollback;
			}
			return protocol;
		}
	}

	void NetSession::destroy()
	{
		TcpClient *t = tcpclient;
		Engine::execute([t](){t->destroy(); });
		delete this;
	}

	void NetSession::send(const Protocol& protocol)
	{
		send(&protocol);
	}

	void NetSession::send(const Protocol* protocol)
	{
		Octets data = protocol->encode();
		int8_t* indata = (int8_t*)data.begin();
		int32_t insize = (int32_t)data.size();
		std::lock_guard<std::mutex> scoped(mutex);
		os_codec->update(indata, (int32_t)0, insize);
		os_codec->flush();
	}

	void NetSession::close()
	{
		std::lock_guard<std::mutex> scoped(mutex);
		tcpclient->close();
	}

	void NetSession::setState(std::shared_ptr<State> state)
	{
		std::lock_guard<std::mutex> scoped(mutex);
		this->state = state;
	}
} // namespace limax {
