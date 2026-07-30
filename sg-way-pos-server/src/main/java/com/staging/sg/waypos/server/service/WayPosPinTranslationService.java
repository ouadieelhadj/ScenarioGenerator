package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.waypos.server.domain.PosInterfaceKey;
import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import com.staging.sg.waypos.server.repository.PosInterfaceKeyRepository;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.jpos.iso.ISOUtil;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class WayPosPinTranslationService {
    private final PosTerminalProfileRepository terminals;
    private final PosInterfaceKeyRepository interfaces;
    private final JposHsmService hsm;

    public WayPosPinTranslationService(
            PosTerminalProfileRepository terminals,
            PosInterfaceKeyRepository interfaces,
            JposHsmService hsm) {
        this.terminals = terminals;
        this.interfaces = interfaces;
        this.hsm = hsm;
    }

    public RoutingTransactionRequest translate(
            String route, RoutingTransactionRequest request) {
        if (request.pinBlockHex() == null) return request;
        PosTerminalProfile terminal = terminals.findById(request.terminalId())
                .orElseThrow(() -> new IllegalStateException("Unknown terminal"));
        if (terminal.getTpkUnderLmk() == null || terminal.getTpkKcv() == null
                || terminal.getTpkLength() == null) {
            throw new IllegalStateException("Terminal TPK is not active");
        }
        PosInterfaceKey destination = interfaces.findById(route)
                .filter(PosInterfaceKey::isActive)
                .orElseThrow(() -> new IllegalStateException(
                        "Destination PEK is not active for " + route));
        try {
            byte[] translated = hsm.translatePinBlock(
                    ISOUtil.hex2byte(request.pinBlockHex()), request.pan(),
                    terminal.getTpkUnderLmk(), terminal.getTpkKcv(),
                    terminal.getTpkLength(),
                    destination.getPekUnderLmk(), destination.getPekKcv(),
                    destination.getPekLength());
            Map<String, String> attributes = new HashMap<>(
                    request.attributes() == null ? Map.of() : request.attributes());
            attributes.put("pinBlockKeyDomain", route);
            return copyWithPinBlock(request, ISOUtil.hexString(translated), attributes);
        } catch (Exception e) {
            throw new IllegalStateException("HSM PIN block translation failed", e);
        }
    }

    public void provisionInterfaceKey(
            String interfaceCode, String underLmk, String kcv, int length) {
        if (interfaceCode == null || interfaceCode.isBlank()
                || underLmk == null || underLmk.isBlank()
                || kcv == null || !kcv.matches("(?i)[0-9a-f]{6}")
                || (length != 8 && length != 16)) {
            throw new IllegalArgumentException("Invalid interface PEK metadata");
        }
        PosInterfaceKey key = interfaces.findById(interfaceCode)
                .orElseGet(() -> PosInterfaceKey.active(
                        interfaceCode, underLmk, kcv.toUpperCase(), length));
        key.replace(underLmk, kcv.toUpperCase(), length);
        interfaces.save(key);
    }

    private static RoutingTransactionRequest copyWithPinBlock(
            RoutingTransactionRequest r, String pinBlock, Map<String, String> attributes) {
        return new RoutingTransactionRequest(
                r.schemaVersion(), r.transactionId(), r.correlationId(),
                r.idempotencyKey(), r.operation(), r.sourceMti(),
                r.processingCode(), r.pan(), r.expiry(), r.amount(), r.currency(),
                r.stan(), r.rrn(), r.terminalId(), r.merchantId(), pinBlock,
                r.emvDataHex(), r.originalTransactionId(), Map.copyOf(attributes));
    }
}
