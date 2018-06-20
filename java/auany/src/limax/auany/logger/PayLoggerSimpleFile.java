package limax.auany.logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.w3c.dom.Element;

import limax.auany.PayDelivery;
import limax.auany.PayLogger;
import limax.auany.PayOrder;
import limax.auany.paygws.AppStore.Request;
import limax.util.ElementHelper;
import limax.util.Trace;

public final class PayLoggerSimpleFile implements PayLogger {
	private final static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

	private Date getDate() {
		Calendar cal = Calendar.getInstance();
		if (last == null) {
			File file = path.resolve("pay.log").toFile();
			try {
				ps = new PrintStream(new FileOutputStream(file, true), false, "UTF-8");
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLogger openlog " + file, e);
				ps = null;
			}
		} else if (last.get(Calendar.DAY_OF_YEAR) != cal.get(Calendar.DAY_OF_YEAR)) {
			ps.close();
			File dest = path.resolve("pay." + new SimpleDateFormat("yyyy.MM.dd").format(last.getTime()) + ".log")
					.toFile();
			File file = path.resolve("pay.log").toFile();
			try {
				ps = new PrintStream(new FileOutputStream(file, !file.renameTo(dest)), false, "UTF-8");
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLogger openlog " + file, e);
				ps = null;
			}
		}
		last = cal;
		return cal.getTime();
	}

	private PrintStream ps;
	private Path path;
	private Calendar last;

	@Override
	public void initialize(Element e) {
		ElementHelper eh = new ElementHelper(e);
		try {
			Files.createDirectories(path = Paths.get(eh.getString("payLoggerSimpleFileHome", "paylogs")));
		} catch (IOException e1) {
		}
	}

	private String format(Date date, String op, PayOrder o) {
		return dateFormat.format(date) + "," + op + "," + o.getOrder() + "," + o.getGateway() + "," + o.getSessionId()
				+ "," + o.getPayId() + "," + o.getProduct() + "," + o.getPrice() + "," + o.getQuantity() + ","
				+ o.getPrice() * o.getQuantity() + "," + o.getElapsed();
	}

	private String format(Date date, String op, int number, Request r) {
		return dateFormat.format(date) + "," + op + "," + number + "," + r.getTid() + "," + r.getOrder() + ","
				+ r.getSessionId() + "," + r.getPayId() + "," + r.getProduct() + "," + r.getQuantity();
	}

	@Override
	public synchronized void logCreate(PayOrder order) {
		Date date = getDate();
		if (ps != null)
			ps.println(format(date, "CREATE", order));
	}

	@Override
	public synchronized void logFake(long serial, int gateway, int expect) {
		Date date = getDate();
		if (ps != null)
			ps.println(dateFormat.format(date) + ",FAKE," + serial + "," + gateway + "," + expect);
	}

	@Override
	public synchronized void logExpire(PayOrder order) {
		Date date = getDate();
		if (ps != null)
			ps.println(format(date, "EXPIRE", order));
	}

	@Override
	public synchronized void logOk(PayOrder order) {
		Date date = getDate();
		if (ps != null)
			ps.println(format(date, "OK", order));
	}

	@Override
	public synchronized void logFail(PayOrder order, String gatewayMessage) {
		Date date = getDate();
		if (ps != null)
			ps.println(format(date, "FAIL", order) + "," + gatewayMessage);
	}

	@Override
	public synchronized void logDead(PayDelivery pd) {
		Date date = getDate();
		if (ps != null)
			ps.println(dateFormat.format(date) + ",DEAD," + pd.getOrder() + "," + pd.getSessionId() + ","
					+ pd.getPayId() + "," + pd.getProduct() + "," + pd.getPrice() + "," + pd.getQuantity() + ","
					+ pd.getPrice() * pd.getQuantity() + "," + pd.getElapsed());
	}

	@Override
	public synchronized void close() throws Exception {
		if (ps != null)
			ps.close();
	}

	@Override
	public synchronized void logAppStoreCreate(Request req, int gateway) {
		Date date = getDate();
		if (ps != null)
			ps.println(format(date, "AppStoreCreate", gateway, req));
	}

	@Override
	public synchronized void logAppStoreSucceed(Request req) {
		Date date = getDate();
		if (ps != null)
			ps.println(format(date, "AppStoreSucceed", 0, req));
	}

	@Override
	public synchronized void logAppStoreFail(Request req, int status) {
		Date date = getDate();
		if (ps != null)
			ps.println(format(date, "AppStoreFail", status, req));
	}

	@Override
	public synchronized void logAppStoreReceiptReplay(Request req) {
		Date date = getDate();
		if (ps != null)
			ps.println(format(date, "AppStoreReceiptReplay", 0, req));
	}
}
