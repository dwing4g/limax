#pragma once

namespace limax {
	class EndpointImpl;
	struct Closeable;
	class Engine
	{
		friend class EndpointImpl;
		static ThreadPool *pool;
		static std::mutex mutex;
		static std::mutex closeables_mutex;
		static std::condition_variable_any cond;
		static std::unordered_set<EndpointImpl*> set;
		static std::unordered_set<std::shared_ptr<Closeable>> closeables;
		static void add(EndpointImpl* e);
		static void remove(EndpointImpl* e);
	public:
		static void add(std::shared_ptr<Closeable> c);
		static void remove(std::shared_ptr<Closeable> c);
		static void open();
		static void close(Runnable done);
		static void execute(Runnable r) { pool->execute(r); }
	};
} // namespace limax {

