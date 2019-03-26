#pragma once

namespace limax {
	namespace helper {

		struct AuanyService
		{
			static std::atomic_int snGenerator;
			static std::unordered_map<int, std::shared_ptr<AuanyService>> map;
			static std::mutex lock;
			int sn;
			limax::AuanyService::Result result;
			DelayedRunnable future;

			static std::shared_ptr<AuanyService> removeService(int sn);
			AuanyService(limax::AuanyService::Result _onresult, long timeout);
			static void onResultViewOpen(endpoint::auanyviews::ServiceResult *view);
			static void cleanup();
		};

		Octets toNonce(const std::string& authcode);
		void cleanupAuanyService();

		class CredentialContext
		{
			typedef std::function<Runnable(const std::string&)> Action;

			struct SharedData;
			typedef std::shared_ptr<SharedData> SharedDataPtr;
			struct Listener;
			friend struct Listener;
			const std::string httpHost;
			const int httpPort;
			const int appid;
			const long timeout;
			limax::AuanyService::Result result;
			SharedDataPtr	shareddata;
		public:
			CredentialContext(const std::string& _httpHost, int _httpPort, int _appid, long _timeout);
			~CredentialContext();
		public:
			void derive(const std::string& authcode, const limax::AuanyService::Result& r);
			void bind(const std::string& authcode, LoginConfigPtr loginConfig, const limax::AuanyService::Result& r);
			void temporary(const std::string& credential, const std::string& authcode, const std::string& authcode2, long milliseconds, int8_t usage, const std::string& subid, const limax::AuanyService::Result& r);
			void temporary(LoginConfigPtr loginConfig, int appid, const std::string& authcode, long milliseconds, int8_t usage, const std::string& subid, const limax::AuanyService::Result& r);
			void transfer(LoginConfigPtr loginConfig, const std::string& authcode, const std::string& temp, const std::string& authtemp, const limax::AuanyService::Result& r);
		private:
			void execute(const Action& a, const limax::AuanyService::Result& r);
		};

	} // namespace helper {
} // namespace limax {
