package limax.auany;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import limax.auany.appconfig.AppManager;
import limax.auanymonitor.AuthApp;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.defines.ErrorCodes;
import limax.defines.ErrorSource;
import limax.defines.TemporaryCredentialUsage;
import limax.endpoint.AuanyService.Result;
import limax.util.Pair;
import limax.zdb.Procedure;

public final class Account {
	private static class SessionCredential {
		protected final boolean kind;
		protected final String uid;
		protected final long notAfter;
		protected final int appid;
		protected final long mainid;
		protected final int serial;

		private SessionCredential(boolean kind, String uid, long notAfter, int appid, long mainid, int serial) {
			this.kind = kind;
			this.uid = uid;
			this.notAfter = notAfter;
			this.appid = appid;
			this.mainid = mainid;
			this.serial = serial;
		}

		SessionCredential(String uid, long notAfter, int appid, long mainid, int serial) {
			this(true, uid, notAfter, appid, mainid, serial);
		}

		SessionCredential(SessionCredential r, long notAfter) {
			this(false, r.uid, notAfter, r.appid, r.mainid, r.serial);
		}

		SessionCredential(OctetsStream os) throws MarshalException {
			this(os.unmarshal_boolean(), os.unmarshal_String(), os.unmarshal_long(), os.unmarshal_int(),
					os.unmarshal_long(), os.unmarshal_int());
		}

		OctetsStream marshal(OctetsStream os) {
			return os.marshal(kind).marshal(uid).marshal(notAfter).marshal(appid).marshal(mainid).marshal(serial);
		}

		boolean isRealSessionCredential() {
			return kind;
		}
	}

	private static class TemporaryCredential extends SessionCredential {
		private final byte usage;
		private final String subid;

		TemporaryCredential(SessionCredential r, long milliseconds, byte usage, String subid) {
			super(r, System.currentTimeMillis() + milliseconds);
			this.usage = usage;
			this.subid = subid;
		}

		TemporaryCredential(String uid, int appid, long mainid, int serial, long milliseconds, byte usage,
				String subid) {
			super(uid, System.currentTimeMillis() + milliseconds, appid, mainid, serial);
			this.usage = usage;
			this.subid = subid;
		}

		TemporaryCredential(OctetsStream os) throws MarshalException {
			super(os);
			this.usage = os.unmarshal_byte();
			this.subid = os.unmarshal_String();
		}

		OctetsStream marshal(OctetsStream os) {
			return super.marshal(os).marshal(usage).marshal(subid);
		}
	}

	private static String encode(Consumer<OctetsStream> cos, String authcode) throws GeneralSecurityException {
		KeyManager km = OperationEnvironment.getKeyManager();
		int keyindex = km.getRecentIndex();
		byte[] key = km.getKey(keyindex);
		OctetsStream os = new OctetsStream();
		cos.accept(os);
		os.marshal(OperationEnvironment.getEnvironmentIdentity());
		os.marshal(keyindex);
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		mac.update(authcode.getBytes(StandardCharsets.UTF_8));
		os.marshal(mac.doFinal(os.getBytes()));
		return Base64.getEncoder().encodeToString(os.getBytes());
	}

	@FunctionalInterface
	private interface Decode<T> {
		T apply(OctetsStream os) throws MarshalException;
	}

	private static <T> T decode(String cred, String authcode, Decode<T> cos)
			throws MarshalException, GeneralSecurityException {
		int pos = cred.indexOf(",");
		if (pos != -1)
			cred = cred.substring(0, pos);
		OctetsStream os = OctetsStream.wrap(Octets.wrap(Base64.getDecoder().decode(cred)));
		T credential = cos.apply(os);
		if (os.unmarshal_int() != OperationEnvironment.getEnvironmentIdentity())
			throw new RuntimeException("not local auany");
		KeyManager km = OperationEnvironment.getKeyManager();
		byte[] key = km.getKey(os.unmarshal_int());
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		mac.update(authcode.getBytes(StandardCharsets.UTF_8));
		mac.update(os.getBytes(), 0, os.position());
		if (!Arrays.equals(mac.doFinal(), os.unmarshal_bytes()))
			throw new RuntimeException("Invalid Credential");
		return credential;
	}

