#include "common.h"

namespace limax {

	JoinableRunnable::JoinableRunnable(Runnable _r)
		: r([_r, this]()
	{
		_r();
		std::lock_guard<std::mutex> l(mutex);
		done = true;
		cond.notify_all();
	})
	{}

	JoinableRunnable::operator Runnable()
	{
		return r;
	}

	void JoinableRunnable::join()
	{
		std::lock_guard<std::mutex> l(mutex);
		while (!done)
			cond.wait(mutex);
	}

} // namespace limax {

namespace limax {

	struct DelayedRunnable::Config
	{
		Runnable r;
		int precision;
		int count;
		volatile bool cancelled = false;
		bool done = false;
		std::mutex mutex;
		std::condition_variable_any cond;
		Config(Runnable _r, int _precision, int _count)
			: r(_r), precision(_precision), count(_count)
		{}
		void _action()
		{
			for (int i = 0; i < count; i++)
			{
				std::this_thread::sleep_for(std::chrono::milliseconds(precision));
				if (cancelled)
					return;
			}
			r();
		}
		void action()
		{
			_action();
			std::lock_guard<std::mutex> l(mutex);
			done = true;
			cond.notify_all();
		}
		void join()
		{
			std::lock_guard<std::mutex> l(mutex);
			while (!done)
				cond.wait(mutex);
		}
	};

	DelayedRunnable::DelayedRunnable(){ }
	DelayedRunnable::DelayedRunnable(Runnable _r, int _precision, int _count)
		: config(std::make_shared<Config>(_r, _precision, _count))
	{
		std::shared_ptr<Config> c = config;
		r = [c](){c->action(); };
	}
	DelayedRunnable::operator Runnable()
	{
		return r;
	}
	void DelayedRunnable::cancel()
	{
		if (config)
			config->cancelled = true;
	}
	void DelayedRunnable::join()
	{
		if (config)
			config->join();
	}

} // namespace limax {

namespace limax {

	Dispatcher::Dispatcher(Executor e)
		: executor(e), running(0)
	{}
	void Dispatcher::operator()(Runnable r)
	{
		execute(r);
	}
	void Dispatcher::execute(Runnable r)
	{
		running.fetch_add(1);
		executor([this, r](){
			r();
			if (running.fetch_sub(1) == 1)
				cond.notify_all();
		});
	}
	void Dispatcher::await()
	{
		std::lock_guard<std::recursive_mutex> l(mutex);
		while (running.load() > 0)
			cond.wait(mutex);
	}

} // namespace limax {

namespace limax {

	struct ThreadPool::Worker
	{
		std::thread thread;
		~Worker()
		{
			thread.join();
		}
		Worker(ThreadPool *p, Runnable _r)
			: thread(std::thread([p, _r, this]()
		{
			for (Runnable r = _r; true;)
			{
				r();
				std::lock_guard<std::mutex> l(p->mutex);
				while (p->q.empty())
				{
					if (p->closed)
						return;
					p->idle++;
					if (p->cond.wait_for(p->mutex, p->idle_timeout) == std::cv_status::timeout)
					{
						p->idle--;
						p->fadeout.push_back(this);
						return;
					}
					p->idle--;
				}
				r = p->q.front();
				p->q.pop_front();
			}
		})){}
	};

	ThreadPool::ThreadPool(int max_idle_in_milliseconds)
		: idle_timeout(max_idle_in_milliseconds)
	{}

	ThreadPool::~ThreadPool()
	{
		shutdown();
	}

	void ThreadPool::shutdown()
	{
		{
			std::lock_guard<std::mutex> l(mutex);
			if (closed)
				return;
			closed = true;
			cond.notify_all();
		}
		for (auto w : workers)
			delete w;
	}
	void ThreadPool::execute(Runnable r)
	{
		std::vector<Worker *> tmp;
		{
			std::lock_guard<std::mutex> l(mutex);
			if (closed)
				return;
			if (idle > 0)
			{
				q.push_back(r);
				cond.notify_one();
			}
			else
				workers.insert(new Worker(this, r));
			if (fadeout.size())
			{
				for (auto w : fadeout)
					workers.erase(w);
				tmp.swap(fadeout);
			}
		}
		for (auto w : tmp)
			delete w;
	}

} // namespace limax {
