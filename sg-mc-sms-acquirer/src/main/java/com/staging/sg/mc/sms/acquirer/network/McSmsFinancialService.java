package com.staging.sg.mc.sms.acquirer.network;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class McSmsFinancialService {
    private final McSmsJposClient client;
    private final AtomicInteger sequence = new AtomicInteger(1);

    public McSmsFinancialService(McSmsJposClient client) {
        this.client = client;
    }

    public Map<String, Object> send(RoutingTransactionRequest routed) throws Exception {
        if (routed.pinBlockHex() != null
                && (routed.attributes() == null
                || !"MASTERCARD_SMS".equals(
                routed.attributes().get("pinBlockKeyDomain")))) {
            throw new IllegalArgumentException(
                    "PIN block is not in the Mastercard SMS PEK domain");
        }
        ISOMsg request = build(routed);
        ISOMsg response = client.sendAndWait(request, 30);
        String rc = response.hasField(39) ? response.getString(39) : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mti_sent", request.getMTI());
        result.put("mti_received", response.getMTI());
        result.put("stan", request.getString(11));
        result.put("response_code", rc);
        result.put("authorization_code",
                response.hasField(38) ? response.getString(38) : null);
        result.put("de55_response_hex", response.hasField(55)
                ? ISOUtil.hexString(response.getBytes(55)) : null);
        result.put("approved", "00".equals(rc) || "10".equals(rc));
        result.put("response_hex", ISOUtil.hexString(response.pack()));
        return result;
    }

    private ISOMsg build(RoutingTransactionRequest routed) throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ISOMsg message = new ISOMsg();
        message.setPackager(client.getPackager());
        message.setMTI(networkMti(routed));
        message.set(2, routed.pan());
        message.set(3, routed.processingCode());
        message.set(4, routed.amount());
        message.set(7, now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));
        message.set(11, "%06d".formatted(sequence.getAndUpdate(
                value -> value >= 999_999 ? 1 : value + 1)));
        message.set(12, now.format(DateTimeFormatter.ofPattern("HHmmss")));
        message.set(13, now.format(DateTimeFormatter.ofPattern("MMdd")));
        if (routed.expiry() != null) message.set(14, routed.expiry());
        message.set(22, attribute(routed, "entryMode", "051"));
        message.set(25, attribute(routed, "conditionCode", "00"));
        if (routed.rrn() != null) message.set(37, routed.rrn());
        if (routed.terminalId() != null) message.set(41, routed.terminalId());
        if (routed.merchantId() != null) message.set(42, routed.merchantId());
        message.set(49, routed.currency());
        if (routed.emvDataHex() != null) {
            message.set(55, ISOUtil.hex2byte(routed.emvDataHex()));
        }
        if (routed.pinBlockHex() != null) {
            message.set(52, ISOUtil.hex2byte(routed.pinBlockHex()));
        }
        if (networkMti(routed).startsWith("04")) {
            String original = attribute(routed, "originalDataElements", null);
            if (original != null) message.set(90, original);
        }
        return message;
    }

    private static String networkMti(RoutingTransactionRequest request) {
        if ("REVERSAL".equals(request.operation())) {
            return request.sourceMti() != null && request.sourceMti().startsWith("042")
                    ? "0420" : "0400";
        }
        if ("ADVICE".equals(request.operation()) || "CAPTURE".equals(request.operation())) return "0220";
        return "0200";
    }

    private static String attribute(
            RoutingTransactionRequest request, String name, String fallback) {
        return request.attributes() == null
                ? fallback : request.attributes().getOrDefault(name, fallback);
    }
}
