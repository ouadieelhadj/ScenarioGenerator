package com.staging.sg.waypos.simulator.service;

import com.staging.sg.common.iso.WayPosLengthChannel;
import com.staging.sg.common.iso.WayPosKeyExchangeCodec;
import com.staging.sg.common.iso.WayPosMessageValidator;
import com.staging.sg.common.iso.WayPosPackager;
import com.staging.sg.common.iso.crypto.WayPosMac;
import com.staging.sg.waypos.simulator.api.SimulatorFieldMapRequest;
import com.staging.sg.waypos.simulator.api.SimulatorKeyChangeResponse;
import com.staging.sg.waypos.simulator.api.SimulatorKeyConfirmationResponse;
import com.staging.sg.waypos.simulator.api.SimulatorTransactionRequest;
import com.staging.sg.waypos.simulator.api.SimulatorTransactionResponse;
import com.staging.sg.waypos.simulator.config.SimulatorProperties;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WayPosSimulatorClient {
    private static final DateTimeFormatter DE7 = DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final DateTimeFormatter DE12 = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DE13 = DateTimeFormatter.ofPattern("MMdd");

    private final SimulatorProperties properties;
    private final WayPosPackager packager;
    private final SimulatorStan stans;
    private final SimulatorKeyStore keyStore;
    private final Map<String, ISOMsg> lastRequests = new ConcurrentHashMap<>();

    public WayPosSimulatorClient(
            SimulatorProperties properties, WayPosPackager packager,
            SimulatorStan stans, SimulatorKeyStore keyStore) {
        this.properties = properties;
        this.packager = packager;
        this.stans = stans;
        this.keyStore = keyStore;
    }

    public SimulatorTransactionResponse send(SimulatorTransactionRequest input) throws Exception {
        String stan = stans.next();
        ISOMsg request = build(input, stan);
        boolean macEnabled = Boolean.TRUE.equals(input.macEnabled());
        if (macEnabled) {
            applyActiveMac(request);
        }
        remember(request);
        return exchange(request, macEnabled);
    }

    public SimulatorTransactionResponse sendFieldMap(
            SimulatorFieldMapRequest input) throws Exception {
        ISOMsg request = buildFieldMap(input);
        boolean macEnabled = Boolean.TRUE.equals(input.macEnabled());
        if (macEnabled) {
            applyActiveMac(request);
        }
        remember(request);
        return exchange(request, macEnabled);
    }

    ISOMsg buildFieldMap(SimulatorFieldMapRequest input) throws Exception {
        if (input == null) {
            throw new IllegalArgumentException("request is required");
        }
        ISOMsg message = new ISOMsg();
        message.setPackager(packager);
        message.setMTI(required(input.mti(), "mti"));
        setFieldMap(message, input.fields(), false);
        setFieldMap(message, input.binaryFields(), true);
        if (input.pin() != null && !input.pin().isBlank()) {
            if (message.hasField(52)) {
                throw new IllegalArgumentException(
                        "DE52 and clear certification PIN are mutually exclusive");
            }
            message.set(52, keyStore.encryptIso0PinBlock(
                    input.pin(), message.getString(2)));
        }
        if (input.unsetFields() != null) {
            for (Integer field : input.unsetFields()) {
                if (field != null) message.unset(field);
            }
        }
        if (!Boolean.FALSE.equals(input.validate())) {
            WayPosMessageValidator.validateRequest(message);
        }
        return message;
    }

    public SimulatorTransactionResponse repeat(
            String terminalId, Boolean macEnabled) throws Exception {
        ISOMsg request = prepareRepeat(terminalId, macEnabled);
        return exchange(request, request.hasField(64));
    }

    ISOMsg prepareRepeat(
            String terminalId, Boolean macEnabled) throws Exception {
        String terminal = fixed(defaultValue(
                terminalId, properties.terminalId()), 8);
        ISOMsg previous = lastRequests.get(terminal);
        if (previous == null) {
            throw new IllegalArgumentException(
                    "No previous transaction for terminal " + terminal.trim());
        }
        ISOMsg request = (ISOMsg) previous.clone();
        request.setPackager(packager);
        request.setMTI(repeatedMti(request.getMTI()));
        boolean protect = macEnabled != null
                ? macEnabled : previous.hasField(64);
        if (protect) {
            applyActiveMac(request);
        } else if (request.hasField(64)) {
            request.unset(64);
        }
        return request;
    }

    private SimulatorTransactionResponse exchange(
            ISOMsg request, boolean macEnabled) throws Exception {
        long started = System.nanoTime();
        WayPosLengthChannel channel =
                new WayPosLengthChannel(properties.host(), properties.port(), packager);
        try {
            channel.setTimeout(properties.timeoutSeconds() * 1000);
            channel.connect();
            channel.send(request);
            ISOMsg response = channel.receive();
            validateResponse(request, response);
            boolean verified = !macEnabled
                    ? !response.hasField(64)
                    : response.hasField(64) && verifyActiveMac(response);
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            String rc = response.hasField(39) ? response.getString(39) : null;
            return new SimulatorTransactionResponse(
                    request.getMTI(), response.getMTI(), request.getString(11),
                    response.hasField(37) ? response.getString(37) : request.getString(37),
                    rc, response.hasField(38) ? response.getString(38) : null,
                    "00".equals(rc) || "10".equals(rc), verified, elapsed,
                    response.hasField(60) ? response.getString(60) : null,
                    response.hasField(55)
                            ? ISOUtil.hexString(response.getBytes(55)) : null);
        } finally {
            if (channel.isConnected()) channel.disconnect();
        }
    }

    public SimulatorKeyChangeResponse keyChange(boolean confirm) throws Exception {
        Exchange first = exchangeKeys();
        List<WayPosKeyExchangeCodec.KeyStatus> statuses = List.of();
        if ("00".equals(first.responseCode()) && first.macVerified()) {
            List<WayPosKeyExchangeCodec.KeyBlock> blocks = new ArrayList<>();
            if (first.message().hasField(48)) {
                blocks.addAll(WayPosKeyExchangeCodec.decodeResponse(
                        first.message().getBytes(48)));
            }
            if (first.message().hasField(59)) {
                blocks.addAll(WayPosKeyExchangeCodec.decodeResponse(
                        first.message().getBytes(59)));
            }
            statuses = keyStore.importBlocks(blocks);
        }
        Exchange confirmation = null;
        if (confirm && !statuses.isEmpty()) {
            confirmation = confirmKeys();
        }
        return new SimulatorKeyChangeResponse(
                first.responseCode(), first.macVerified(), statuses,
                confirmation != null,
                confirmation == null ? null : confirmation.responseCode(),
                confirmation != null && confirmation.macVerified());
    }

    public SimulatorKeyConfirmationResponse confirmKeyStatuses() throws Exception {
        List<WayPosKeyExchangeCodec.KeyStatus> statuses = keyStore.statuses();
        if (statuses.isEmpty()) {
            throw new IllegalStateException(
                    "No imported key status is available for confirmation");
        }
        Exchange confirmation = confirmKeys();
        boolean confirmed = "00".equals(confirmation.responseCode())
                && confirmation.macVerified();
        return new SimulatorKeyConfirmationResponse(
                confirmation.responseCode(), confirmation.macVerified(),
                List.copyOf(statuses), confirmed);
    }

    ISOMsg build(SimulatorTransactionRequest input, String stan) throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ISOMsg message = new ISOMsg();
        message.setPackager(packager);
        String mti = defaultValue(input.mti(), "0200");
        message.setMTI(mti);
        message.set(7, now.format(DE7));
        message.set(11, stan);
        message.set(12, now.format(DE12));
        message.set(13, now.format(DE13));
        message.set(41, fixed(defaultValue(input.terminalId(), properties.terminalId()), 8));
        if (input.merchantId() != null && !input.merchantId().isBlank()) {
            message.set(42, fixed(input.merchantId(), 15));
        }
        if (mti.startsWith("01") || mti.startsWith("02")) {
            financialFields(message, input, now, stan);
        } else if (mti.startsWith("04")) {
            reversalFields(message, input, now, stan);
        } else if (mti.startsWith("05")) {
            message.set(3, defaultValue(input.processingCode(), "920000"));
            message.set(60, required(
                    input.operationSpecificData(), "operationSpecificData"));
        } else if ("0320".equals(mti) || "0321".equals(mti)) {
            message.set(3, defaultValue(input.processingCode(), "000000"));
            message.set(4, required(input.amount(), "amount"));
            message.set(49, properties.currency());
        } else if ("0302".equals(mti)) {
            message.set(47, ISOUtil.hex2byte(
                    required(input.fileDataHex(), "fileDataHex")));
        } else if (mti.startsWith("08")) {
            message.set(3, required(input.processingCode(), "processingCode"));
        } else {
            throw new IllegalArgumentException(
                    "Unsupported simulator MTI " + mti);
        }
        setIfPresent(message, 24, input.networkId());
        setIfPresent(message, 31, input.securityAdditionalData());
        setBinaryIfPresent(message, 47, input.fileDataHex());
        setBinaryIfPresent(message, 48, input.keyDataHex());
        setBinaryIfPresent(message, 59, input.overflowDataHex());
        setIfPresent(message, 56, input.originalDataElements());
        setIfPresent(message, 60, input.operationSpecificData());
        message.set(63, defaultValue(input.privateData(), "007SV1.0.0"));
        if (input.pinBlockHex() != null) message.set(52, ISOUtil.hex2byte(input.pinBlockHex()));
        if (input.emvDataHex() != null) message.set(55, ISOUtil.hex2byte(input.emvDataHex()));
        return message;
    }

    private void financialFields(
            ISOMsg message, SimulatorTransactionRequest input,
            ZonedDateTime now, String stan) throws Exception {
        message.set(2, required(input.pan(), "pan"));
        message.set(3, defaultValue(input.processingCode(), "000000"));
        message.set(4, required(input.amount(), "amount"));
        message.set(14, required(input.expiry(), "expiry"));
        message.set(22, defaultValue(input.entryMode(), "051"));
        message.set(25, defaultValue(input.conditionCode(), "00"));
        setIfPresent(message, 37, input.rrn());
        message.set(43, fixed("WAY POS TEST CASABLANCA MA", 40));
        message.set(49, properties.currency());
    }

    private void reversalFields(
            ISOMsg message, SimulatorTransactionRequest input,
            ZonedDateTime now, String stan) throws Exception {
        message.set(2, required(input.pan(), "pan"));
        message.set(3, defaultValue(input.processingCode(), "000000"));
        message.set(4, required(input.amount(), "amount"));
        message.set(24, required(input.networkId(), "networkId"));
        message.set(37, defaultValue(
                input.rrn(), now.format(DE13) + stan + "00"));
        message.set(49, properties.currency());
        message.set(60, required(
                input.operationSpecificData(), "operationSpecificData"));
    }

    private Exchange exchangeKeys() throws Exception {
        String stan = stans.next();
        ISOMsg request = buildInitialKeyChange(stan);
        ISOMsg response = send(request);
        boolean verified = !response.hasField(64);
        return new Exchange(response,
                response.hasField(39) ? response.getString(39) : null, verified);
    }

    ISOMsg buildInitialKeyChange(String stan) throws Exception {
        ISOMsg request = buildSystem("0800", "960000", stan);
        request.set(48, WayPosKeyExchangeCodec.encodeStatusDetails(
                keyStore.initialMasterKeyStatuses()));
        return request;
    }

    private Exchange confirmKeys() throws Exception {
        String stan = stans.next();
        ISOMsg request = buildKeyConfirmation(stan);
        byte[] requestTak = keyStore.activeTak();
        try {
            applyMac(request, requestTak);
            ISOMsg response = send(request);
            boolean verified = response.hasField(64)
                    && verifyMac(response, requestTak);
            return new Exchange(response,
                    response.hasField(39) ? response.getString(39) : null,
                    verified);
        } finally {
            Arrays.fill(requestTak, (byte) 0);
        }
    }

    ISOMsg buildKeyConfirmation(String stan) throws Exception {
        List<WayPosKeyExchangeCodec.KeyStatus> statuses = keyStore.statuses();
        if (statuses.isEmpty()) {
            throw new IllegalStateException(
                    "No imported key status is available for confirmation");
        }
        ISOMsg request = buildSystem("0800", "930000", stan);
        request.set(48, WayPosKeyExchangeCodec.encodeStatuses(statuses));
        return request;
    }

    private ISOMsg buildSystem(String mti, String processingCode, String stan) throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ISOMsg message = new ISOMsg();
        message.setPackager(packager);
        message.setMTI(mti);
        message.set(3, processingCode);
        message.set(7, now.format(DE7));
        message.set(11, stan);
        message.set(41, fixed(properties.terminalId(), 8));
        message.set(63, "007SV1.0.0");
        return message;
    }

    private ISOMsg send(ISOMsg request) throws Exception {
        WayPosLengthChannel channel =
                new WayPosLengthChannel(properties.host(), properties.port(), packager);
        try {
            channel.setTimeout(properties.timeoutSeconds() * 1000);
            channel.connect();
            channel.send(request);
            return channel.receive();
        } finally {
            if (channel.isConnected()) channel.disconnect();
        }
    }

    private void applyMac(ISOMsg message, byte[] key) throws Exception {
        message.set(64, new byte[4]);
        byte[] packed = message.pack();
        byte[] data = Arrays.copyOf(packed, packed.length - 4);
        message.set(64, WayPosMac.calculate(key, data, mode()));
    }

    private boolean verifyMac(ISOMsg message, byte[] key) throws Exception {
        byte[] received = message.getBytes(64);
        byte[] packed = message.pack();
        byte[] data = Arrays.copyOf(packed, packed.length - 4);
        return MessageDigest.isEqual(received, WayPosMac.calculate(key, data, mode()));
    }

    private void applyActiveMac(ISOMsg message) throws Exception {
        byte[] key = keyStore.activeTak();
        try {
            applyMac(message, key);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private boolean verifyActiveMac(ISOMsg message) throws Exception {
        byte[] key = keyStore.activeTak();
        try {
            return verifyMac(message, key);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private WayPosMac.DataMode mode() {
        return WayPosMac.DataMode.valueOf(properties.macMode().toUpperCase());
    }

    void remember(ISOMsg request) throws Exception {
        if (!request.hasField(41)) return;
        ISOMsg stored = (ISOMsg) request.clone();
        stored.setPackager(packager);
        lastRequests.put(request.getString(41), stored);
    }

    static void validateResponse(
            ISOMsg request, ISOMsg response) throws Exception {
        String expectedMti = responseMti(request.getMTI());
        if (!expectedMti.equals(response.getMTI())) {
            throw new IllegalStateException(
                    "Expected response MTI " + expectedMti
                            + ", received " + response.getMTI());
        }
        for (int field : new int[] {2, 3, 4, 7, 11, 41, 49}) {
            if (request.hasField(field)
                    && (!response.hasField(field)
                    || !request.getString(field)
                    .equals(response.getString(field)))) {
                throw new IllegalStateException(
                        "Response correlation mismatch DE" + field);
            }
        }
    }

    private static String responseMti(String requestMti) {
        char[] value = requestMti.toCharArray();
        if (value.length != 4 || (value[2] != '0' && value[2] != '2')) {
            throw new IllegalArgumentException(
                    "Unsupported request MTI " + requestMti);
        }
        value[2] = (char) (value[2] + 1);
        return new String(value);
    }

    private static String repeatedMti(String mti) {
        if (mti == null || mti.length() != 4) {
            throw new IllegalArgumentException("Invalid repeat MTI");
        }
        char[] value = mti.toCharArray();
        value[3] = '1';
        return new String(value);
    }

    private static void setIfPresent(
            ISOMsg message, int field, String value) throws Exception {
        if (value != null && !value.isBlank()) message.set(field, value);
    }

    private static void setBinaryIfPresent(
            ISOMsg message, int field, String value) throws Exception {
        if (value != null && !value.isBlank()) {
            message.set(field, ISOUtil.hex2byte(value));
        }
    }

    private static void setFieldMap(
            ISOMsg message, Map<String, String> fields, boolean binary)
            throws Exception {
        if (fields == null) return;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            int field = fieldNumber(entry.getKey());
            if (field == 1 || field == 64) {
                throw new IllegalArgumentException(
                        "DE" + field + " is managed by the simulator");
            }
            String value = required(entry.getValue(), "DE" + field);
            if (binary) {
                message.set(field, ISOUtil.hex2byte(value));
            } else {
                message.set(field, value);
            }
        }
    }

    private static int fieldNumber(String name) {
        String normalized = required(name, "field name")
                .trim().toUpperCase();
        if (normalized.startsWith("DE")) normalized = normalized.substring(2);
        int field;
        try {
            field = Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid ISO field " + name, e);
        }
        if (field < 2 || field > 63) {
            throw new IllegalArgumentException(
                    "Only primary-bitmap fields DE2..DE63 are supported");
        }
        return field;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String fixed(String value, int length) {
        if (value.length() > length) return value.substring(0, length);
        return value + " ".repeat(length - value.length());
    }

    private record Exchange(ISOMsg message, String responseCode, boolean macVerified) {}
}
