package com.staging.sg.threeds.network.service;

import com.staging.sg.common.threeds.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ThreeDsNetworkSimulatorService {
    public static final String VERSION = "2.3.1.1";
    private final RestClient member;
    private final RestClient merchantSite;
    private final String networkBaseUrl;
    private final byte[] evidenceKey;
    private final String challengeOtp;
    private final Map<UUID, ExternalSession> externalSessions = new ConcurrentHashMap<>();
    private final Map<UUID, ThreeDsServerMode> resultOwners = new ConcurrentHashMap<>();

    public ThreeDsNetworkSimulatorService(
            @Value("${three-ds.member.base-url:http://127.0.0.1:8560}") String memberUrl,
            @Value("${three-ds.merchant-site.base-url:http://127.0.0.1:8551}") String merchantSiteUrl,
            @Value("${three-ds.network.base-url:http://127.0.0.1:8561}") String networkBaseUrl,
            @Value("${three-ds.sandbox.hmac-key:}") String evidenceKey,
            @Value("${three-ds.sandbox.challenge-otp:}") String challengeOtp) {
        this.member = RestClient.builder().baseUrl(memberUrl).build();
        this.merchantSite = RestClient.builder().baseUrl(merchantSiteUrl).build();
        this.networkBaseUrl = networkBaseUrl;
        this.evidenceKey = evidenceKey.getBytes(StandardCharsets.UTF_8);
        this.challengeOtp = challengeOtp;
    }

    public ThreeDsARes authenticate(ThreeDsAReq request) {
        validate(request);
        UUID dsTransId = UUID.randomUUID();
        resultOwners.put(request.threeDSServerTransId(), request.serverMode());
        if (request.issuerMode() == ThreeDsIssuerMode.MEMBER) {
            ThreeDsARes response = member.post()
                    .uri(uri -> uri.path("/api/3ds/member/v1/acs/areq")
                            .queryParam("dsTransId", dsTransId).build())
                    .body(request).retrieve().body(ThreeDsARes.class);
            if (response == null) throw new IllegalStateException("Empty member ACS response");
            return response;
        }
        UUID acsTransId = UUID.randomUUID();
        ThreeDsTransStatus status = request.flow() == ThreeDsFlow.CHALLENGE
                ? ThreeDsTransStatus.C : ThreeDsTransStatus.Y;
        ExternalSession session = new ExternalSession(request, dsTransId, acsTransId);
        externalSessions.put(acsTransId, session);
        String proof = status == ThreeDsTransStatus.Y ? evidence(session) : null;
        return new ThreeDsARes("ARes", VERSION, request.threeDSServerTransId(),
                dsTransId, acsTransId, request.program(), status,
                status == ThreeDsTransStatus.Y ? eci(request.program()) : null,
                proof, status == ThreeDsTransStatus.C
                    ? networkBaseUrl + "/api/3ds/network/v1/external-acs/creq" : null,
                true);
    }

    public ThreeDsCRes challenge(ThreeDsCReq request) {
        ExternalSession session = externalSessions.get(request.acsTransId());
        if (session == null || !session.matches(request)) {
            throw new IllegalStateException("External ACS challenge correlation mismatch");
        }
        session.attempts++;
        boolean valid = session.attempts <= 3 && constantEquals(
                request.challengeData(), challengeOtp);
        ThreeDsTransStatus status = valid ? ThreeDsTransStatus.Y : ThreeDsTransStatus.N;
        String proof = valid ? evidence(session) : null;
        ThreeDsRReq result = new ThreeDsRReq("RReq", VERSION,
                request.threeDSServerTransId(), request.dsTransId(),
                request.acsTransId(), session.request.program(), status,
                valid ? eci(session.request.program()) : null, proof, true);
        ThreeDsRRes acknowledgment = routeResult(result);
        if (!acknowledgment.accepted()) {
            throw new IllegalStateException("3DS Server rejected external ACS result");
        }
        return new ThreeDsCRes("CRes", VERSION, request.threeDSServerTransId(),
                request.dsTransId(), request.acsTransId(), status, true);
    }

    public ThreeDsRRes routeResult(ThreeDsRReq request) {
        ThreeDsServerMode owner = resultOwners.get(request.threeDSServerTransId());
        if (owner == null) {
            throw new IllegalStateException("Unknown 3DS Server result owner");
        }
        RestClient resultServer = owner == ThreeDsServerMode.MEMBER
                ? member : merchantSite;
        String resultPath = owner == ThreeDsServerMode.MEMBER
                ? "/api/3ds/member/v1/results"
                : "/api/merchant-site-simulator/v1/3ds/results";
        ThreeDsRRes response = resultServer.post().uri(resultPath)
                .body(request).retrieve().body(ThreeDsRRes.class);
        if (response == null) throw new IllegalStateException("Empty 3DS Server RRes");
        return response;
    }

    private String evidence(ExternalSession session) {
        if (evidenceKey.length < 32) {
            throw new IllegalStateException(
                    "THREE_DS_NETWORK_SANDBOX_HMAC_KEY must contain at least 32 characters");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(evidenceKey, "HmacSHA256"));
            String context = session.request.threeDSServerTransId() + "|"
                    + session.dsTransId + "|" + session.acsTransId + "|"
                    + session.request.program() + "|" + session.request.amountMinor()
                    + "|" + session.request.currency() + "|" + session.request.merchantId();
            return Base64.getEncoder().encodeToString(Arrays.copyOf(
                    mac.doFinal(context.getBytes(StandardCharsets.UTF_8)), 20));
        } catch (Exception e) {
            throw new IllegalStateException("External ACS evidence generation failed", e);
        }
    }

    private static boolean constantEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static String eci(ThreeDsProgram program) {
        return program == ThreeDsProgram.VISA ? "05" : "02";
    }

    private static void validate(ThreeDsAReq request) {
        if (request == null || !"AReq".equals(request.messageType())
                || !VERSION.equals(request.messageVersion()) || request.program() == null
                || request.flow() == null || request.issuerMode() == null
                || request.serverMode() == null
                || request.threeDSServerTransId() == null) {
            throw new IllegalArgumentException("Invalid 3DS AReq");
        }
    }

    private static final class ExternalSession {
        private final ThreeDsAReq request;
        private final UUID dsTransId;
        private final UUID acsTransId;
        private int attempts;

        private ExternalSession(ThreeDsAReq request, UUID dsTransId, UUID acsTransId) {
            this.request = request;
            this.dsTransId = dsTransId;
            this.acsTransId = acsTransId;
        }

        private boolean matches(ThreeDsCReq challenge) {
            return request.threeDSServerTransId().equals(challenge.threeDSServerTransId())
                    && dsTransId.equals(challenge.dsTransId())
                    && acsTransId.equals(challenge.acsTransId());
        }
    }
}