	private static String encode(SessionCredential c, String authcode, Collection<Long> subordinates)
			throws GeneralSecurityException {
		return Stream
				.concat(Stream.of('S' + encode(os -> c.marshal(os), authcode)),
						subordinates.stream().map(i -> Long.toUnsignedString(i, Character.MAX_RADIX)))
				.collect(Collectors.joining(","));
	}

	private static SessionCredential decodeSessionCredential(String cred, String authcode) throws Exception {
		SessionCredential credential = decode(cred, authcode, os -> new SessionCredential(os));
		if (!credential.isRealSessionCredential())
			throw new Exception();
		return credential;
	}

	private static String encode(TemporaryCredential c, String authcode) throws GeneralSecurityException {
		return 'T' + encode(os -> c.marshal(os), authcode);
	}

	private static TemporaryCredential decodeTemporaryCredential(String cred, String authcode) throws Exception {
		return decode(cred, authcode, os -> new TemporaryCredential(os));
	}

	private static Procedure.Done<Procedure> done(SessionCredential[] c, String authcode, Collection<Long> subordinates,
			int[] e, Runnable[] accountLoggerAction, Result onresult) {
		return (p, r) -> {
			try {
				if (r.isSuccess()) {
					if (accountLoggerAction[0] != null)
						accountLoggerAction[0].run();
					onresult.apply(ErrorSource.LIMAX, ErrorCodes.SUCCEED, encode(c[0], authcode, subordinates));
					return;
				}
			} catch (Exception ex) {
			}
			onresult.apply(ErrorSource.LIMAX, e[0], "");
		};
	}

	@FunctionalInterface
	interface CredentialResult {
		void apply(int errorSource, int errorCode, String uid, long notAfter);
	}

