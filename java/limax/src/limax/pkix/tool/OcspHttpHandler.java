package limax.pkix.tool;

import java.math.BigInteger;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import limax.codec.Octets;
import limax.codec.asn1.ASN1BitString;
import limax.codec.asn1.ASN1ConstructedObject;
import limax.codec.asn1.ASN1Enumerated;
import limax.codec.asn1.ASN1GeneralizedTime;
import limax.codec.asn1.ASN1Integer;
import limax.codec.asn1.ASN1Null;
import limax.codec.asn1.ASN1Object;
import limax.codec.asn1.ASN1ObjectIdentifier;
import limax.codec.asn1.ASN1OctetString;
import limax.codec.asn1.ASN1RawData;
import limax.codec.asn1.ASN1Sequence;
import limax.codec.asn1.ASN1Tag;
import limax.codec.asn1.DecodeBER;
import limax.codec.asn1.TagClass;
import limax.http.DataSupplier;
import limax.http.Headers;
import limax.http.HttpExchange;
import limax.http.HttpHandler;
import limax.util.SecurityUtils.PublicKeyAlgorithm;

class OcspHttpHandler implements HttpHandler {
	private static final long OCSP_REQ_MAX = Long.getLong("limax.pkix.tool.OcspHttpHandler.OCSP_REQ_MAX", 65536);
	private static final ASN1Tag CtxTag0 = new ASN1Tag(TagClass.ContextSpecific, 0);
	private static final ASN1Tag CtxTag1 = new ASN1Tag(TagClass.ContextSpecific, 1);
	private static final ASN1Object V1 = new ASN1ConstructedObject(CtxTag0, new ASN1Integer(BigInteger.valueOf(0)));
	private static final ASN1ObjectIdentifier OID_OSCP_BASIC = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.48.1.1");
	private static final byte[] OCSPResponseStatusMalformedRequest = new byte[] { 0x30, 0x03, 0x0a, 0x01, 0x01 };
	private static final byte[] OCSPResponseStatusInternalError = new byte[] { 0x30, 0x03, 0x0a, 0x01, 0x02 };
	private static final byte[] OCSPResponseStatusUnauthorized = new byte[] { 0x30, 0x03, 0x0a, 0x01, 0x06 };

	private final Function<OcspCertID, OcspCertStatus> query;
	private final OcspResponseCache cache;
	private final OcspSignerConfig ocspSignerConfig;

	OcspHttpHandler(Function<OcspCertID, OcspCertStatus> query, OcspResponseCache cache,
			OcspSignerConfig ocspSignerConfig) {
		this.query = query;
		this.cache = cache;
		this.ocspSignerConfig = ocspSignerConfig;
	}

	@Override
	public void censor(HttpExchange exchange) {
		exchange.getFormData().postLimit(OCSP_REQ_MAX);
	}

