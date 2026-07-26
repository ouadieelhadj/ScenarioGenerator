package com.staging.sg.common.iso.sid;

import com.staging.sg.common.entity.SwamAcqTransaction;
import com.staging.sg.common.entity.SwamIssTransaction;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOException;

/**
 * Copies clearing-relevant SID fields to the durable transaction journal.
 * PIN (DE52), MAC (DE128), tracks and raw messages are deliberately excluded.
 */
public final class SidTransactionPersistenceMapper {
    private SidTransactionPersistenceMapper() {
    }

    public static void populate(SwamIssTransaction tx, ISOMsg request, ISOMsg response)
            throws ISOException {
        tx.setLocalTransactionDt(text(request, 12));
        tx.setSettlementDate(text(request, 15));
        tx.setConversionDate(text(request, 16));
        tx.setExpiryDate(text(request, 14));
        tx.setMerchantCategoryCode(text(request, 18));
        tx.setAcquirerCountryCode(text(request, 19));
        tx.setForwardingCountryCode(text(request, 21));
        tx.setPosDataCode(text(request, 22));
        tx.setFunctionCode(text(request, 24));
        tx.setMessageReasonCode(text(request, 25));
        tx.setCardSequenceNumber(text(request, 23));
        tx.setAcquirerInstitutionId(text(request, 32));
        tx.setForwardingInstitutionId(text(request, 33));
        tx.setRrn(text(request, 37));
        tx.setAuthorizationCode(text(response, 38));
        tx.setTerminalId(text(request, 41));
        tx.setMerchantId(text(request, 42));
        tx.setMerchantNameLocation(text(request, 43));
        tx.setSettlementAmount(number(request, 5));
        tx.setBillingAmount(number(request, 6));
        tx.setSettlementCurrency(text(request, 50));
        tx.setBillingCurrency(text(request, 51));
        tx.setSecurityControlInfo(text(request, 53));
        tx.setOriginalDataElements(text(request, 56));
        tx.setSenderIdentification(text(request, 124));
        applyLifecycle(tx, request.getMTI(), text(response, 39));
        tx.setClearingAmount(tx.isClearingEligible() ? tx.getAmount() : 0L);
    }

    public static void populate(SwamAcqTransaction tx, ISOMsg request, ISOMsg response)
            throws ISOException {
        tx.setLocalTransactionDt(text(request, 12));
        tx.setSettlementDate(text(request, 15));
        tx.setConversionDate(text(request, 16));
        tx.setExpiryDate(text(request, 14));
        tx.setMerchantCategoryCode(text(request, 18));
        tx.setAcquirerCountryCode(text(request, 19));
        tx.setForwardingCountryCode(text(request, 21));
        tx.setPosDataCode(text(request, 22));
        tx.setFunctionCode(text(request, 24));
        tx.setMessageReasonCode(text(request, 25));
        tx.setCardSequenceNumber(text(request, 23));
        tx.setAcquirerInstitutionId(text(request, 32));
        tx.setForwardingInstitutionId(text(request, 33));
        tx.setRrn(text(request, 37));
        tx.setAuthorizationCode(text(response, 38));
        tx.setTerminalId(text(request, 41));
        tx.setMerchantId(text(request, 42));
        tx.setMerchantNameLocation(text(request, 43));
        tx.setSettlementAmount(number(request, 5));
        tx.setBillingAmount(number(request, 6));
        tx.setSettlementCurrency(text(request, 50));
        tx.setBillingCurrency(text(request, 51));
        tx.setSecurityControlInfo(text(request, 53));
        tx.setOriginalDataElements(text(request, 56));
        tx.setSenderIdentification(text(request, 124));
        applyLifecycle(tx, request.getMTI(), text(response, 39));
        tx.setClearingAmount(tx.isClearingEligible() ? tx.getAmount() : 0L);
    }

    private static void applyLifecycle(SwamIssTransaction tx, String mti, String actionCode) {
        boolean approved = "000".equals(actionCode);
        tx.setClearingEligible(approved && ("1200".equals(mti) || "1220".equals(mti) || "1221".equals(mti)));
        tx.setLifecycleStatus(lifecycle(mti, approved));
    }

    private static void applyLifecycle(SwamAcqTransaction tx, String mti, String actionCode) {
        boolean approved = "000".equals(actionCode);
        tx.setClearingEligible(approved && ("1200".equals(mti) || "1220".equals(mti) || "1221".equals(mti)));
        tx.setLifecycleStatus(lifecycle(mti, approved));
    }

    private static String lifecycle(String mti, boolean approved) {
        if (!approved) return "DECLINED";
        return switch (mti) {
            case "1100" -> "AUTHORIZED";
            case "1200", "1220", "1221" -> "FINANCIAL_CONFIRMED";
            case "1420", "1421" -> "REVERSED";
            default -> "RECEIVED";
        };
    }

    private static String text(ISOMsg message, int field) throws ISOException {
        return message != null && message.hasField(field) ? message.getString(field) : null;
    }

    private static Long number(ISOMsg message, int field) throws ISOException {
        String value = text(message, field);
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }
}
