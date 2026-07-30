package com.staging.sg.waypos.server.service;

import com.staging.sg.common.emv.McDmasEmv;
import com.staging.sg.common.iso.WayPosBerTlv;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.waypos.server.domain.PosCard;
import org.jpos.iso.ISOUtil;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WayPosLocalEmvService {
    private final McDmasEmv emv;

    public WayPosLocalEmvService(McDmasEmv emv) {
        this.emv = emv;
    }

    public Validation validate(
            RoutingTransactionRequest request, PosCard card) {
        if (request.emvDataHex() == null) return Validation.notPresent();
        if (card.getMdkUnderLmk() == null || card.getMdkKcv() == null
                || card.getMdkLength() == null || card.getArpcArcHex() == null) {
            return Validation.failed(Status.NOT_CONFIGURED);
        }
        try {
            Map<Integer, byte[]> tags = tags(ISOUtil.hex2byte(request.emvDataHex()));
            require(tags, 0x9F26, 0x9F37, 0x9F36, 0x95, 0x9A, 0x9C,
                    0x9F02, 0x5F2A, 0x82, 0x9F1A, 0x9F10);
            if (!request.amount().equals(hex(tags.get(0x9F02)))
                    || !leftPad(request.currency(), 4).equals(hex(tags.get(0x5F2A)))) {
                return Validation.failed(Status.MALFORMED);
            }
            int atc = Integer.parseInt(hex(tags.get(0x9F36)), 16);
            if (!card.isAtcFresh(atc)) {
                return Validation.failed(Status.REPLAY);
            }
            McDmasEmv.EmvInput input = new McDmasEmv.EmvInput();
            input.mdkUnderLmk = card.getMdkUnderLmk();
            input.mdkKcv = card.getMdkKcv();
            input.mdkLenBytes = card.getMdkLength();
            input.pan = request.pan();
            input.psn = card.getPanSequenceNumber();
            input.atc = atc;
            input.amount = hex(tags.get(0x9F02));
            input.otherAmount = tags.containsKey(0x9F03)
                    ? hex(tags.get(0x9F03)) : "000000000000";
            input.countryCode = hex(tags.get(0x9F1A));
            input.currency = hex(tags.get(0x5F2A));
            input.date = hex(tags.get(0x9A));
            input.txType = hex(tags.get(0x9C));
            input.tvr = hex(tags.get(0x95));
            input.unpredictable = hex(tags.get(0x9F37));
            input.aip = hex(tags.get(0x82));
            input.iad = hex(tags.get(0x9F10));
            input.cvmResults = tags.containsKey(0x9F34)
                    ? hex(tags.get(0x9F34)) : "000000";
            input.aid = tags.containsKey(0x84) ? hex(tags.get(0x84)) : null;
            input.appVersion = tags.containsKey(0x9F09)
                    ? hex(tags.get(0x9F09)) : null;

            String receivedArqc = hex(tags.get(0x9F26));
            String calculatedArqc = emv.recomputeArqc(input);
            if (!java.security.MessageDigest.isEqual(
                    ISOUtil.hex2byte(receivedArqc), ISOUtil.hex2byte(calculatedArqc))) {
                return Validation.failed(Status.INVALID_ARQC);
            }
            card.recordAtc(atc);
            return new Validation(Status.VERIFIED, input, receivedArqc);
        } catch (IllegalArgumentException e) {
            return Validation.failed(Status.MALFORMED);
        } catch (Exception e) {
            return Validation.failed(Status.ERROR);
        }
    }

    public String approvalResponse(PosCard card, Validation validation) {
        if (validation.status() != Status.VERIFIED) return null;
        try {
            String arpc = emv.computeArpc(
                    validation.input(), validation.arqc(), card.getArpcArcHex());
            byte[] issuerAuthenticationData =
                    ISOUtil.hex2byte(arpc + card.getArpcArcHex());
            return ISOUtil.hexString(WayPosBerTlv.encode(List.of(
                    new WayPosBerTlv.Tlv(0x91, issuerAuthenticationData))));
        } catch (Exception e) {
            throw new IllegalStateException("ARPC generation failed", e);
        }
    }

    private static Map<Integer, byte[]> tags(byte[] de55) {
        Map<Integer, byte[]> result = new HashMap<>();
        for (WayPosBerTlv.Tlv tlv : WayPosBerTlv.decode(de55)) {
            if (result.putIfAbsent(tlv.tag(), tlv.value()) != null) {
                throw new IllegalArgumentException("Duplicate EMV tag");
            }
        }
        return result;
    }

    private static void require(Map<Integer, byte[]> tags, int... required) {
        for (int tag : required) {
            if (!tags.containsKey(tag)) {
                throw new IllegalArgumentException("Missing EMV tag "
                        + Integer.toHexString(tag).toUpperCase());
            }
        }
    }

    private static String hex(byte[] value) {
        return ISOUtil.hexString(value).toUpperCase();
    }

    private static String leftPad(String value, int length) {
        return "0".repeat(Math.max(0, length - value.length())) + value;
    }

    public enum Status {
        NOT_PRESENT, VERIFIED, INVALID_ARQC, REPLAY, MALFORMED,
        NOT_CONFIGURED, ERROR
    }

    public record Validation(Status status, McDmasEmv.EmvInput input, String arqc) {
        static Validation notPresent() {
            return new Validation(Status.NOT_PRESENT, null, null);
        }
        static Validation failed(Status status) {
            return new Validation(status, null, null);
        }
    }
}
