package com.staging.sg.swam.lis.member.service;

import com.staging.sg.swam.lis.common.codec.LisRecordCodec;
import com.staging.sg.swam.lis.common.model.*;
import com.staging.sg.swam.lis.common.packager.LisPackagerRegistry;
import com.staging.sg.swam.lis.common.service.*;
import com.staging.sg.swam.lis.member.persistence.*;
import com.staging.sg.swam.lis.member.repository.*;
import org.jpos.iso.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.*;
import java.util.HexFormat;

@Service
public class MemberLisImportService {
    private final MemberBusinessDayRepository dayRepository;
    private final MemberLisFileRepository fileRepository;
    private final MemberClearingTransactionRepository clearingRepository;
    private final MemberChargebackRepository chargebackRepository;
    private final String memberId;
    private final Path incomingDirectory;
    private final ClearingIdentityService identity;

    public MemberLisImportService(MemberBusinessDayRepository dayRepository,
            MemberLisFileRepository fileRepository,
            MemberClearingTransactionRepository clearingRepository,
            MemberChargebackRepository chargebackRepository,
            @Value("${swam.lis.bank-member-id}") String memberId,
            @Value("${swam.lis.incoming-directory}") String incomingDirectory,
            @Value("${swam.lis.pan-fingerprint-salt}") String salt) {
        this.dayRepository = dayRepository; this.fileRepository = fileRepository;
        this.clearingRepository = clearingRepository; this.chargebackRepository = chargebackRepository;
        this.memberId = memberId;
        this.incomingDirectory = Path.of(incomingDirectory).toAbsolutePath().normalize();
        this.identity = new ClearingIdentityService(salt);
    }

    @Transactional
    public LisImportResult importFile(String originalName, byte[] bytes) throws IOException, ISOException {
        String digest = HexFormat.of().formatHex(sha256(bytes));
        if (fileRepository.findByDirectionAndSha256(LisDirection.INCOMING, digest).isPresent()) {
            throw new IllegalStateException("LIS incoming already imported: " + digest);
        }
        ParsedLisFile parsed = new LisIncomingFileReader(
                new LisRecordCodec(new LisPackagerRegistry())).read(bytes);
        LocalDate date = LocalDate.parse(parsed.processingDate(),
                DateTimeFormatter.ofPattern("ddMMyy").withResolverStyle(ResolverStyle.SMART));
        MemberBusinessDay day = dayRepository.findByBankMemberIdAndBusinessDate(memberId, date)
                .orElseGet(() -> {
                    MemberBusinessDay value = new MemberBusinessDay();
                    value.setBankMemberId(memberId); value.setBusinessDate(date);
                    return dayRepository.save(value);
                });
        Files.createDirectories(incomingDirectory);
        String safeName = Path.of(originalName == null ? "incoming.lis" : originalName)
                .getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        Path target = incomingDirectory.resolve(digest.substring(0, 12) + "_" + safeName).normalize();
        if (!target.startsWith(incomingDirectory)) throw new SecurityException("Invalid LIS incoming path");
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        MemberLisFile file = new MemberLisFile();
        file.setBusinessDayId(day.getId()); file.setDirection(LisDirection.INCOMING);
        file.setFileName(safeName); file.setStoragePath(target.toString());
        file.setSourceMember(parsed.originatorBankCode()); file.setDestinationMember(memberId);
        file.setProcessingDate(date); file.setFileSequence(parsed.fileSequence());
        file.setSha256(digest); file.setByteSize(bytes.length);
        file.setPhysicalRecordCount(parsed.physicalRecordCount()); file.setStatus(LisFileStatus.PROCESSING);
        file = fileRepository.save(file);

        long matched = 0, lisOnly = 0;
        for (LisFinancialRecord financial : parsed.financialRecords()) {
            IncomingValues value = values(financial);
            String key = identity.functionalKey(memberId, value.rrn, null, value.authorization,
                    ClearingIdentityService.canonicalTransactionDate(value.at), value.amount, value.currency);
            var local = clearingRepository.findByFunctionalKey(key);
            MemberClearingTransaction resolved;
            if (local.isPresent()) {
                MemberClearingTransaction row = local.get();
                row.setIncomingLisFileId(file.getId()); row.setIncomingRecordSequence(value.sequence);
                row.setSourcePresence(SourcePresence.BOTH); row.setMatchStatus(MatchStatus.MATCHED);
                row.setAccountingStatus(AccountingStatus.READY); row.setUpdatedAt(LocalDateTime.now());
                resolved = clearingRepository.save(row); matched++;
            } else {
                MemberClearingTransaction row = new MemberClearingTransaction();
                row.setBankMemberId(memberId); row.setBusinessDayId(day.getId());
                row.setIncomingLisFileId(file.getId()); row.setIncomingRecordSequence(value.sequence);
                row.setFunctionalKey(key); row.setTransactionType(value.type);
                row.setClearingCycle(value.cycle); row.setPanFingerprint(identity.panFingerprint(value.pan));
                row.setMaskedPan(identity.maskPan(value.pan)); row.setRrn(value.rrn);
                row.setAuthorizationCode(value.authorization); row.setTransactionAt(value.at);
                row.setProcessingDate(date); row.setMcc(value.tcr0.getString(8));
                row.setTerminalId(value.tcr0.getString(12)); row.setMerchantId(value.tcr0.getString(4));
                row.setMerchantName(value.tcr0.getString(5)); row.setMerchantCity(value.tcr0.getString(6));
                row.setTransactionAmount(value.amount); row.setTransactionCurrency(value.currency);
                row.setSettlementAmount(Long.parseLong(value.tcr1.getString(15)));
                row.setSettlementCurrency(value.tcr1.getString(29));
                row.setSourcePresence(SourcePresence.LIS_ONLY); row.setMatchStatus(MatchStatus.LIS_ONLY);
                row.setAccountingStatus(AccountingStatus.READY);
                resolved = clearingRepository.save(row); lisOnly++;
            }
            receiveChargebackIfApplicable(financial, resolved, file, parsed.originatorBankCode());
        }
        file.setStatus(LisFileStatus.PROCESSED); fileRepository.save(file);
        return new LisImportResult(file.getId(), parsed.financialRecords().size(),
                matched, lisOnly, matched + lisOnly, file.getStatus().name());
    }