	@Override
	public DataSupplier handle(HttpExchange exchange) {
		byte[] response;
		while (true) {
			Octets key;
			byte[] in;
			if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
				String path = exchange.getRequestURI().getPath();
				in = Base64.getDecoder().decode(path.substring(path.lastIndexOf('/') + 1));
				key = Octets.wrap(in);
			} else {
				key = exchange.getFormData().getRaw();
				in = key.getBytes();
			}
			response = cache.get(key);
			if (response == null) {
				ASN1Sequence request;
				try {
					request = (ASN1Sequence) DecodeBER.decode(in);
				} catch (Exception e) {
					response = OCSPResponseStatusMalformedRequest;
					break;
				}
				List<OcspCertID> certIDs;
				try {
					certIDs = OcspHttpHandler.parseRequest(request);
				} catch (Exception e) {
					response = OCSPResponseStatusMalformedRequest;
					break;
				}
				List<OcspCertStatus> certStatuses = new ArrayList<>();
				try {
					for (OcspCertID certID : certIDs) {
						OcspCertStatus certStatus = query.apply(certID);
						if (certStatus == null) {
							certStatuses = null;
							break;
						}
						certStatuses.add(certStatus);
					}
				} catch (Exception e) {
				}
				if (certStatuses == null) {
					response = OCSPResponseStatusUnauthorized;
					break;
				}
				try {
					OcspResponseInfo responseInfo = OcspHttpHandler.makeResponse(certStatuses,
							ocspSignerConfig.getNextUpdateDelay(), ocspSignerConfig.getSignatureBits(),
							ocspSignerConfig.getCurrent());
					cache.put(key, responseInfo);
					response = responseInfo.getResponse();
				} catch (Exception e) {
					response = OCSPResponseStatusInternalError;
					break;
				}
			}
			break;
		}
		Headers headers = exchange.getResponseHeaders();
		headers.set("Content-Type", "application/ocsp-response");
		headers.set("Content-Transfer-Encoding", "binary");
		headers.set("Cache-Control", "no-store");
		return DataSupplier.from(response);
	}

	private static List<OcspCertID> parseRequest(ASN1Sequence request) throws Exception {
		ASN1Sequence tbsRequest = (ASN1Sequence) request.get(0);
		ASN1Sequence requestList = tbsRequest.getChildren().stream().filter(obj -> obj.compareTag(ASN1Sequence.tag))
				.findAny().map(obj -> (ASN1Sequence) obj).get();
		List<OcspCertID> certIDs = new ArrayList<>();
		for (int i = 0, count = requestList.size(); i < count; i++)
			certIDs.add(new OcspCertID((ASN1Sequence) ((ASN1Sequence) requestList.get(i)).get(0)));
		return certIDs;
	}

	private static OcspResponseInfo makeResponse(List<OcspCertStatus> certStatuses, int nextUpdateDelay,
			int signatureBits, OcspSignerConfig.Current config) throws Exception {
		Instant now = Instant.now();
		Instant nextUpdate = now.plusMillis(nextUpdateDelay);
		ASN1Sequence responses = new ASN1Sequence();
		for (OcspCertStatus certStatus : certStatuses) {
			ASN1Sequence singleResponse = new ASN1Sequence();
			singleResponse.addChild(certStatus.getCertID());
			singleResponse
					.addChild(
							certStatus.isRevoked()
									? new ASN1Sequence(CtxTag1,
											new ASN1GeneralizedTime(
													Instant.ofEpochMilli(certStatus.getRevocationTime())))
									: new ASN1Null(CtxTag0));
			singleResponse.addChild(new ASN1GeneralizedTime(now));
			singleResponse.addChild(new ASN1ConstructedObject(CtxTag0, new ASN1GeneralizedTime(nextUpdate)));
			responses.addChild(singleResponse);
		}
		ASN1Sequence tbsResponseData = new ASN1Sequence();
		tbsResponseData.addChild(V1);
		tbsResponseData.addChild(new ASN1RawData(config.getResponderID()));
		tbsResponseData.addChild(new ASN1GeneralizedTime(now));
		tbsResponseData.addChild(responses);
		byte[] tbsResponseDataDER = tbsResponseData.toDER();
		PublicKeyAlgorithm publicKeyAlgorithm = PublicKeyAlgorithm.valueOf(config.getPrivateKey());
		ASN1ObjectIdentifier signature = publicKeyAlgorithm.getSignatureAlgorithm(signatureBits);
		ASN1Object signatureAlgorithm = publicKeyAlgorithm.createAlgorithmIdentifier(signature);
		Signature signer = Signature.getInstance(signature.get());
		signer.initSign(config.getPrivateKey());
		signer.update(tbsResponseDataDER);
		ASN1Sequence basicOCSPResponse = new ASN1Sequence();
		basicOCSPResponse.addChild(new ASN1RawData(tbsResponseDataDER));
		basicOCSPResponse.addChild(signatureAlgorithm);
		basicOCSPResponse.addChild(new ASN1BitString(signer.sign()));
		basicOCSPResponse.addChild(new ASN1RawData(config.getCerts()));
		return new OcspResponseInfo(
				certStatuses.stream().map(OcspCertStatus::getSerialNumber).collect(Collectors.toSet()), nextUpdate,
				new ASN1Sequence(new ASN1Enumerated(new byte[] { 0 }),
						new ASN1ConstructedObject(CtxTag0,
								new ASN1Sequence(OID_OSCP_BASIC, new ASN1OctetString(basicOCSPResponse.toDER()))))
										.toDER());
	}
}