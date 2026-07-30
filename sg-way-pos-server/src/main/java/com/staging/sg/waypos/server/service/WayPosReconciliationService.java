package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.WayPosPrivateData;
import com.staging.sg.waypos.server.domain.PosAuthorization;
import com.staging.sg.waypos.server.domain.PosBatchUpload;
import com.staging.sg.waypos.server.repository.PosAuthorizationRepository;
import com.staging.sg.waypos.server.repository.PosBatchUploadRepository;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WayPosReconciliationService {
    private final PosAuthorizationRepository authorizations;
    private final PosBatchUploadRepository uploads;

    public WayPosReconciliationService(
            PosAuthorizationRepository authorizations,
            PosBatchUploadRepository uploads) {
        this.authorizations = authorizations;
        this.uploads = uploads;
    }

    public boolean matches(
            String terminalId, String batchId, String de63, boolean useBatchUpload) {
        try {
            Map<TotalKey, Total> requested = parseTotals(de63);
            Map<TotalKey, Total> expected = useBatchUpload
                    ? totalsFromUploads(terminalId, batchId)
                    : totalsFromJournal(terminalId, batchId);
            Set<String> currencies = new HashSet<>();
            requested.keySet().forEach(key -> currencies.add(key.currency()));
            if (currencies.isEmpty()) return false;
            for (String currency : currencies) {
                expected.putIfAbsent(
                        new TotalKey("D", "1", "O", currency), Total.ZERO);
                expected.putIfAbsent(
                        new TotalKey("C", "1", "O", currency), Total.ZERO);
            }
            return requested.equals(expected);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Transactional
    public boolean recordBatchUpload(
            ISOMsg request, String terminalId, String expectedBatchId) {
        try {
            WayPosPrivateData.Item batchInfo = WayPosPrivateData
                    .decode(request.getString(63)).stream()
                    .filter(item -> "BI".equals(item.tableId()))
                    .findFirst().orElseThrow();
            if (batchInfo.value().length() != 10) return false;
            String originalMti = batchInfo.value().substring(0, 4);
            String batchId = batchInfo.value().substring(4);
            if (!batchId.equals(expectedBatchId)
                    || !(originalMti.startsWith("02") || originalMti.startsWith("04"))
                    || !request.hasField(4) || !request.hasField(49)) {
                return false;
            }
            String fingerprint = fingerprint(request.pack());
            if (!uploads.existsByTerminalIdAndBatchIdAndMessageFingerprint(
                    terminalId, batchId, fingerprint)) {
                uploads.save(PosBatchUpload.received(
                        terminalId, batchId, fingerprint, normalizeMti(originalMti),
                        request.getString(3),
                        request.hasField(24) ? request.getString(24) : null,
                        Long.parseLong(request.getString(4)), request.getString(49),
                        request.hasField(39) ? request.getString(39) : null));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<TotalKey, Total> totalsFromJournal(
            String terminalId, String batchId) {
        Map<TotalKey, Total> totals = new HashMap<>();
        for (PosAuthorization value :
                authorizations.findByTerminalIdAndBatchIdAndStatusIn(
                        terminalId, batchId, List.of("APPROVED", "REVERSED"))) {
            Classification classification = classify(
                    value.getMti(), value.getProcessingCode(), value.getNetworkId());
            if (classification != null && value.getAmountMinor() != null) {
                add(totals, classification, value.getCurrency(), value.getAmountMinor());
            }
        }
        return totals;
    }

    private Map<TotalKey, Total> totalsFromUploads(
            String terminalId, String batchId) {
        Map<TotalKey, Total> totals = new HashMap<>();
        for (PosBatchUpload value :
                uploads.findByTerminalIdAndBatchIdOrderById(terminalId, batchId)) {
            if (value.getResponseCode() != null
                    && !List.of("00", "10").contains(value.getResponseCode())) {
                continue;
            }
            Classification classification = classify(
                    value.getOriginalMti(), value.getProcessingCode(),
                    value.getNetworkId());
            if (classification != null) {
                add(totals, classification, value.getCurrency(), value.getAmountMinor());
            }
        }
        return totals;
    }

    private static Map<TotalKey, Total> parseTotals(String de63) {
        List<WayPosPrivateData.Item> items = WayPosPrivateData.decode(de63);
        String pc = items.stream().filter(item -> "PC".equals(item.tableId()))
                .map(WayPosPrivateData.Item::value).findFirst().orElseThrow();
        if (pc.length() < 5 || pc.charAt(0) != '2' || pc.charAt(4) != '1') {
            throw new IllegalArgumentException("Unsupported reconciliation scheme");
        }
        Map<TotalKey, Total> totals = new HashMap<>();
        for (WayPosPrivateData.Item item : items) {
            if (!"28".equals(item.tableId())) continue;
            String value = item.value();
            if (value.isEmpty() || value.length() % 21 != 0) {
                throw new IllegalArgumentException("Invalid reconciliation totals");
            }
            for (int offset = 0; offset < value.length(); offset += 21) {
                String group = value.substring(offset, offset + 21);
                TotalKey key = new TotalKey(
                        group.substring(0, 1), group.substring(1, 2),
                        group.substring(2, 3), group.substring(6, 9));
                if (!key.type().matches("[DCAPIT]")
                        || !key.online().matches("[01]")
                        || !key.reversal().matches("[OR]")
                        || !key.currency().matches("\\d{3}")) {
                    throw new IllegalArgumentException("Invalid reconciliation group");
                }
                Total total = new Total(
                        Integer.parseInt(group.substring(3, 6)),
                        Long.parseLong(group.substring(9, 21)));
                if (totals.putIfAbsent(key, total) != null) {
                    throw new IllegalArgumentException("Duplicate reconciliation group");
                }
            }
        }
        return totals;
    }

    private static Classification classify(
            String mti, String processingCode, String networkId) {
        String type = mti == null ? "" : normalizeMti(mti);
        String prefix = processingCode == null || processingCode.length() < 2
                ? "" : processingCode.substring(0, 2);
        boolean credit = List.of("21", "24", "25", "29", "48").contains(prefix);
        if (type.startsWith("020")) {
            if ("20".equals(prefix)) return new Classification("D", "1", "R");
            if ("23".equals(prefix)) return new Classification("C", "1", "O");
            if (credit) return new Classification("C", "1", "O");
            if (List.of("00", "09", "13", "50", "52").contains(prefix)) {
                return new Classification("D", "1", "O");
            }
            return null;
        }
        if (type.startsWith("022")) {
            if ("102".equals(networkId)) {
                return new Classification("D", "1", "O");
            }
            if ("200".equals(networkId)) return new Classification("D", "0", "O");
            if ("59".equals(prefix)
                    || ("202".equals(networkId) && !"02".equals(prefix))) {
                return new Classification("D", "1", "O");
            }
            return null;
        }
        if (type.startsWith("040") || type.startsWith("042")) {
            if ("402".equals(networkId)) return null;
            return new Classification(credit ? "C" : "D", "1", "R");
        }
        return null;
    }

    private static void add(
            Map<TotalKey, Total> values, Classification classification,
            String currency, long amount) {
        TotalKey key = new TotalKey(
                classification.type(), classification.online(),
                classification.reversal(), currency);
        values.merge(key, new Total(1, amount),
                (left, right) -> new Total(
                        Math.addExact(left.count(), right.count()),
                        Math.addExact(left.amount(), right.amount())));
    }

    private static String fingerprint(byte[] packed) throws Exception {
        return ISOUtil.hexString(
                MessageDigest.getInstance("SHA-256").digest(packed));
    }

    private static String normalizeMti(String mti) {
        if (mti.length() != 4) return mti;
        char[] value = mti.toCharArray();
        value[3] = '0';
        return new String(value);
    }

    private record Classification(String type, String online, String reversal) {}
    private record TotalKey(String type, String online, String reversal, String currency) {}
    private record Total(int count, long amount) {
        private static final Total ZERO = new Total(0, 0);
    }
}