    private void receiveChargebackIfApplicable(LisFinancialRecord financial,
            MemberClearingTransaction transaction, MemberLisFile file, String originator) {
        String tc = financial.tcr0().getString(0);
        if (!java.util.Set.of("15", "16", "17").contains(tc)) return;
        String reason = financial.tcr0().getString(16);
        int cycle = Integer.parseInt(financial.tcr0().getString(13));
        if (chargebackRepository.existsByClearingTransactionIdAndDirectionAndCycleNumberAndReasonCode(
                transaction.getId(), ChargebackDirection.RECEIVED, cycle, reason)) return;
        MemberChargeback chargeback = new MemberChargeback();
        chargeback.setBankMemberId(memberId); chargeback.setClearingTransactionId(transaction.getId());
        chargeback.setDirection(ChargebackDirection.RECEIVED); chargeback.setStatus(ChargebackStatus.RECEIVED);
        chargeback.setTransactionCode(tc); chargeback.setCycleNumber(cycle); chargeback.setReasonCode(reason);
        chargeback.setChargebackReference(financial.tcr0().getString(17));
        chargeback.setAmount(Long.parseLong(financial.tcr1().getString(11)));
        chargeback.setCurrency(financial.tcr1().getString(28));
        chargeback.setSourceLisFileId(file.getId());
        chargeback.setSourceRecordSequence(Integer.parseInt(financial.tcr0().getString(1)));
        chargeback.setCounterpartyMember(originator); chargeback.setReceivedAt(LocalDateTime.now());
        chargeback.setCreatedBy("LIS_IMPORT");
        chargebackRepository.save(chargeback);
        transaction.setDisputeStatus(DisputeStatus.OPEN); clearingRepository.save(transaction);
    }

    private static IncomingValues values(LisFinancialRecord record) {
        ISOMsg zero = record.tcr0(), one = record.tcr1();
        LocalDate date = LocalDate.parse(one.getString(3),
                DateTimeFormatter.ofPattern("ddMMyy").withResolverStyle(ResolverStyle.SMART));
        LocalTime time = LocalTime.parse(one.getString(27), DateTimeFormatter.ofPattern("HHmmss"));
        String type = switch (zero.getString(0)) {
            case "07", "17", "27", "37" -> "CASH_WITHDRAWAL";
            case "06", "16", "26", "36" -> "CASH_ADVANCE";
            default -> "PURCHASE"; };
        return new IncomingValues(Integer.parseInt(zero.getString(1)), zero, one, type,
                zero.getString(20).trim(), one.getString(26).trim(), one.getString(4).trim(),
                LocalDateTime.of(date, time), Long.parseLong(one.getString(11)),
                one.getString(28), "2".equals(zero.getString(13)) ? 2 : 1);
    }

    private record IncomingValues(int sequence, ISOMsg tcr0, ISOMsg tcr1, String type,
            String pan, String rrn, String authorization, LocalDateTime at,
            long amount, String currency, int cycle) {}
    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
