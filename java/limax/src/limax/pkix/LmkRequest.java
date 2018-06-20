package limax.pkix;

import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import limax.codec.JSON;
import limax.codec.Octets;
import limax.codec.RFC2822Address;
import limax.codec.SinkOctets;
import limax.codec.StreamSource;
import limax.codec.asn1.ASN1Object;
import limax.codec.asn1.ASN1OctetString;
import limax.codec.asn1.ASN1PrimitiveObject;
import limax.codec.asn1.ASN1Sequence;
import limax.codec.asn1.ASN1Tag;
import limax.codec.asn1.DecodeBER;
import limax.codec.asn1.TagClass;
import limax.endpoint.LmkBundle;
import limax.util.Helper;

public class LmkRequest {
	private static final ASN1Tag tagEmail = new ASN1Tag(TagClass.ContextSpecific, 1);
	private final String u;
	private final String d;

	LmkRequest(String username, String domain) {
		this.u = username;
		this.d = domain;
	}

	public String getUid() {
		return u + "@" + d;
	}

	public static LmkRequest buildLmkRequest(String uid) {
		RFC2822Address email = new RFC2822Address(uid);
		return new LmkRequest(email.getUsername(), email.getDomain());
	}

	public static LmkRequest buildLmkRequest(X509Certificate cert) throws Exception {
		ASN1Sequence seq = (ASN1Sequence) DecodeBER
				.decode(((ASN1OctetString) DecodeBER.decode(cert.getExtensionValue("2.5.29.17"))).get());
		for (int i = 0; i < seq.size(); i++) {
			ASN1Object obj = seq.get(i);
			if (obj instanceof ASN1PrimitiveObject) {
				ASN1PrimitiveObject item = (ASN1PrimitiveObject) obj;
				if (item.getTag().equals(tagEmail))
					return buildLmkRequest(new String(item.getData(), StandardCharsets.ISO_8859_1));
			}
		}
		throw new RuntimeException("Invalid Lmk Certificate");
	}

	public class Context {
		private final String passphrase;
		private final String host;

		private Context(String host) {
			this.passphrase = Helper.toHexString(Helper.makeRandValues(16));
			this.host = host;
		}

		public String getUid() {
			return LmkRequest.this.getUid();
		}

		public String getHost() {
			return host;
		}

		public LmkBundle fetch(SSLContext sslContext) throws Exception {
			Map<String, String> map = new HashMap<>();
			map.put("u", u);
			map.put("d", d);
			map.put("p", passphrase);
			HttpsURLConnection conn = (HttpsURLConnection) new URL("https", host,
					"/?" + URLEncoder.encode(JSON.stringify(map), "utf8")).openConnection();
			conn.setSSLSocketFactory(sslContext.getSocketFactory());
			conn.connect();
			Octets lmkData = new Octets();
			try (InputStream in = conn.getInputStream()) {
				new StreamSource(in, new SinkOctets(lmkData)).flush();
			}
			return LmkBundle.createInstance(lmkData, Octets.wrap(passphrase.getBytes()));
		}
	}

	public Context createContext(String host) {
		return new Context(host);
	}

	@FunctionalInterface
	public interface Requestor {
		LmkBundle fetch(String username) throws Exception;
	}

	public static Requestor createRequestor(SSLContextAllocator sslContextAllocator, String host) {
		return u -> new LmkRequest(u, null).createContext(host).fetch(sslContextAllocator.alloc());
	}
}
