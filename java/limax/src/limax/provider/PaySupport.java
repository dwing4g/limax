package limax.provider;

public interface PaySupport {
	void onPay(long serial, long sessionid, int product, int price, int quantity, Runnable ack);

	void onPayConfirm(long serial, Runnable ack);
}
