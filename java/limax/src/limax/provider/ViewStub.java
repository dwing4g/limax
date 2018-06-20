package limax.provider;

public interface ViewStub {
	short getClassIndex();

	ViewLifecycle getLifecycle();

	Class<? extends View> getViewClass();

	long getTick();
}