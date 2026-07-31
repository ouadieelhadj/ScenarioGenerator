package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.domain.*;
import com.staging.sg.card.issuing.port.*;
import com.staging.sg.card.issuing.repository.*;
import com.staging.sg.common.issuing.*;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class IssuerDecisionService implements IssuerAuthorizationUseCase {
    private final PaymentIdentifierResolutionPort resolver;
    private final PaymentIdentifierRepository identifiers;
    private final CardInstrumentRepository instruments;
    private final CardContractRepository contracts;
    private final CardProductRepository products;
    private final CardSecurityPort security;
    private final FundingAuthorizationPort funding;
    private final AuthorizationJournalService journal;
    private final SecureRandom random = new SecureRandom();

    public IssuerDecisionService(
            PaymentIdentifierResolutionPort resolver,
            PaymentIdentifierRepository identifiers,
            CardInstrumentRepository instruments,
            CardContractRepository contracts,
            CardProductRepository products, CardSecurityPort security,
            FundingAuthorizationPort funding, AuthorizationJournalService journal) {
        this.resolver=resolver; this.identifiers=identifiers; this.instruments=instruments;
        this.contracts=contracts; this.products=products; this.security=security;
        this.funding=funding; this.journal=journal;
    }

    @Override
    public IssuingAuthorizationResponse authorize(IssuingAuthorizationRequest request) {
        IssuingContractValidator.validate(request);
        String fingerprint=fingerprint(request);
        var replay=journal.replay(request.issuerId(),request.callerId(),
                request.idempotencyKey(),fingerprint);
        if(replay.isPresent()) return replay.get().response(true);

        PaymentIdentifier identifier;
        try {
            var resolved=resolver.resolve(request.issuerId(),
                    request.paymentIdentifierType(),request.paymentIdentifier());
            identifier=identifiers
                    .findByIssuerIdAndVaultReferenceAndStatus(
                            request.issuerId(),
                            resolved.vaultReference(),PaymentIdentifierStatus.ACTIVE)
                    .orElse(null);
        } catch (PaymentIdentifierNotFoundException notFound) {
            return terminal(request,fingerprint,null,
                    IssuingDecisionStatus.DECLINED,"CARD_NOT_FOUND",null,0);
        } catch (RuntimeException unavailable) {
            return transientUnknown(request,"IDENTIFIER_RESOLUTION_UNAVAILABLE");
        }
        if(identifier==null) return terminal(request,fingerprint,null,
                IssuingDecisionStatus.DECLINED,"CARD_NOT_FOUND",null,0);

        CardInstrument instrument=instruments.findById(identifier.instrumentId()).orElse(null);
        if(instrument==null || !instrument.issuerId().equals(request.issuerId()))
            return terminal(request,fingerprint,identifier.id(),
                    IssuingDecisionStatus.DECLINED,"CARD_NOT_FOUND",null,0);
        if(instrument.status()!=CardInstrumentStatus.ACTIVE)
            return terminal(request,fingerprint,identifier.id(),
                    IssuingDecisionStatus.DECLINED,"CARD_NOT_ACTIVE",null,0);
        if(expired(instrument.expiryYymm()))
            return terminal(request,fingerprint,identifier.id(),
                    IssuingDecisionStatus.DECLINED,"CARD_EXPIRED",null,0);

        CardContract contract=contracts.findById(instrument.contractId()).orElse(null);
        if(contract==null || contract.status()!=CardContractStatus.ACTIVE
                || !contract.issuerId().equals(request.issuerId()))
            return terminal(request,fingerprint,identifier.id(),
                    IssuingDecisionStatus.DECLINED,"CONTRACT_NOT_ACTIVE",null,0);
        CardProduct product=products.findById(contract.productId()).orElse(null);
        if(product==null || !product.isActive()
                || !product.issuerId().equals(request.issuerId()))
            return terminal(request,fingerprint,identifier.id(),
                    IssuingDecisionStatus.DECLINED,"PRODUCT_NOT_ACTIVE",null,0);
        if(!product.currency().equals(request.currency()))
            return terminal(request,fingerprint,identifier.id(),
                    IssuingDecisionStatus.DECLINED,"CURRENCY_NOT_ALLOWED",null,0);
        if(!serviceAllowed(product,request))
            return terminal(request,fingerprint,identifier.id(),
                    IssuingDecisionStatus.DECLINED,"SERVICE_NOT_ALLOWED",null,0);
        if(request.operation()!=IssuingOperation.AUTHORIZATION
                && request.operation()!=IssuingOperation.FINANCIAL)
            return terminal(request,fingerprint,identifier.id(),
                    IssuingDecisionStatus.DECLINED,"OPERATION_NOT_SUPPORTED",null,0);

        if(request.pinBlockHex()!=null || request.emvDataHex()!=null){
            var checked=security.verify(new CardSecurityPort.SecurityCommand(
                    request.issuerId(),identifier.vaultReference(),
                    request.pinBlockHex(),request.pinKeyDomain(),request.emvDataHex(),
                    request.transactionId(),request.correlationId()));
            if(checked.status()==CardSecurityPort.SecurityStatus.UNAVAILABLE)
                return transientUnknown(request,checked.responseCode());
            if(checked.status()==CardSecurityPort.SecurityStatus.DECLINED)
                return terminal(request,fingerprint,identifier.id(),
                        IssuingDecisionStatus.DECLINED,checked.responseCode(),null,0);
            if(request.emvDataHex()!=null)
                return transientUnknown(request,"EMV_RESPONSE_PERSISTENCE_NOT_READY");
        }

        var funded=funding.authorize(new FundingAuthorizationPort.FundingCommand(
                request.issuerId(),contract.fundingContractId(),request.operation(),
                request.amountMinor(),request.currency(),request.transactionId(),
                request.originalTransactionId(),request.correlationId(),
                request.idempotencyKey()));
        if(funded.status()==FundingAuthorizationPort.FundingStatus.UNAVAILABLE)
            return transientUnknown(request,funded.responseCode());
        if(funded.status()==FundingAuthorizationPort.FundingStatus.DECLINED)
            return terminal(request,fingerprint,identifier.id(),
                    IssuingDecisionStatus.DECLINED,funded.responseCode(),null,0);
        IssuingDecisionStatus status=funded.status()
                ==FundingAuthorizationPort.FundingStatus.PARTIALLY_APPROVED
                ? IssuingDecisionStatus.PARTIALLY_APPROVED : IssuingDecisionStatus.APPROVED;
        return terminal(request,fingerprint,identifier.id(),status,
                funded.responseCode(),authorizationCode(),funded.approvedAmountMinor());
    }

    private IssuingAuthorizationResponse terminal(
            IssuingAuthorizationRequest r,String fingerprint,UUID identifierId,
            IssuingDecisionStatus status,String code,String authCode,long approved){
        var decision=IssuingAuthorization.decided(
                r.issuerId(),r.callerId(),r.transactionId(),r.correlationId(),
                r.idempotencyKey(),fingerprint,identifierId,r.operation(),
                r.originalTransactionId(),r.amountMinor(),r.currency(),status,
                code,authCode,approved,false);
        journal.record(decision,r.correlationId());
        return decision.response(false);
    }
    private static IssuingAuthorizationResponse transientUnknown(
            IssuingAuthorizationRequest r,String code){
        return new IssuingAuthorizationResponse("1.0",r.issuerId(),r.transactionId(),
                r.correlationId(),IssuingDecisionStatus.UNKNOWN,code,null,0,
                r.currency(),null,true,Map.of("processing","FAIL_CLOSED"));
    }
    private static boolean serviceAllowed(CardProduct p,IssuingAuthorizationRequest r){
        if("CASH".equalsIgnoreCase(r.attributes().get("service")))
            return p.cashEnabled();
        if(r.ecommerce()) return p.ecommerceEnabled();
        return p.purchaseEnabled();
    }
    private static boolean expired(String yymm){
        try {
            return YearMonth.parse(yymm,DateTimeFormatter.ofPattern("yyMM"))
                    .isBefore(YearMonth.now());
        } catch (RuntimeException invalidExpiry) {
            return true;
        }
    }
    private String authorizationCode(){return "%06d".formatted(random.nextInt(1_000_000));}
    private static String fingerprint(IssuingAuthorizationRequest r){
        return CommandFingerprint.of(r.issuerId(),r.callerId(),r.transactionId(),
                r.operation(),r.originalTransactionId(),r.paymentIdentifierType(),
                r.paymentIdentifier(),r.amountMinor(),r.currency(),r.terminalId(),
                r.merchantId(),r.cardPresent(),r.ecommerce(),r.pinBlockHex(),
                r.pinKeyDomain(),r.emvDataHex(),new TreeMap<>(r.attributes()));
    }
}
