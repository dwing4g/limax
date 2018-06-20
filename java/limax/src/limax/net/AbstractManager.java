package limax.net;

abstract class AbstractManager extends AbstractRpcContext implements Manager, SupportDispatch {
	abstract void removeProtocolTransport(AbstractTransport transport);

	abstract void addProtocolTransport(AbstractTransport transport);

	@Override
	public void close() {
		closeAllContexts();
	}

	Manager getOutmostWrapperManager() {
		Manager manager = this;
		while (manager.getWrapperManager() != null)
			manager = manager.getWrapperManager();
		return manager;
	}
}
