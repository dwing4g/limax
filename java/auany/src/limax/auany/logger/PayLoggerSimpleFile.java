package limax.auany.logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.w3c.dom.Element;

import limax.auany.PayDelivery;
import limax.auany.PayLogger;
import limax.auany.PayOrder;
import limax.auany.paygws.AppStore.Request;
import limax.util.ElementHelper;
import limax.util.Trace;

public final class PayLoggerSimpleFile implements PayLogger {
	private final static DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

	private LocalDateTime getDate() {
		LocalDateTime now = LocalDateTime.now();
		if (last == null) {
			File file = path.resolve("pay.log").toFile();
			try {
				ps = new PrintStream(new FileOutputStream(file, true), false, "UTF-8");
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLogger openlog " + file, e);
				ps = null;
			}
		} else if (last.getDayOfYear() != now.getDayOfYear()) {
			ps.close();
			File dest = path.resolve("pay." + last.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) + ".log").toFile();
			File file = path.resolve("pay.log").toFile();
			try {
				ps = new PrintStream(new FileOutputStream(file, !file.renameTo(dest)), false, "UTF-8");
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PayLogger openlog " + file, e);
				ps = null;
			}
		}
		last = now;
		return now;
	}

	private PrintStream ps;
	private Path path;
	private LocalDateTime last;

	@Override
	public void initialize(Element e) {
		ElementHelper eh = new ElementHelper(e);
		try {
			Files.createDirectories(path = Paths.get(eh.getString("payLoggerSimpleFileHome", "paylogs")));
		} catch (IOException e1) {
		}
		eh.warnUnused();
	}

	private String format(LocalDateTime date, String op, PayOrder o) {
		return date.format(dateFormat) + "," + op + "," + o.getOrder() + "," + o.getGateway() + "," + o.getSessionId()
				+ "," + o.getPayId() + "," + o.getProduct() + "," + o.getPrice() + "," + o.getQuantity() + ","
				+ o.getPrice() * o.getQuantity() + "," + o.getElapsed();
	}

	private String format(LocalDateTime date, String op, int number, Request r) {
		return date.format(dateFormat) + "," + op + "," + number + "," + r.getTid() + "," + r.getOrder() + ","
				+ r.getSessionId() + "," + r.getPayId() + "," + r.getProduct() + "," + r.getQuantity();
	}

	@Override
	public synchronized void logCreate(PayOrder order) {
		LocalDateTime date = getDate();
		if (ps != null)
			ps.println(format(date, "CREATE", order));
	}

	@Override
	public synchronized void logFake(long serial, int gateway, int expect) {
		LocalDateTime date = getDate();
		if (ps != null)
			ps.println(dateFormat.format(date) + ",FAKE," + serial + "," + gateway + "," + expect);
	}

	@Override
	public synchronized void logExpire(PayOrder order) {
		LocalDateTime date = getDate();
		if (ps != null)
			ps.println(format(date, "EXPIRE", order));
	}

	@Override
	public synchronized void logOk(PayOrder order) {
		LocalDateTime date = getDate();
		if (ps != null)
			ps.println(format(date, "OK", order));
	}

	@Override
	public synchronized void logFail(PayOrder order, String gatewayMessage) {
		LocalDateTime date = getDate();
		if (ps != null)
			ps.println(format(date, "FAIL", order) + "," + gatewayMessage);
	}

	@Override
	public synchronized void logDead(PayDelivery pd) {
		LocalDateTime date = getDate();
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
		LocalDateTime date = getDate();
		if (ps != null)
			ps.println(format(date, "AppStoreCreate", gateway, req));
	}

	@Override
	public synchronized void logAppStoreSucceed(Request req) {
		LocalDateTime date = getDate();
		if (ps != null)
			ps.println(format(date, "AppStoreSucceed", 0, req));
	}

	@Override
	public synchronized void logAppStoreFail(Request req, int status) {
		LocalDateTime date = getDate();
		if (ps != null)
			ps.println(format(date, "AppStoreFail", status, req));
	}

	@Override
	public synchronized void logAppStoreReceiptReplay(Request req) {
		LocalDateTime date = getDate();
		if (ps != null)
			ps.println(format(date, "AppStoreReceiptReplay", 0, req));
	}
}
