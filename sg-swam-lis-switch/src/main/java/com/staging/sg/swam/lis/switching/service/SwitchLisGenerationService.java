package com.staging.sg.swam.lis.switching.service;

import com.staging.sg.common.entity.SwamIssTransaction;
import com.staging.sg.common.repository.SwamIssTransactionRepository;
import com.staging.sg.swam.lis.common.codec.LisRecordCodec;
import com.staging.sg.swam.lis.common.model.*;
import com.staging.sg.swam.lis.common.packager.LisPackagerRegistry;
import com.staging.sg.swam.lis.common.service.*;
import com.staging.sg.swam.lis.switching.persistence.*;
import com.staging.sg.swam.lis.switching.repository.*;
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
public class SwitchLisGenerationService {
    private static final DateTimeFormatter LIS_DATE = DateTimeFormatter.ofPattern("ddMMyy");
    private final SwitchBusinessDayRepository dayRepository;
    private final SwitchClearingTransactionRepository clearingRepository;
    private final SwitchLisFileRepository fileRepository;
    private final SwitchChargebackRepository chargebackRepository;
    private final SwamIssTransactionRepository sourceRepository;
    private final String switchMemberId;
    private final String switchBankCode;
    private final Path outputDirectory;

    public SwitchLisGenerationService(SwitchBusinessDayRepository dayRepository,
            SwitchClearingTransactionRepository clearingRepository,
            SwitchLisFileRepository fileRepository, SwitchChargebackRepository chargebackRepository,
            SwamIssTransactionRepository sourceRepository,
            @Value("${swam.lis.switch-member-id}") String switchMemberId,
            @Value("${swam.lis.switch-bank-code}") String switchBankCode,
            @Value("${swam.lis.output-directory}") String outputDirectory) {
        this.dayRepository = dayRepository; this.clearingRepository = clearingRepository;
        this.fileRepository = fileRepository; this.chargebackRepository = chargebackRepository;
        this.sourceRepository = sourceRepository;
        this.switchMemberId = switchMemberId; this.switchBankCode = switchBankCode;
        this.outputDirectory = Path.of(outputDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public LisFileResult generate(LocalDate date, String destinationBankCode) throws IOException, ISOException {
        SwitchBusinessDay day = dayRepository.findByBankMemberIdAndBusinessDate("SWITCH", date)
                .orElseThrow(() -> new IllegalStateException("EOD switch absent for " + date));
        List<SwitchClearingTransaction> rows = clearingRepository
                .findByBusinessDayIdAndLocalSourceTypeOrderById(day.getId(), "SWAM_ISS");
        Map<Long, SwamIssTransaction> sources = new HashMap<>();
        sourceRepository.findAllById(rows.stream().map(SwitchClearingTransaction::getLocalSidTransactionId).toList())
                .forEach(source -> sources.put(source.getId(), source));
        LisFinancialRecordFactory factory = new LisFinancialRecordFactory();
        List<LisFinancialRecord> records = new ArrayList<>(rows.stream().map(row -> factory.create(
                data(row, Optional.ofNullable(sources.get(row.getLocalSidTransactionId()))
                        .orElseThrow(() -> new IllegalStateException("Source SID switch absente: " + row.getLocalSidTransactionId())))))
                .toList());
        List<SwitchChargeback> disputes=chargebackRepository.findByStatusOrderById(ChargebackStatus.READY_TO_SEND);
        LisDisputeRecordFactory disputeFactory=new LisDisputeRecordFactory();
        for(SwitchChargeback dispute:disputes){
            SwitchClearingTransaction row=clearingRepository.findById(dispute.getClearingTransactionId())
                    .orElseThrow(()->new IllegalStateException("Clearing transaction dispute absente"));
            LisFinancialRecord original=originalRecord(row,sources,factory);
            records.add(disputeFactory.create(original,dispute.getTransactionCode(),dispute.getCycleNumber(),
                    dispute.getReasonCode(),dispute.getChargebackReference(),dispute.getAmount(),dispute.getManualReason()));
        }
        int sequence = (int) fileRepository.count() + 1;
        byte[] bytes = new LisOutgoingFileAssembler(new LisRecordCodec(new LisPackagerRegistry()))
                .assemble(destinationBankCode, LIS_DATE.format(date), sequence, false, switchBankCode, records);
        Files.createDirectories(outputDirectory);
        String name = "LIS_SWITCH_%s_%03d.dat".formatted(date.toString().replace("-", ""), sequence);
        Path target = outputDirectory.resolve(name).normalize();
        if (!target.startsWith(outputDirectory)) throw new SecurityException("Invalid LIS output path");
        Path temp = Files.createTempFile(outputDirectory, name, ".tmp");
        Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        String digest = HexFormat.of().formatHex(sha256(bytes));
        SwitchLisFile file = new SwitchLisFile();
        file.setBusinessDayId(day.getId()); file.setDirection(LisDirection.OUTGOING);
        file.setFileName(name); file.setStoragePath(target.toString());
        file.setSourceMember(switchMemberId); file.setDestinationMember(destinationBankCode);
        file.setProcessingDate(date); file.setFileSequence(sequence);
        file.setSha256(digest); file.setByteSize(bytes.length);
        file.setPhysicalRecordCount(bytes.length / 256); file.setStatus(LisFileStatus.GENERATED);
        file = fileRepository.save(file);
        for(SwitchChargeback dispute:disputes){
            dispute.setOutgoingLisFileId(file.getId());dispute.setStatus(dispute.getParentChargebackId()==null
                    ?ChargebackStatus.SENT:ChargebackStatus.REPRESENTED);
            dispute.setEmittedAt(java.time.LocalDateTime.now());dispute.setUpdatedAt(java.time.LocalDateTime.now());
            chargebackRepository.save(dispute);
        }
        return new LisFileResult(file.getId(), name, target.toString(), digest,
                bytes.length, bytes.length / 256, file.getStatus().name());
    }

    private LisFinancialRecord originalRecord(SwitchClearingTransaction row,
            Map<Long,SwamIssTransaction> sources,LisFinancialRecordFactory factory)throws IOException,ISOException{
        SwamIssTransaction source=sources.get(row.getLocalSidTransactionId());
        if(source==null&&row.getLocalSidTransactionId()!=null)
            source=sourceRepository.findById(row.getLocalSidTransactionId()).orElse(null);
        if(source!=null)return factory.create(data(row,source));
        if(row.getIncomingLisFileId()==null||row.getIncomingRecordSequence()==null)
            throw new IllegalStateException("No original presentation available for dispute");
        SwitchLisFile incoming=fileRepository.findById(row.getIncomingLisFileId()).orElseThrow();
        ParsedLisFile parsed=new LisIncomingFileReader(new LisRecordCodec(new LisPackagerRegistry()))
                .read(Files.readAllBytes(Path.of(incoming.getStoragePath())));
        return parsed.financialRecords().stream().filter(record->Integer.parseInt(record.tcr0().getString(1))
                ==row.getIncomingRecordSequence()).findFirst()
                .orElseThrow(()->new IllegalStateException("Incoming LIS source record absent"));
    }

    private LisFinancialData data(SwitchClearingTransaction row, SwamIssTransaction source) {
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

    private static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
