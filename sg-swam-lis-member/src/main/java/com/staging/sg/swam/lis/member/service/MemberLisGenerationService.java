package com.staging.sg.swam.lis.member.service;

import com.staging.sg.common.entity.SwamAcqTransaction;
import com.staging.sg.common.repository.SwamAcqTransactionRepository;
import com.staging.sg.swam.lis.common.codec.LisRecordCodec;
import com.staging.sg.swam.lis.common.model.*;
import com.staging.sg.swam.lis.common.packager.LisPackagerRegistry;
import com.staging.sg.swam.lis.common.service.*;
import com.staging.sg.swam.lis.member.persistence.*;
import com.staging.sg.swam.lis.member.repository.*;
import org.jpos.iso.ISOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MemberLisGenerationService {
    private static final DateTimeFormatter LIS_DATE = DateTimeFormatter.ofPattern("ddMMyy");
    private final MemberBusinessDayRepository dayRepository;
    private final MemberClearingTransactionRepository clearingRepository;
    private final MemberLisFileRepository fileRepository;
    private final MemberChargebackRepository chargebackRepository;
    private final SwamAcqTransactionRepository sourceRepository;
    private final String memberId;
    private final String memberBankCode;
    private final Path outputDirectory;

    public MemberLisGenerationService(MemberBusinessDayRepository dayRepository,
            MemberClearingTransactionRepository clearingRepository,
            MemberLisFileRepository fileRepository, MemberChargebackRepository chargebackRepository,
            SwamAcqTransactionRepository sourceRepository,
            @Value("${swam.lis.bank-member-id}") String memberId,
            @Value("${swam.lis.bank-code}") String memberBankCode,
            @Value("${swam.lis.output-directory}") String outputDirectory) {
        this.dayRepository = dayRepository;
        this.clearingRepository = clearingRepository;
        this.fileRepository = fileRepository;
        this.chargebackRepository = chargebackRepository;
        this.sourceRepository = sourceRepository;
        this.memberId = memberId;
        this.memberBankCode = memberBankCode;
        this.outputDirectory = Path.of(outputDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public LisFileResult generate(LocalDate date, String destinationBankCode) throws IOException, ISOException {
        MemberBusinessDay day = dayRepository.findByBankMemberIdAndBusinessDate(memberId, date)
                .orElseThrow(() -> new IllegalStateException("EOD member absent for " + date));
        List<MemberClearingTransaction> clearing = clearingRepository
                .findByBusinessDayIdAndLocalSourceTypeOrderById(day.getId(), "SWAM_ACQ");
        Map<Long, SwamAcqTransaction> sources = new HashMap<>();
        sourceRepository.findAllById(clearing.stream().map(MemberClearingTransaction::getLocalSidTransactionId).toList())
                .forEach(source -> sources.put(source.getId(), source));
        LisFinancialRecordFactory factory = new LisFinancialRecordFactory();
        List<LisFinancialRecord> records = new ArrayList<>(clearing.stream().map(row -> {
            SwamAcqTransaction source = Optional.ofNullable(sources.get(row.getLocalSidTransactionId()))
                    .orElseThrow(() -> new IllegalStateException("Source SID member absente: " + row.getLocalSidTransactionId()));
            return factory.create(data(row, source));
        }).toList());
        List<MemberChargeback> disputes = chargebackRepository
                .findByStatusOrderById(ChargebackStatus.READY_TO_SEND);
        LisDisputeRecordFactory disputeFactory = new LisDisputeRecordFactory();
        for (MemberChargeback dispute : disputes) {
            MemberClearingTransaction row = clearingRepository.findById(dispute.getClearingTransactionId())
                    .orElseThrow(() -> new IllegalStateException("Clearing transaction dispute absente"));
            LisFinancialRecord original = originalRecord(row, sources, factory);
            records.add(disputeFactory.create(original, dispute.getTransactionCode(),
                    dispute.getCycleNumber(), dispute.getReasonCode(), dispute.getChargebackReference(),
                    dispute.getAmount(), dispute.getManualReason()));
        }
        int fileSequence = (int) fileRepository.count() + 1;
        byte[] bytes = new LisOutgoingFileAssembler(new LisRecordCodec(new LisPackagerRegistry()))
                .assemble(destinationBankCode, LIS_DATE.format(date), fileSequence, false, memberBankCode, records);
        Files.createDirectories(outputDirectory);
        String name = "LIS_MEMBER_%s_%03d.dat".formatted(date.toString().replace("-", ""), fileSequence);
        Path target = outputDirectory.resolve(name).normalize();
        if (!target.startsWith(outputDirectory)) throw new SecurityException("Invalid LIS output path");
        Path temporary = Files.createTempFile(outputDirectory, name, ".tmp");
        Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        String digest = HexFormat.of().formatHex(sha256(bytes));
        MemberLisFile file = new MemberLisFile();
        file.setBusinessDayId(day.getId()); file.setDirection(LisDirection.OUTGOING);
        file.setFileName(name); file.setStoragePath(target.toString());
        file.setSourceMember(memberId); file.setDestinationMember(destinationBankCode);
        file.setProcessingDate(date); file.setFileSequence(fileSequence);
        file.setSha256(digest); file.setByteSize(bytes.length);
        file.setPhysicalRecordCount(bytes.length / 256); file.setStatus(LisFileStatus.GENERATED);
        file = fileRepository.save(file);
        for (MemberChargeback dispute : disputes) {
            dispute.setOutgoingLisFileId(file.getId());
            dispute.setStatus(dispute.getParentChargebackId() == null
                    ? ChargebackStatus.SENT : ChargebackStatus.REPRESENTED);
            dispute.setEmittedAt(java.time.LocalDateTime.now());
            dispute.setUpdatedAt(java.time.LocalDateTime.now());
            chargebackRepository.save(dispute);
        }
        return new LisFileResult(file.getId(), name, target.toString(), digest,
                bytes.length, bytes.length / 256, file.getStatus().name());
    }

    private LisFinancialRecord originalRecord(MemberClearingTransaction row,
            Map<Long, SwamAcqTransaction> sources, LisFinancialRecordFactory factory)
            throws IOException, ISOException {
        SwamAcqTransaction source = sources.get(row.getLocalSidTransactionId());
        if (source == null && row.getLocalSidTransactionId() != null) {
            source = sourceRepository.findById(row.getLocalSidTransactionId()).orElse(null);
        }
        if (source != null) return factory.create(data(row, source));
        if (row.getIncomingLisFileId() == null || row.getIncomingRecordSequence() == null)
            throw new IllegalStateException("No original presentation available for dispute");
        MemberLisFile incoming = fileRepository.findById(row.getIncomingLisFileId()).orElseThrow();
        ParsedLisFile parsed = new LisIncomingFileReader(new LisRecordCodec(new LisPackagerRegistry()))
                .read(Files.readAllBytes(Path.of(incoming.getStoragePath())));
        return parsed.financialRecords().stream()
                .filter(record -> Integer.parseInt(record.tcr0().getString(1))
                        == row.getIncomingRecordSequence())
                .findFirst().orElseThrow(() -> new IllegalStateException("Incoming LIS source record absent"));
    }

    private LisFinancialData data(MemberClearingTransaction row, SwamAcqTransaction source) {
        long settlement = row.getSettlementAmount() == null ? row.getTransactionAmount() : row.getSettlementAmount();
        return new LisFinancialData(row.getTransactionType(), source.getPan(), source.getExpiryDate(),
                row.getStan(), row.getRrn(), row.getAuthorizationCode(), row.getTransactionAt(),
                row.getMerchantId(), row.getMerchantName(), row.getMerchantCity(),
                source.getAcquirerCountryCode(), row.getMcc(), row.getTerminalId(),
                source.getAcquirerInstitutionId(), source.getForwardingInstitutionId(),
                row.getTransactionAmount(), row.getTransactionCurrency(), settlement,
                row.getSettlementCurrency(), row.getBillingAmount(), row.getBillingCurrency(),
                source.getCardSequenceNumber(), " ", row.getClearingCycle());
    }

    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