	static void check(String cred, String authcode, CredentialResult onresult) {
		SessionCredential c;
		try {
			c = decodeSessionCredential(cred, authcode);
		} catch (Exception e) {
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL, "", 0);
			return;
		}
		if (c.notAfter > System.currentTimeMillis()) {
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL, "", 0);
			return;
		}
		onresult.apply(ErrorSource.LIMAX, ErrorCodes.SUCCEED, c.uid, c.notAfter);
	}

	private static void derive(long sessionid, Result onresult) {
		int[] e = new int[] { ErrorCodes.AUANY_CALL_PROCEDURE_FAILED };
		long[] subid = new long[1];
		Procedure.execute(() -> {
			xbean.Session s = table.Session.update(sessionid);
			if (s == null)
				return false;
			if (s.getSubordinates().size() >= AppManager.getMaxSubordinates(s.getAppid())) {
				e[0] = ErrorCodes.AUANY_SERVICE_ACCOUNT_TOO_MANY_SUBORDINATES;
				return false;
			}
			s.getSubordinates().add(subid[0] = table.Session.newKey());
			return true;
		}, (p, r) -> {
			if (r.isSuccess())
				onresult.apply(ErrorSource.LIMAX, ErrorCodes.SUCCEED,
						Long.toUnsignedString(subid[0], Character.MAX_RADIX));
			else
				onresult.apply(ErrorSource.LIMAX, e[0], "");
		});
	}

	public static void derive(String cred, String authcode, long sessionid, Result onresult) {
		if (cred.isEmpty() && authcode.isEmpty()) {
			derive(sessionid, onresult);
			return;
		}
		if (Long.toString(sessionid).equals(cred)) {
			if (!Invite.test1(sessionid)) {
				onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_INVITE, "");
				return;
			}
			int appid = (int) sessionid;
			SessionCredential c[] = new SessionCredential[1];
			int e[] = new int[] { ErrorCodes.AUANY_CALL_PROCEDURE_FAILED };
			Runnable[] r = new Runnable[1];
			Procedure.execute(() -> {
				Pair<Long, xbean.Session> p = table.Session.insert();
				p.getValue().setSerial(0);
				p.getValue().setAppid(appid);
				c[0] = new SessionCredential("", Long.MAX_VALUE, appid, p.getKey(), 0);
				return true;
			}, done(c, authcode, Collections.emptyList(), e, r, onresult));
		} else {
			SessionCredential c[] = new SessionCredential[1];
			try {
				c[0] = decodeSessionCredential(cred, authcode);
			} catch (Exception e) {
				onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL, "");
				return;
			}
			int maxSubordinates = AppManager.getMaxSubordinates(c[0].appid);
			if (maxSubordinates == 0) {
				onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_ACCOUNT_TOO_MANY_SUBORDINATES, "");
				return;
			}
			int e[] = new int[] { ErrorCodes.AUANY_CALL_PROCEDURE_FAILED };
			Runnable[] r = new Runnable[1];
			Collection<Long> subordinates = new ArrayList<>();
			Procedure.execute(() -> {
				xbean.Session s = table.Session.update(sessionid);
				if (sessionid != c[0].mainid && !s.getSubordinates().contains(sessionid)) {
					e[0] = ErrorCodes.AUANY_SERVICE_CREDENTIAL_NOT_MATCH;
					return false;
				}
				if (s.getSubordinates().size() >= maxSubordinates) {
					e[0] = ErrorCodes.AUANY_SERVICE_ACCOUNT_TOO_MANY_SUBORDINATES;
					return false;
				}
				long subid = table.Session.newKey();
				s.getSubordinates().add(subid);
				subordinates.addAll(s.getSubordinates());
				r[0] = () -> AccountManager.getLogger().link(c[0].appid, subid, c[0].uid);
				return true;
			}, done(c, authcode, subordinates, e, r, onresult));
		}
	}

	public static void bind(String cred, String authcode, String uid, long notAfter, long sessionid, Result onresult) {
		if (Long.toString(sessionid).equals(cred)) {
			if (!Invite.test1(sessionid)) {
				onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_INVITE, "");
				return;
			}
			int appid = (int) sessionid;
			SessionCredential c[] = new SessionCredential[1];
			int e[] = new int[] { ErrorCodes.AUANY_CALL_PROCEDURE_FAILED };
			Runnable r[] = new Runnable[1];
			Collection<Long> subordinates = new ArrayList<>();
			Procedure.execute(() -> {
				xbean.Account a = table.Account.update(uid);
				if (a == null)
					a = table.Account.insert(uid);
				Long mainid = a.getApplication().get(appid);
				if (mainid != null) {
					xbean.Session s = table.Session.update(mainid);
					int serial = s.getSerial() + 1;
					s.setSerial(serial);
					c[0] = new SessionCredential(uid, notAfter, appid, mainid, serial);
					subordinates.addAll(s.getSubordinates());
					// rebind from http, account already linked.
				} else {
					Pair<Long, xbean.Session> p = table.Session.insert();
					long newid = p.getKey();
					p.getValue().setSerial(0);
					p.getValue().setAppid(appid);
					a.getApplication().put(appid, newid);
					c[0] = new SessionCredential(uid, notAfter, appid, newid, 0);
					r[0] = () -> AccountManager.getLogger().link(appid, newid, uid);
				}
				return true;
			}, done(c, authcode, subordinates, e, r, onresult));
		} else {
			SessionCredential c[] = new SessionCredential[1];
			try {
				c[0] = decodeSessionCredential(cred, authcode);
			} catch (Exception e) {
				onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL, "");
				return;
			}
			boolean rebind = c[0].uid.length() > 0;
			if (rebind && !uid.equals(c[0].uid)) {
				onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_BIND_HAS_BEEN_BOUND, "");
				return;
			}
			int appid = c[0].appid;
			int e[] = new int[] { ErrorCodes.AUANY_CALL_PROCEDURE_FAILED };
			Runnable r[] = new Runnable[1];
			Collection<Long> subordinates = new ArrayList<>();
			Procedure.execute(() -> {
				xbean.Session s = table.Session.select(c[0].mainid);
				if (sessionid != c[0].mainid && !s.getSubordinates().contains(sessionid)) {
					e[0] = ErrorCodes.AUANY_SERVICE_CREDENTIAL_NOT_MATCH;
					return false;
				}
				c[0] = new SessionCredential(uid, notAfter, s.getAppid(), c[0].mainid, s.getSerial());
				subordinates.addAll(s.getSubordinates());
				xbean.Account a = table.Account.update(uid);
				if (a == null)
					a = table.Account.insert(uid);
				Long mainid = a.getApplication().get(appid);
				if (mainid == null) {
					a.getApplication().put(appid, c[0].mainid);
				} else if (mainid != c[0].mainid) {
					e[0] = ErrorCodes.AUANY_SERVICE_BIND_ACCOUNT_HAS_BEEN_USED;
					return false;
				}
				if (!rebind) {
					r[0] = () -> {
						AccountLogger logger = AccountManager.getLogger();
						logger.link(appid, c[0].mainid, uid);
						subordinates.forEach(subid -> logger.link(appid, subid, uid));
					};
				}
				return true;
			}, done(c, authcode, subordinates, e, r, onresult));
		}
	}

	interface LoginResult {
		void apply(int errorSource, int errorCode, long sessionid, long mainid, String uid, int serial, Octets lmkdata);
	}

	private static Procedure.Done<Procedure> done(long[] sessionid, String uid, int serial[], int[] e,
			Runnable[] accountLoggerAction, Octets[] lmkdata, LoginResult onresult) {
		return (p, r) -> {
			try {
				if (r.isSuccess()) {
					if (accountLoggerAction[0] != null)
						accountLoggerAction[0].run();
					onresult.apply(ErrorSource.LIMAX, e[0], sessionid[0], sessionid[1], uid, serial[0], lmkdata[0]);
					return;
				}
			} catch (Exception ex) {
			}
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_CALL_PROCEDURE_FAILED, 0, 0, "", 0, null);
		};
	}

	private static void login(SessionCredential c, String subid, int appid, LoginResult onresult) {
		long[] sessionid = new long[] { 0, c.mainid };
		int[] e = new int[] { ErrorCodes.AUANY_AUTHENTICATE_FAIL };
		Runnable[] r = new Runnable[1];
		Octets[] l = new Octets[1];
		Procedure.execute(() -> {
			xbean.Session s = table.Session.select(c.mainid);
			if (c.serial - s.getSerial() < 0)
				return true;
			if (c.uid.length() > 0) {
				xbean.Account a = table.Account.select(c.uid);
				if (a == null)
					return true;
				Long mainid = a.getApplication().get(appid);
				if (mainid == null || mainid != c.mainid)
					return true;
				l[0] = a.getLmkdataOctetsCopy();
			}
			if (subid.isEmpty()) {
				sessionid[0] = c.mainid;
				e[0] = ErrorCodes.SUCCEED;
			} else {
				long id = Long.parseUnsignedLong(subid, Character.MAX_RADIX);
				if (s.getSubordinates().contains(id)) {
					sessionid[0] = id;
					e[0] = ErrorCodes.SUCCEED;
				} else {
					l[0] = null;
				}
			}
			AuthApp.increment_auth(appid);
			return true;
		}, done(sessionid, c.uid, new int[] { c.serial }, e, r, l, onresult));
	}

	public static void login(String cred, String authcode, String subid, int appid, LoginResult onresult) {
		SessionCredential c;
		try {
			c = decodeSessionCredential(cred, authcode);
		} catch (Exception e) {
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL, 0, 0, "", 0, null);
			return;
		}
		if (appid != c.appid || c.notAfter > System.currentTimeMillis()) {
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL, 0, 0, "", 0, null);
			return;
		}
		login(c, subid, appid, onresult);
	}

	private static void login(String uid, String subid, int appid, LoginResult onresult, boolean autocreate) {
		long[] sessionid = new long[2];
		int[] serial = new int[1];
		int[] e = new int[] { ErrorCodes.AUANY_AUTHENTICATE_FAIL };
		Runnable[] r = new Runnable[1];
		Octets[] l = new Octets[1];
		Procedure.execute(() -> {
			xbean.Account a;
			if (subid.isEmpty()) {
				a = table.Account.update(uid);
				if (a == null)
					a = table.Account.insert(uid);
				Long mainid = a.getApplication().get(appid);
				if (mainid == null) {
					if (!autocreate)
						return false;
					Pair<Long, xbean.Session> p = table.Session.insert();
					p.getValue().setSerial(0);
					p.getValue().setAppid(appid);
					a.getApplication().put(appid, sessionid[0] = sessionid[1] = p.getKey());
					serial[0] = 0;
					r[0] = () -> AccountManager.getLogger().link(appid, sessionid[0], uid);
					AuthApp.increment_newaccount(appid);
				} else {
					sessionid[0] = sessionid[1] = mainid;
					serial[0] = table.Session.select(mainid).getSerial();
				}
			} else {
				a = table.Account.select(uid);
				if (a == null)
					return true;
				Long mainid = a.getApplication().get(appid);
				if (mainid == null)
					return true;
				xbean.Session s = table.Session.select(mainid);
				if (s == null)
					return true;
				long id = Long.parseUnsignedLong(subid, Character.MAX_RADIX);
				if (!s.getSubordinates().contains(id))
					return true;
				sessionid[0] = id;
				sessionid[1] = mainid;
				serial[0] = s.getSerial();
			}
			l[0] = a.getLmkdataOctetsCopy();
			AuthApp.increment_auth(appid);
			e[0] = ErrorCodes.SUCCEED;
			return true;
		}, done(sessionid, uid, serial, e, r, l, onresult));
	}

	public static void login(String uid, String subid, int appid, LoginResult onresult) {
		login(uid, subid, appid, onresult, true);
	}

	public static void temporary(String cred, String authcode, String authcode2, long milliseconds, byte usage,
			String subid, Result onresult) {
		SessionCredential c;
		try {
			c = decodeSessionCredential(cred, authcode);
		} catch (Exception e) {
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL, "");
			return;
		}
		login(c, subid, c.appid, (errorSource, errorCode, sessionid, mainid, uid, serial, lmkdata) -> {
			if (errorSource != ErrorSource.LIMAX || errorCode != ErrorCodes.SUCCEED) {
				onresult.apply(errorSource, errorCode, "");
				return;
			}
			try {
				onresult.apply(ErrorSource.LIMAX, ErrorCodes.SUCCEED,
						encode(new TemporaryCredential(c, milliseconds, usage, subid), authcode2));
			} catch (Exception e) {
				onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL, "");
			}
		});
	}

	public static void temporary(String uid, int appid, String authcode, long milliseconds, byte usage, String subid,
			Result onresult) {
		login(uid, subid, appid, (errorSource, errorCode, sessionid, mainid, _uid, serial, lmkdata) -> {
			if (errorSource != ErrorSource.LIMAX || errorCode != ErrorCodes.SUCCEED) {
				onresult.apply(errorSource, errorCode, "");
				return;
			}
			try {
				onresult.apply(ErrorSource.LIMAX, ErrorCodes.SUCCEED, encode(
						new TemporaryCredential(uid, appid, mainid, serial, milliseconds, usage, subid), authcode));
			} catch (Exception e) {
				onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL, "");
			}
		}, false);
	}

	public static void temporaryLogin(String temp, String authcode, int appid, LoginResult onresult) {
		TemporaryCredential c;
		try {
			c = decodeTemporaryCredential(temp, authcode);
		} catch (Exception e) {
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL, 0, 0, "", 0, null);
			return;
		}
		if (appid != c.appid || c.notAfter > System.currentTimeMillis()
				|| c.usage != TemporaryCredentialUsage.USAGE_LOGIN) {
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL, 0, 0, "", 0, null);
			return;
		}
		login(c, c.subid, appid, onresult);
	}

	public static void transfer(String uid, long notAfter, String authcode, String temp, String authtemp,
			long sessionid, Result onresult) {
		if (!Invite.test1(sessionid)) {
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_INVITE, "");
			return;
		}
		TemporaryCredential c;
		try {
			c = decodeTemporaryCredential(temp, authtemp);
		} catch (Exception e) {
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL, "");
			return;
		}
		if (c.appid != (int) sessionid || c.usage != TemporaryCredentialUsage.USAGE_TRANSFER
				|| c.notAfter > System.currentTimeMillis() || uid.equals(c.uid)) {
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL, "");
			return;
		}
		SessionCredential[] cred = new SessionCredential[1];
		Collection<Long> subordinates = new ArrayList<>();
		int[] e = new int[] { ErrorCodes.AUANY_CALL_PROCEDURE_FAILED };
		Runnable[] r = new Runnable[1];
		if (c.subid.isEmpty()) {
			Procedure.execute(() -> {
				xbean.Session s = table.Session.update(c.mainid);
				if (s.getSerial() != c.serial) {
					e[0] = ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL;
					return false;
				}
				s.setSerial(c.serial + 1);
				xbean.Account adst = table.Account.update(uid);
				if (adst == null)
					adst = table.Account.insert(uid);
				else if (adst.getApplication().containsKey(c.appid)) {
					e[0] = ErrorCodes.AUANY_SERVICE_TRANSFER_APPID_COLLISION;
					return false;
				}
				adst.getApplication().put(c.appid, c.mainid);
				if (!c.uid.isEmpty()) {
					xbean.Account asrc = table.Account.update(c.uid);
					if (asrc != null)
						asrc.getApplication().remove(c.appid);
				}
				cred[0] = new SessionCredential(uid, notAfter, c.appid, c.mainid, s.getSerial());
				subordinates.addAll(s.getSubordinates());
				r[0] = () -> {
					AccountLogger logger = AccountManager.getLogger();
					logger.relink(c.appid, c.mainid, c.uid, uid);
					subordinates.forEach(subid -> logger.relink(c.appid, subid, c.uid, uid));
				};
				return true;
			}, done(cred, authcode, subordinates, e, r, onresult));
		} else {
			long subid = Long.parseUnsignedLong(c.subid, Character.MAX_RADIX);
			Procedure.execute(() -> {
				xbean.Session s = table.Session.update(c.mainid);
				if (s.getSerial() != c.serial || !s.getSubordinates().remove(subid)) {
					e[0] = ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL;
					return false;
				}
				xbean.Account adst = table.Account.update(uid);
				if (adst == null)
					adst = table.Account.insert(uid);
				Long mainid = adst.getApplication().get(c.appid);
				if (mainid == null) {
					Pair<Long, xbean.Session> p = table.Session.insert();
					long newid = p.getKey();
					p.getValue().setSerial(0);
					p.getValue().setAppid(c.appid);
					p.getValue().getSubordinates().add(subid);
					adst.getApplication().put(c.appid, newid);
					cred[0] = new SessionCredential(uid, notAfter, c.appid, newid, 0);
					subordinates.add(subid);
					r[0] = () -> {
						AccountManager.getLogger().link(c.appid, newid, uid);
						AccountManager.getLogger().relink(c.appid, subid, c.uid, uid);
					};
				} else {
					xbean.Session sdst = table.Session.update(mainid);
					if (sdst == null)
						return false;
					if (sdst.getSubordinates().size() >= AppManager.getMaxSubordinates(c.appid)) {
						e[0] = ErrorCodes.AUANY_SERVICE_ACCOUNT_TOO_MANY_SUBORDINATES;
						return false;
					}
					sdst.getSubordinates().add(subid);
					sdst.setSerial(s.getSerial() + 1);
					cred[0] = new SessionCredential(uid, notAfter, c.appid, mainid, 0);
					subordinates.addAll(sdst.getSubordinates());
					r[0] = () -> AccountManager.getLogger().relink(c.appid, subid, c.uid, uid);
				}
				return true;
			}, done(cred, authcode, subordinates, e, r, onresult));
		}
	}

	static void addLmkData(String uid, Octets lmkdata, Runnable done) {
		Procedure.execute(() -> {
			xbean.Account a = table.Account.update(uid);
			if (a != null)
				a.setLmkdataOctetsCopy(lmkdata);
			return true;
		}, (p, r) -> done.run());
	}
}
