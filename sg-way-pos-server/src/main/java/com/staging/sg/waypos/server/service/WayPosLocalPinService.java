package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.waypos.server.domain.PosCard;
import com.staging.sg.waypos.server.domain.PosSecurityKey;
import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import com.staging.sg.waypos.server.repository.PosSecurityKeyRepository;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.jpos.iso.ISOUtil;
import org.springframework.stereotype.Service;

@Service
public class WayPosLocalPinService {
    public static final String PVK_A = "LOCAL_PVK_A";
    public static final String PVK_B = "LOCAL_PVK_B";

    private final PosTerminalProfileRepository terminals;
    private final PosSecurityKeyRepository securityKeys;
    private final JposHsmService hsm;

    public WayPosLocalPinService(
            PosTerminalProfileRepository terminals,
            PosSecurityKeyRepository securityKeys,
            JposHsmService hsm) {
        this.terminals = terminals;
        this.securityKeys = securityKeys;
        this.hsm = hsm;
    }

    public Verification verify(RoutingTransactionRequest request, PosCard card) {
        if (request.pinBlockHex() == null) return Verification.NOT_PRESENT;
        if (card.getPinPvv() == null || card.getPinPvki() == null) {
            return Verification.NOT_CONFIGURED;
        }
        PosTerminalProfile terminal = terminals.findById(request.terminalId()).orElse(null);
        PosSecurityKey a = securityKeys.findById(PVK_A)
                .filter(PosSecurityKey::isActive).orElse(null);
        PosSecurityKey b = securityKeys.findById(PVK_B)
                .filter(PosSecurityKey::isActive).orElse(null);
        if (terminal == null || terminal.getTpkUnderLmk() == null
                || terminal.getTpkKcv() == null || terminal.getTpkLength() == null
                || a == null || b == null) {
            return Verification.NOT_CONFIGURED;
        }
        try {
            boolean verified = hsm.verifyPinPvv(
                    ISOUtil.hex2byte(request.pinBlockHex()), request.pan(),
                    terminal.getTpkUnderLmk(), terminal.getTpkKcv(),
                    terminal.getTpkLength(),
                    a.getKeyUnderLmk(), a.getKcv(),
                    b.getKeyUnderLmk(), b.getKcv(),
                    card.getPinPvki(), card.getPinPvv());
            return verified ? Verification.VERIFIED : Verification.INVALID;
        } catch (Exception e) {
            return Verification.ERROR;
        }
    }

    public PinUpdate updatePvv(
            RoutingTransactionRequest request, PosCard card,
            String newPinBlockHex) {
        if (newPinBlockHex == null
                || !newPinBlockHex.matches("(?i)[0-9a-f]{16}")) {
            return PinUpdate.INVALID_BLOCK;
        }
        if (card.getPinPvki() == null) return PinUpdate.NOT_CONFIGURED;
        PosTerminalProfile terminal = terminals.findById(
                request.terminalId()).orElse(null);
        PosSecurityKey a = securityKeys.findById(PVK_A)
                .filter(PosSecurityKey::isActive).orElse(null);
        PosSecurityKey b = securityKeys.findById(PVK_B)
                .filter(PosSecurityKey::isActive).orElse(null);
        if (terminal == null || terminal.getTpkUnderLmk() == null
                || terminal.getTpkKcv() == null || terminal.getTpkLength() == null
                || a == null || b == null) {
            return PinUpdate.NOT_CONFIGURED;
        }
        try {
            String pvv = hsm.calculatePinPvv(
                    ISOUtil.hex2byte(newPinBlockHex), request.pan(),
                    terminal.getTpkUnderLmk(), terminal.getTpkKcv(),
                    terminal.getTpkLength(),
                    a.getKeyUnderLmk(), a.getKcv(),
                    b.getKeyUnderLmk(), b.getKcv(), card.getPinPvki());
            card.updatePinPvv(pvv);
            return PinUpdate.UPDATED;
        } catch (Exception e) {
            return PinUpdate.ERROR;
        }
    }

    public enum Verification {
        NOT_PRESENT, VERIFIED, INVALID, NOT_CONFIGURED, ERROR
    }

    public enum PinUpdate {
        UPDATED, INVALID_BLOCK, NOT_CONFIGURED, ERROR
    }
}
