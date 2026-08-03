package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.WayPosOperationCatalog;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.iso.crypto.WayPosMac;
import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import com.staging.sg.waypos.server.domain.PosTerminalKey;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.Arrays;

@Service
public class WayPosSecurityService {
    private final PosTerminalProfileRepository terminals;
    private final JposHsmService hsm;
    private final WayPosKeyExchangeService keyExchange;
    private final WayPosInitialKeyChangeAuthenticator initialKeyChange;

    public WayPosSecurityService(
            PosTerminalProfileRepository terminals, JposHsmService hsm,
            WayPosKeyExchangeService keyExchange,
            WayPosInitialKeyChangeAuthenticator initialKeyChange) {
        this.terminals = terminals;
        this.hsm = hsm;
        this.keyExchange = keyExchange;
        this.initialKeyChange = initialKeyChange;
    }

    public ValidatedTerminal validate(ISOMsg request) throws Exception {
        String terminalId = request.hasField(41) ? request.getString(41) : null;
        PosTerminalProfile profile = terminalId == null ? null
                : terminals.findById(terminalId).orElse(null);
        if (profile == null || !profile.isEnabled()) {
            throw new PosSecurityException("03", "Unknown or disabled terminal");
        }
        var operation = WayPosOperationCatalog.resolve(
                request.getMTI(),
                request.hasField(3) ? request.getString(3) : null,
                request.hasField(24) ? request.getString(24) : null);
        if (operation.extended() && !profile.isExtendedSet()) {
            throw new PosSecurityException(
                    "57", "Extended operation disabled for terminal");
        }
        if (!request.hasField(64)) {
            if (profile.isMacRequired()
                    && !initialKeyChange.authenticates(request)) {
                throw new PosSecurityException("63", "MAC required");
            }
            return new ValidatedTerminal(profile, null, null, null);
        }
        byte[] received = request.getBytes(64);
        byte[] packed = request.pack();
        byte[] data = Arrays.copyOf(packed, packed.length - 4);
        if (validMac(data, received, profile.getTakUnderLmk(),
                profile.getTakKcv(), profile.getTakLength(), profile)) {
            return new ValidatedTerminal(
                    profile, profile.getTakUnderLmk(),
                    profile.getTakKcv(), profile.getTakLength());
        }
        if (isKeyLifecycleMessage(request)) {
            for (PosTerminalKey candidate :
                    keyExchange.candidateAuthenticationKeys(terminalId)) {
                if (validMac(data, received, candidate.getKeyUnderLmk(),
                        candidate.getKcv(), candidate.getKeyLength(), profile)) {
                    return new ValidatedTerminal(
                            profile, candidate.getKeyUnderLmk(),
                            candidate.getKcv(), candidate.getKeyLength());
                }
            }
        }
        throw new PosSecurityException("63", "Invalid MAC");
    }

    public void protectResponse(
            ISOMsg request, ISOMsg response, ValidatedTerminal terminal) throws Exception {
        if (!request.hasField(64)) return;
        if (terminal.takUnderLmk() == null || terminal.kcv() == null
                || terminal.keyLength() == null) {
            throw new PosSecurityException("96", "TAK not provisioned");
        }
        response.set(64, new byte[4]);
        byte[] packed = response.pack();
        byte[] data = Arrays.copyOf(packed, packed.length - 4);
        response.set(64, hsm.generateWayPosMac(
                data, terminal.takUnderLmk(), terminal.kcv(),
                terminal.keyLength(), mode(terminal.profile())));
    }

    private static WayPosMac.DataMode mode(PosTerminalProfile profile) {
        return WayPosMac.DataMode.valueOf(profile.getMacData());
    }

    private boolean validMac(
            byte[] data, byte[] received, String underLmk, String kcv,
            Integer length, PosTerminalProfile profile) throws Exception {
        if (underLmk == null || kcv == null || length == null) return false;
        byte[] expected = hsm.generateWayPosMac(
                data, underLmk, kcv, length, mode(profile));
        return MessageDigest.isEqual(received, expected);
    }

    private static boolean isKeyLifecycleMessage(ISOMsg request) {
        try {
            return request.getMTI().startsWith("08")
                    && ("960000".equals(request.getString(3))
                    || "930000".equals(request.getString(3)));
        } catch (Exception e) {
            return false;
        }
    }

    public record ValidatedTerminal(
            PosTerminalProfile profile,
            String takUnderLmk,
            String kcv,
            Integer keyLength) {}

    public static final class PosSecurityException extends RuntimeException {
        private final String responseCode;
        public PosSecurityException(String responseCode, String message) {
            super(message);
            this.responseCode = responseCode;
        }
        public String responseCode() { return responseCode; }
    }
}
