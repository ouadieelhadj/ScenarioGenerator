package com.staging.sg.threeds.member.service;

import com.staging.sg.common.threeds.*;
import com.staging.sg.threeds.member.domain.MemberAuthentication;
import com.staging.sg.threeds.member.repository.MemberAuthenticationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MemberThreeDsService {
    public static final String VERSION = "2.3.1.1";
    private final MemberAuthenticationRepository authentications;
    private final SandboxEvidenceService evidence;
    private final RestClient network;
    private final String memberBaseUrl;
    private final String challengeOtp;
    private final Map<UUID, String> transientEvidence = new ConcurrentHashMap<>();

    public MemberThreeDsService(MemberAuthenticationRepository authentications,
            SandboxEvidenceService evidence,
            @Value("${three-ds.network.base-url:http://127.0.0.1:8561}") String networkUrl,
            @Value("${three-ds.member.base-url:http://127.0.0.1:8560}") String memberBaseUrl,
            @Value("${three-ds.sandbox.challenge-otp:}") String challengeOtp) {
        this.authentications = authentications;
        this.evidence = evidence;
        this.network = RestClient.builder().baseUrl(networkUrl).build();
        this.memberBaseUrl = memberBaseUrl;
        this.challengeOtp = challengeOtp;
    }

    public ThreeDsStartResponse start(ThreeDsStartRequest request) {
        validate(request);
        var existing = authentications.findByTransactionId(request.transactionId());
        if (existing.isPresent()) return response(existing.get());
        UUID serverId = UUID.randomUUID();
        MemberAuthentication auth = MemberAuthentication.create(
                request, serverId, evidence.panFingerprint(request.pan()));
        authentications.save(auth);
        ThreeDsAReq areq = new ThreeDsAReq("AReq", VERSION, serverId,
                request.transactionId(), request.correlationId(), request.program(),
                request.flow(), request.issuerMode(), ThreeDsServerMode.MEMBER,
                request.acquirerId(),
                request.merchantId(), request.amountMinor(), request.currency(),
                request.pan(), request.expiry());
        ThreeDsARes ares = network.post().uri("/api/3ds/network/v1/areq")
                .body(areq).retrieve().body(ThreeDsARes.class);
        if (ares == null) throw new IllegalStateException("Empty 3DS network response");
        auth = find(serverId);
        String proof = ares.authenticationValue();
        auth.apply(ares, evidence.fingerprint(proof));
        if (proof != null) transientEvidence.put(auth.id(), proof);
        authentications.save(auth);
        return response(auth, ares.challengeUrl());
    }

    public ThreeDsStartResponse status(UUID serverId) {
        return response(find(serverId));
    }

    @Transactional
    public ThreeDsVerificationResponse verify(ThreeDsVerificationRequest request) {
        validateVerification(request);
        MemberAuthentication auth = authentications.findByDsTransId(request.dsTransId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown 3DS Directory Server transaction"));
        boolean replayedForSameTransaction = auth.evidenceConsumedAt() != null;
        String fingerprint = evidence.fingerprint(request.authenticationValue());
        if (!auth.matchesVerification(request, fingerprint)) {
            return new ThreeDsVerificationResponse("1.0", false, false, true);
        }
        auth.consumeEvidence();
        authentications.save(auth);
        return new ThreeDsVerificationResponse("1.0", true,
                replayedForSameTransaction, true);
    }

    @Transactional
    public ThreeDsARes acsAuthenticate(ThreeDsAReq request, UUID dsTransId) {
        MemberAuthentication auth = authentications.findById(request.threeDSServerTransId())
                .orElseGet(() -> authentications.save(MemberAuthentication.create(
                        request, evidence.panFingerprint(request.pan()))));
        UUID acsTransId = UUID.randomUUID();
        ThreeDsTransStatus status = request.flow() == ThreeDsFlow.CHALLENGE
                ? ThreeDsTransStatus.C : ThreeDsTransStatus.Y;
        String proof = status == ThreeDsTransStatus.Y ? generate(auth, dsTransId, acsTransId) : null;
        String eci = status == ThreeDsTransStatus.Y ? eci(request.program()) : null;
        ThreeDsARes result = new ThreeDsARes("ARes", VERSION, auth.id(), dsTransId,
                acsTransId, request.program(), status, eci, proof,
                status == ThreeDsTransStatus.C
                        ? memberBaseUrl + "/api/3ds/member/v1/acs/creq" : null,
                true);
        auth.apply(result, evidence.fingerprint(proof));
        if (proof != null) transientEvidence.put(auth.id(), proof);
        authentications.save(auth);
        return result;
    }

    public ThreeDsCRes acsChallenge(ThreeDsCReq request) {
        MemberAuthentication auth = find(request.threeDSServerTransId());
        correlate(auth, request.dsTransId(), request.acsTransId());
        if (auth.transStatus() != ThreeDsTransStatus.C) {
            throw new IllegalStateException("3DS challenge is not pending");
        }
        auth.registerChallengeAttempt();
        authentications.save(auth);
        boolean valid = auth.challengeAttempts() <= 3
                && evidence.otpMatches(request.challengeData(), challengeOtp);
        ThreeDsTransStatus status = valid ? ThreeDsTransStatus.Y : ThreeDsTransStatus.N;
        String proof = valid ? generate(auth, request.dsTransId(), request.acsTransId()) : null;
        ThreeDsRReq rreq = new ThreeDsRReq("RReq", VERSION, auth.id(),
                request.dsTransId(), request.acsTransId(), auth.program(), status,
                valid ? eci(auth.program()) : null, proof, true);
        ThreeDsRRes rres = network.post().uri("/api/3ds/network/v1/rreq")
                .body(rreq).retrieve().body(ThreeDsRRes.class);
        if (rres == null || !rres.accepted()) {
            throw new IllegalStateException("3DS result was not acknowledged");
        }
        auth = find(auth.id());
        auth.apply(rreq, evidence.fingerprint(proof));
        if (proof != null) transientEvidence.put(auth.id(), proof);
        authentications.save(auth);
        return new ThreeDsCRes("CRes", VERSION, auth.id(), request.dsTransId(),
                request.acsTransId(), status, true);
    }

    @Transactional
    public ThreeDsRRes receiveResult(ThreeDsRReq request) {
        MemberAuthentication auth = find(request.threeDSServerTransId());
        auth.apply(request, evidence.fingerprint(request.authenticationValue()));
        if (request.authenticationValue() != null) {
            transientEvidence.put(auth.id(), request.authenticationValue());
        }
        authentications.save(auth);
        return new ThreeDsRRes("RRes", VERSION, auth.id(), request.dsTransId(),
                request.acsTransId(), true);
    }

    private ThreeDsStartResponse response(MemberAuthentication auth) {
        return response(auth, auth.transStatus() == ThreeDsTransStatus.C
                ? memberBaseUrl + "/api/3ds/member/v1/acs/creq" : null);
    }

    private ThreeDsStartResponse response(MemberAuthentication auth, String challengeUrl) {
        return new ThreeDsStartResponse(VERSION, auth.id(), auth.dsTransId(),
                auth.acsTransId(), auth.program(), auth.transStatus(), auth.eci(),
                transientEvidence.get(auth.id()), challengeUrl, true);
    }

    private String generate(MemberAuthentication auth, UUID ds, UUID acs) {
        return evidence.evidence(auth.id() + "|" + ds + "|" + acs + "|"
                + auth.program() + "|" + auth.amountMinor() + "|" + auth.currency()
                + "|" + auth.merchantId());
    }

    private MemberAuthentication find(UUID id) {
        return authentications.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown 3DS authentication"));
    }

    private static void correlate(MemberAuthentication auth, UUID ds, UUID acs) {
        if (!ds.equals(auth.dsTransId()) || !acs.equals(auth.acsTransId())) {
            throw new IllegalStateException("3DS challenge correlation mismatch");
        }
    }

    private static String eci(ThreeDsProgram program) {
        return program == ThreeDsProgram.VISA ? "05" : "02";
    }

    private static void validate(ThreeDsStartRequest r) {
        if (r == null || !"1.0".equals(r.schemaVersion()) || blank(r.transactionId())
                || blank(r.correlationId()) || r.program() == null || r.flow() == null
                || r.flow() == ThreeDsFlow.NOT_REQUESTED || r.issuerMode() == null
                || blank(r.acquirerId()) || blank(r.merchantId()) || r.amountMinor() <= 0
                || r.currency() == null || !r.currency().matches("\\d{3}")
                || r.pan() == null || !r.pan().matches("\\d{12,19}")
                || r.expiry() == null || !r.expiry().matches("\\d{4}")) {
            throw new IllegalArgumentException("Invalid 3DS start request");
        }
    }

    private static void validateVerification(ThreeDsVerificationRequest r) {
        if (r == null || !"1.0".equals(r.schemaVersion())
                || blank(r.transactionId()) || r.dsTransId() == null
                || r.program() == null || blank(r.eci())
                || blank(r.authenticationValue()) || blank(r.merchantReference())
                || r.amountMinor() <= 0 || r.currency() == null
                || !r.currency().matches("\\d{3}")) {
            throw new IllegalArgumentException("Invalid 3DS verification request");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
