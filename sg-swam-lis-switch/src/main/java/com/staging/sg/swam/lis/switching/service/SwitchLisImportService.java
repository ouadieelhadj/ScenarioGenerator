package com.staging.sg.swam.lis.switching.service;

import com.staging.sg.swam.lis.common.codec.LisRecordCodec;
import com.staging.sg.swam.lis.common.model.*;
import com.staging.sg.swam.lis.common.packager.LisPackagerRegistry;
import com.staging.sg.swam.lis.common.service.*;
import com.staging.sg.swam.lis.switching.persistence.*;
import com.staging.sg.swam.lis.switching.repository.*;
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
public class SwitchLisImportService {
    private final SwitchBusinessDayRepository days; private final SwitchLisFileRepository files;
    private final SwitchClearingTransactionRepository transactions;
    private final SwitchChargebackRepository chargebacks;
    private final String memberId; private final Path directory; private final ClearingIdentityService identity;

    public SwitchLisImportService(SwitchBusinessDayRepository days, SwitchLisFileRepository files,
            SwitchClearingTransactionRepository transactions, SwitchChargebackRepository chargebacks,
            @Value("${swam.lis.default-member-id}") String memberId,
            @Value("${swam.lis.incoming-directory}") String directory,
            @Value("${swam.lis.pan-fingerprint-salt}") String salt) {
        this.days=days; this.files=files; this.transactions=transactions; this.chargebacks=chargebacks;
        this.memberId=memberId;
        this.directory=Path.of(directory).toAbsolutePath().normalize(); this.identity=new ClearingIdentityService(salt);
    }

    @Transactional
    public LisImportResult importFile(String originalName, byte[] bytes) throws IOException, ISOException {
        String digest=HexFormat.of().formatHex(sha256(bytes));
        if(files.findByDirectionAndSha256(LisDirection.INCOMING,digest).isPresent())
            throw new IllegalStateException("LIS incoming already imported: "+digest);
        ParsedLisFile parsed=new LisIncomingFileReader(new LisRecordCodec(new LisPackagerRegistry())).read(bytes);
        LocalDate date=LocalDate.parse(parsed.processingDate(),
                DateTimeFormatter.ofPattern("ddMMyy").withResolverStyle(ResolverStyle.SMART));
        SwitchBusinessDay day=days.findByBankMemberIdAndBusinessDate("SWITCH",date).orElseGet(()->{
            SwitchBusinessDay value=new SwitchBusinessDay(); value.setBankMemberId("SWITCH");
            value.setBusinessDate(date); return days.save(value); });
        Files.createDirectories(directory);
        String safe=Path.of(originalName==null?"incoming.lis":originalName).getFileName().toString()
                .replaceAll("[^A-Za-z0-9._-]","_");
        Path target=directory.resolve(digest.substring(0,12)+"_"+safe).normalize();
        if(!target.startsWith(directory)) throw new SecurityException("Invalid LIS incoming path");
        Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
        SwitchLisFile file=new SwitchLisFile(); file.setBusinessDayId(day.getId());
        file.setDirection(LisDirection.INCOMING); file.setFileName(safe); file.setStoragePath(target.toString());
        file.setSourceMember(parsed.originatorBankCode()); file.setDestinationMember("SWITCH");
        file.setProcessingDate(date); file.setFileSequence(parsed.fileSequence()); file.setSha256(digest);
        file.setByteSize(bytes.length); file.setPhysicalRecordCount(parsed.physicalRecordCount());
        file.setStatus(LisFileStatus.PROCESSING); file=files.save(file);
        long matched=0,lisOnly=0;
        for(LisFinancialRecord financial:parsed.financialRecords()){
            Incoming v=values(financial);
            String key=identity.functionalKey(memberId,v.rrn,null,v.auth,
                    ClearingIdentityService.canonicalTransactionDate(v.at),v.amount,v.currency);
            var local=transactions.findByFunctionalKey(key);
            SwitchClearingTransaction resolved;
            if(local.isPresent()){
                SwitchClearingTransaction row=local.get(); row.setIncomingLisFileId(file.getId());
                row.setIncomingRecordSequence(v.sequence); row.setSourcePresence(SourcePresence.BOTH);
                row.setMatchStatus(MatchStatus.MATCHED); row.setAccountingStatus(AccountingStatus.READY);
                row.setUpdatedAt(LocalDateTime.now()); resolved=transactions.save(row); matched++;
            }else{
                SwitchClearingTransaction row=new SwitchClearingTransaction(); row.setBankMemberId(memberId);
                row.setBusinessDayId(day.getId()); row.setIncomingLisFileId(file.getId());
                row.setIncomingRecordSequence(v.sequence); row.setFunctionalKey(key); row.setTransactionType(v.type);
                row.setClearingCycle(v.cycle); row.setPanFingerprint(identity.panFingerprint(v.pan));
                row.setMaskedPan(identity.maskPan(v.pan)); row.setRrn(v.rrn); row.setAuthorizationCode(v.auth);
                row.setTransactionAt(v.at); row.setProcessingDate(date); row.setMcc(v.zero.getString(8));
                row.setTerminalId(v.zero.getString(12)); row.setMerchantId(v.zero.getString(4));
                row.setMerchantName(v.zero.getString(5)); row.setMerchantCity(v.zero.getString(6));
                row.setTransactionAmount(v.amount); row.setTransactionCurrency(v.currency);
                row.setSettlementAmount(Long.parseLong(v.one.getString(15)));
                row.setSettlementCurrency(v.one.getString(29)); row.setSourcePresence(SourcePresence.LIS_ONLY);
                row.setMatchStatus(MatchStatus.LIS_ONLY); row.setAccountingStatus(AccountingStatus.READY);
                resolved=transactions.save(row); lisOnly++;
            }
            receiveChargebackIfApplicable(financial,resolved,file,parsed.originatorBankCode());
        }
        file.setStatus(LisFileStatus.PROCESSED); files.save(file);
        return new LisImportResult(file.getId(),parsed.financialRecords().size(),matched,lisOnly,
                matched+lisOnly,file.getStatus().name());
    }

