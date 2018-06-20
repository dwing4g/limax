#pragma once

namespace limax {
	namespace helper {

		class ScriptSenderImpl : public ScriptSender
		{
			std::weak_ptr<EndpointManager> netmanager;
		public:
			ScriptSenderImpl(std::weak_ptr<EndpointManager> _netmanager)
				: netmanager(_netmanager)
			{}
			virtual ~ScriptSenderImpl() {}
		public:
			virtual void send(const std::string& scriptdata)
			{
			}
		};


		class ScriptExchange
		{
			EndpointManager* netmanager;
			std::shared_ptr<ScriptEngineHandle> handle;
			hashset<int32_t> providers;
			int closeReason;
		public:
			ScriptExchange()
				: closeReason(0)
			{}
			~ScriptExchange() {}
		private:
			void process(int t, const std::string& p)
			{
				auto r = handle->action(t, p);
				if (3 != r)
					return;
				if (Trace::isInfoEnabled())
				{
					std::ostringstream oss;
					oss << "ScriptExchange::process handle->action = " << r << " do netmanager->close()";
					Trace::info(oss.str());
				}
				netmanager->close();
			}
		public:
			void onLoad(EndpointManager* _netmanager, std::shared_ptr<ScriptEngineHandle> _handle, const std::string& welcome)
			{
				handle = _handle;
				if (!handle)
					return;
				providers = handle->getProviders();
				netmanager = _netmanager;
				handle->registerScriptSender([_netmanager](const std::string& scriptdata)
				{
					limax::endpoint::providerendpoint::SendControlToServer  protocol;
					protocol.providerid = -1;
					protocol.stringdata = scriptdata;
					protocol.send(_netmanager->getTransport());
					return true;
				});
				if (Trace::isInfoEnabled())
				{
					std::ostringstream oss;
					oss << "ScriptExchange::onLoad welcome = " << welcome;
					Trace::info(oss.str());
				}
				process(1, welcome);
			}

			void onSyncViewToClients(limax::endpoint::providerendpoint::SyncViewToClients* protocol)
			{
				if (!handle)
					return;
				if (protocol->stringdata.empty())
					return;
				if (providers.find(protocol->providerid) != providers.end())
				{
					if (Trace::isInfoEnabled())
					{
						std::ostringstream oss;
						oss << "ScriptExchange::onSyncViewToClients stringdata = " << protocol->stringdata;
						Trace::info(oss.str());
					}
					process(1, protocol->stringdata);
				}
			}

			void onTunnel(int32_t providerid, int label, Octets data) {
				if (!handle)
					return;
				Octets b64data = Base64Encode::transform(data);
				std::string s((const char *)b64data.begin(), b64data.size());
				std::stringstream ss;
				ss << 'S' << tostring36(s.length()) << ':' << s;
				ss << 'I' << tostring36(providerid) << ':';
				ss << 'I' << tostring36(label) << ':';
				process(1, ss.str());
			}

			void onClose(int _closeReason)
			{
				if (Trace::isInfoEnabled())
				{
					std::ostringstream oss;
					oss << "ScriptExchange::onClose closeReason = " << _closeReason;
					Trace::info(oss.str());
				}
				closeReason = _closeReason;
			}

			void onUnload()
			{
				if (handle)
				{
					std::string str;
					if (closeReason != 0)
						str = std::to_string(closeReason);
					if (Trace::isInfoEnabled())
					{
						std::ostringstream oss;
						oss << "ScriptExchange::onUnload action  2 \"" << str << "\"";
						Trace::info(oss.str());
					}
					handle->action(2, str);
				}
			}
		};

	} // namespace helper {
} // namespace limax {
