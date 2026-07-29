package com.staging.sg.dmcs.common.ipm;

import com.staging.sg.common.entity.AbstractDmcClearingTransaction;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Transforme une étape DMC persistée en étape de litige sortante.
 *
 * <p>DE31 et DE95 ne sont jamais fabriqués. DE31 doit déjà être porté par le
 * cycle entrant. DE95 doit être fourni par le système habilité lors du First
 * Chargeback, puis il peut être repris du parent pour la seconde
 * présentation.</p>
 */
public final class DmcOutgoingDisputeMapper {
    private DmcOutgoingDisputeMapper() {
    }

    public static <T extends AbstractDmcClearingTransaction> T populate(
            T target,
            AbstractDmcClearingTransaction parent,
            DisputeCommand command,
            String lifecycleStage,
            String destinationId,
            String originId) {
        if (target == null || parent == null || command == null) {
            throw new IllegalArgumentException(
                    "Cible, transaction parente et commande obligatoires");
        }
        String arn = require(parent.getAcquirerReference(), "DE31/ARN réel absent");
        String issuerReference = firstNonBlank(
                command.issuerReference(), parent.getIssuerReference());
        require(issuerReference, "DE95 réel absent");
        long amount = command.amount() == null
                ? requireAmount(parent.getAmount()) : command.amount();
        long originalTransactionAmount = requireAmount(parent.getAmount());
        long originalReconciliationAmount = parent.getReconciliationAmount() == null
                ? originalTransactionAmount : parent.getReconciliationAmount();

        target.setBusinessDate(command.businessDate() == null
                ? LocalDate.now() : command.businessDate());
        target.setSourceType("LOCAL_CLEARING");
        target.setDirection("OUT");
        target.setParentTransactionId(parent.getId());
        target.setCorrelationKey(parent.getCorrelationKey() + ":" + lifecycleStage);
        target.setLifecycleStage(lifecycleStage);
        target.setStatus("READY");
        target.setMatchStatus("PENDING");
        target.setMti(lifecycleStage.equals("FIRST_CHARGEBACK") ? "1442" : "1240");
        target.setFunctionCode(command.functionCode());
        target.setPan(parent.getPan());
        target.setMaskedPan(parent.getMaskedPan());
        target.setProcessingCode(parent.getProcessingCode());
        target.setAmount(amount);
        target.setReconciliationAmount(amount);
        target.setReconciliationRate(parent.getReconciliationRate());
        target.setTransactionDatetime(parent.getTransactionDatetime());
        target.setExpiry(parent.getExpiry());
        target.setPosDataCode(parent.getPosDataCode());
        target.setMessageReasonCode(command.messageReasonCode());
        target.setMcc(parent.getMcc());
        target.setOriginalAmounts(
                "%012d%012d".formatted(
                        originalTransactionAmount, originalReconciliationAmount));
        target.setAcquirerReference(arn);
        target.setAcquiringInstitutionId(parent.getAcquiringInstitutionId());
        target.setForwardingInstitutionId(parent.getForwardingInstitutionId());
        target.setRrn(parent.getRrn());
        target.setAuthorizationCode(parent.getAuthorizationCode());
        target.setTerminalId(parent.getTerminalId());
        target.setAcceptorId(parent.getAcceptorId());
        target.setAcceptorNameLocation(parent.getAcceptorNameLocation());
        target.setCurrency(parent.getCurrency());
        target.setReconciliationCurrency(firstNonBlank(
                parent.getReconciliationCurrency(), parent.getCurrency()));
        target.setMessageNumber("00000002");
        target.setDestinationId(destinationId);
        target.setOriginId(originId);
        target.setIssuerReference(issuerReference);
        target.setPdsData(command.pdsData());
        target.setUpdatedAt(LocalDateTime.now());
        return target;
    }

    public static DmcDisputeMessageFactory.DisputeData toMessage(
            AbstractDmcClearingTransaction transaction) {
        return new DmcDisputeMessageFactory.DisputeData(
                transaction.getFunctionCode(),
                transaction.getPan(),
                transaction.getProcessingCode(),
                requireAmount(transaction.getAmount()),
                transaction.getTransactionDatetime(),
                transaction.getExpiry(),
                transaction.getPosDataCode(),
                transaction.getMessageReasonCode(),
                transaction.getMcc(),
                transaction.getOriginalAmounts(),
                transaction.getAcquirerReference(),
                transaction.getAcquiringInstitutionId(),
                transaction.getForwardingInstitutionId(),
                transaction.getRrn(),
                transaction.getAuthorizationCode(),
                transaction.getTerminalId(),
                transaction.getAcceptorId(),
                transaction.getAcceptorNameLocation(),
                transaction.getCurrency(),
                transaction.getDestinationId(),
                transaction.getOriginId(),
                transaction.getIssuerReference(),
                transaction.getPdsData());
    }

    private static long requireAmount(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("Montant réel absent");
        }
        return value;
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    public record DisputeCommand(
            String functionCode,
            Long amount,
            String messageReasonCode,
            String issuerReference,
            String pdsData,
            LocalDate businessDate) {
    }
}