    private void receiveChargebackIfApplicable(LisFinancialRecord financial,
            SwitchClearingTransaction transaction,SwitchLisFile file,String originator){
        String tc=financial.tcr0().getString(0);
        if(!java.util.Set.of("15","16","17").contains(tc))return;
        String reason=financial.tcr0().getString(16);
        int cycle=Integer.parseInt(financial.tcr0().getString(13));
        if(chargebacks.existsByClearingTransactionIdAndDirectionAndCycleNumberAndReasonCode(
                transaction.getId(),ChargebackDirection.RECEIVED,cycle,reason))return;
        SwitchChargeback cb=new SwitchChargeback();cb.setBankMemberId(memberId);
        cb.setClearingTransactionId(transaction.getId());cb.setDirection(ChargebackDirection.RECEIVED);
        cb.setStatus(ChargebackStatus.RECEIVED);cb.setTransactionCode(tc);cb.setCycleNumber(cycle);
        cb.setReasonCode(reason);cb.setChargebackReference(financial.tcr0().getString(17));
        cb.setAmount(Long.parseLong(financial.tcr1().getString(11)));cb.setCurrency(financial.tcr1().getString(28));
        cb.setSourceLisFileId(file.getId());cb.setSourceRecordSequence(Integer.parseInt(financial.tcr0().getString(1)));
        cb.setCounterpartyMember(originator);cb.setReceivedAt(LocalDateTime.now());cb.setCreatedBy("LIS_IMPORT");
        chargebacks.save(cb);transaction.setDisputeStatus(DisputeStatus.OPEN);transactions.save(transaction);
    }

    private static Incoming values(LisFinancialRecord r){
        ISOMsg z=r.tcr0(),o=r.tcr1();
        LocalDate d=LocalDate.parse(o.getString(3),DateTimeFormatter.ofPattern("ddMMyy")
                .withResolverStyle(ResolverStyle.SMART));
        LocalTime t=LocalTime.parse(o.getString(27),DateTimeFormatter.ofPattern("HHmmss"));
        String type=switch(z.getString(0)){case"07","17","27","37"->"CASH_WITHDRAWAL";
            case"06","16","26","36"->"CASH_ADVANCE";default->"PURCHASE";};
        return new Incoming(Integer.parseInt(z.getString(1)),z,o,type,z.getString(20).trim(),
                o.getString(26).trim(),o.getString(4).trim(),LocalDateTime.of(d,t),
                Long.parseLong(o.getString(11)),o.getString(28),"2".equals(z.getString(13))?2:1);
    }
    private record Incoming(int sequence,ISOMsg zero,ISOMsg one,String type,String pan,String rrn,
            String auth,LocalDateTime at,long amount,String currency,int cycle){}
    private static byte[] sha256(byte[] b){try{return MessageDigest.getInstance("SHA-256").digest(b);}
        catch(Exception e){throw new IllegalStateException(e);}}
}
