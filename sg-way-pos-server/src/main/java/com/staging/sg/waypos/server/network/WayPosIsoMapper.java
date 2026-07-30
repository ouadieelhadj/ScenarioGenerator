package com.staging.sg.waypos.server.network;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import org.jpos.iso.ISOMsg;
import com.staging.sg.common.iso.WayPosOperationCatalog;
import com.staging.sg.common.iso.WayPosPrivateData;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Component
public class WayPosIsoMapper {
    private static final DateTimeFormatter DE7 =
            DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final DateTimeFormatter DE12 =
            DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DE13 =
            DateTimeFormatter.ofPattern("MMdd");

    public RoutingTransactionRequest toRequest(ISOMsg message) throws Exception {
        String transactionId = UUID.randomUUID().toString();
        String mti = message.getMTI();
        String stan = value(message, 11);
        String terminal = value(message, 41);
        String amount = value(message, 4);
        String idempotency = String.join(":",
                nullSafe(terminal), nullSafe(stan),
                nullSafe(originalRequestMti(mti)), nullSafe(amount));
        var operation = WayPosOperationCatalog.resolve(
                mti, value(message, 3), value(message, 24));
        Map<String, String> attributes = new HashMap<>();
        attributes.put("operationName", operation.name());
        put(attributes, "transmissionDateTime", value(message, 7));
        put(attributes, "networkId", value(message, 24));
        put(attributes, "entryMode", value(message, 22));
        put(attributes, "conditionCode", value(message, 25));
        put(attributes, "securityAdditionalData", value(message, 31));
        put(attributes, "originalDataElements", value(message, 56));
        put(attributes, "operationSpecificData", value(message, 60));
        put(attributes, "privateData63", value(message, 63));
        put(attributes, "targetAccount", targetAccount(value(message, 63)));
        return new RoutingTransactionRequest(
                "1.0", transactionId, transactionId, idempotency,
                operation.effect().name(),
                mti, value(message, 3),
                value(message, 2), value(message, 14), amount, value(message, 49),
                stan, value(message, 37), terminal, value(message, 42),
                message.hasField(52) ? org.jpos.iso.ISOUtil.hexString(message.getBytes(52)) : null,
                message.hasField(55) ? org.jpos.iso.ISOUtil.hexString(message.getBytes(55)) : null,
                null, Map.copyOf(attributes));
    }

    public ISOMsg toResponse(
            ISOMsg request, RoutingTransactionResponse routingResponse) throws Exception {
        ISOMsg response = new ISOMsg();
        response.setPackager(request.getPackager());
        response.setMTI(responseMti(request.getMTI()));
        copy(request, response, 2, 3, 4, 7, 11, 22, 25, 37, 41, 42, 49);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        response.set(12, now.format(DE12));
        response.set(13, now.format(DE13));
        response.set(39, routingResponse.posResponseCode());
        if (routingResponse.authorizationCode() != null) {
            response.set(38, routingResponse.authorizationCode());
        }
        if (routingResponse.arpcHex() != null) {
            response.set(55, org.jpos.iso.ISOUtil.hex2byte(routingResponse.arpcHex()));
        }
        return response;
    }

    public ISOMsg systemError(ISOMsg request) throws Exception {
        return error(request, "96");
    }

    public ISOMsg error(ISOMsg request, String responseCode) throws Exception {
        return toResponse(request, RoutingTransactionResponse.decline(
                UUID.randomUUID().toString(), responseCode, null));
    }

    private static String responseMti(String requestMti) {
        if (requestMti == null || requestMti.length() != 4) {
            throw new IllegalArgumentException("Invalid request MTI");
        }
        char[] value = requestMti.toCharArray();
        if (value[2] != '0' && value[2] != '2') {
            throw new IllegalArgumentException("Unsupported request MTI " + requestMti);
        }
        value[2] = (char) (value[2] + 1);
        return new String(value);
    }

    private static String originalRequestMti(String requestMti) {
        if (requestMti == null || requestMti.length() != 4) return requestMti;
        char[] value = requestMti.toCharArray();
        if (value[3] == '1') value[3] = '0';
        return new String(value);
    }

    private static void copy(ISOMsg source, ISOMsg target, int... fields)
            throws Exception {
        for (int field : fields) {
            if (source.hasField(field)) {
                if (source.getValue(field) instanceof byte[] binary) {
                    target.set(field, binary.clone());
                } else {
                    target.set(field, source.getString(field));
                }
            }
        }
    }

    private static String value(ISOMsg message, int field) {
        return message.hasField(field) ? message.getString(field) : null;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (value != null) map.put(key, value);
    }

    private static String targetAccount(String privateData) {
        if (privateData == null) return null;
        try {
            return WayPosPrivateData.decode(privateData).stream()
                    .filter(item -> "60".equals(item.tableId()))
                    .map(WayPosPrivateData.Item::value)
                    .map(WayPosIsoMapper::panFromAccountData)
                    .findFirst().orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String panFromAccountData(String value) {
        int separator = value.indexOf('=');
        if (separator < 0) separator = value.indexOf('D');
        return separator < 0 ? value : value.substring(0, separator);
    }
}
