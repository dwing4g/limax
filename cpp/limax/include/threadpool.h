#pragma once

namespace limax {
#pragma region class JoinableRunnable
	class LIMAX_DLL_EXPORT_API JoinableRunnable
	{
		bool done = false;
		std::mutex mutex;
		std::condition_variable_any cond;
		Runnable r;
	public:
		JoinableRunnable(Runnable _r);
		operator Runnable();
		void join();
	};
#pragma endregion
#pragma region class DelayedRunnable
	class LIMAX_DLL_EXPORT_API DelayedRunnable
	{
		struct Config;
		Runnable r;
		std::shared_ptr<Config> config;
	public:
		DelayedRunnable();
		DelayedRunnable(Runnable _r, int _precision, int _count);
		operator Runnable();
		void cancel();
		void join();
	};
#pragma endregion
#pragma region class Dispatcher
	class LIMAX_DLL_EXPORT_API Dispatcher
	{
		Executor executor;
		std::atomic<int> running;
		std::recursive_mutex mutex;
		std::condition_variable_any cond;
	public:
		explicit Dispatcher(Executor e);
		Dispatcher(const Dispatcher&) = delete;
		Dispatcher& operator = (const Dispatcher&) = delete;
		void operator()(Runnable r);
		void execute(Runnable r);
		void await();
	};
#pragma endregion
#pragma region class ThreadPool
	class LIMAX_DLL_EXPORT_API ThreadPool
	{
		struct Worker;
		std::mutex mutex;
		std::condition_variable_any cond;
		std::chrono::milliseconds idle_timeout;
		bool closed = false;
		int idle = 0;
		std::unordered_set<Worker*> workers;
		std::vector<Worker*> fadeout;
		std::deque<Runnable> q;
	public:
		~ThreadPool();
		ThreadPool(int max_idle_in_milliseconds);
		ThreadPool(const ThreadPool&) = delete;
		ThreadPool& operator = (const ThreadPool&) = delete;
		void shutdown();
		void execute(Runnable r);
	};
#pragma endregion
} // namespace limax {
