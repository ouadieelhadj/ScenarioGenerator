package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.domain.*;
import com.staging.sg.card.issuing.port.*;
import com.staging.sg.card.issuing.repository.*;
import com.staging.sg.common.issuing.*;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IssuerDecisionServiceTest {
    @Test
    void approvesOnlyAfterRealFundingApprovalAndJournalsDecision() {
        Fixture f=new Fixture();
        when(f.funding.authorize(any())).thenReturn(
                new FundingAuthorizationPort.FundingResult(
                        FundingAuthorizationPort.FundingStatus.APPROVED,
                        "APPROVED",1000,"funding-ref"));

        var response=f.service.authorize(request());

        assertEquals(IssuingDecisionStatus.APPROVED,response.status());
        assertNotNull(response.authorizationCode());
        assertEquals(6,response.authorizationCode().length());
        verify(f.journal).record(any(),eq("corr-1"));
        verifyNoInteractions(f.security);
    }

    @Test
    void remainsRetryableAndDoesNotJournalWhenCoreBankingIsUnavailable() {
        Fixture f=new Fixture();
        when(f.funding.authorize(any())).thenReturn(
                new FundingAuthorizationPort.FundingResult(
                        FundingAuthorizationPort.FundingStatus.UNAVAILABLE,
                        "CORE_BANKING_UNAVAILABLE",0,null));

        var response=f.service.authorize(request());

        assertEquals(IssuingDecisionStatus.UNKNOWN,response.status());
        assertTrue(response.retryable());
        verify(f.journal,never()).record(any(),any());
    }

    private static IssuingAuthorizationRequest request(){
        return new IssuingAuthorizationRequest(
                "1.0","ISSUER-1","SERVER_POS","txn-1","corr-1","idem-1",
                IssuingOperation.AUTHORIZATION,null,PaymentIdentifierType.PAN,
                "protected-input",1000,"504","2026-07-31T09:00:00",
                "TERM-1","MERCHANT-1","5411","504",true,false,
                null,null,null,Map.of());
    }

    private static final class Fixture {
        final PaymentIdentifierResolutionPort resolver=mock(PaymentIdentifierResolutionPort.class);
        final PaymentIdentifierRepository identifiers=mock(PaymentIdentifierRepository.class);
        final CardInstrumentRepository instruments=mock(CardInstrumentRepository.class);
        final CardContractRepository contracts=mock(CardContractRepository.class);
        final CardProductRepository products=mock(CardProductRepository.class);
        final CardSecurityPort security=mock(CardSecurityPort.class);
        final FundingAuthorizationPort funding=mock(FundingAuthorizationPort.class);
        final AuthorizationJournalService journal=mock(AuthorizationJournalService.class);
        final IssuerDecisionService service;
        Fixture(){
            CardProduct product=CardProduct.draft(
                    "ISSUER-1","DEBIT",1,CardType.DEBIT,"504",
                    true,false,false,"maker","prod-idem","fp");
            product.approve("checker"); product.activate();
            CardContract contract=CardContract.draft(
                    "ISSUER-1","EXT","CUSTOMER","HOLDER","FUNDING-1",
                    product.id(),"maker","contract-idem","fp");
            contract.submit(); contract.approve("checker");
            String expiry=YearMonth.now().plusYears(2)
                    .format(DateTimeFormatter.ofPattern("yyMM"));
            CardInstrument instrument=CardInstrument.inactive(
                    "ISSUER-1",contract.id(),"vault-ref","123456******7890",
                    expiry,"maker","card-idem","fp");
            instrument.activate(contract.status());
            PaymentIdentifier identifier=PaymentIdentifier.activePan(
                    "ISSUER-1",instrument.id(),"vault-ref","123456******7890");
            when(journal.replay(any(),any(),any(),any())).thenReturn(Optional.empty());
            when(resolver.resolve(any(),any(),any())).thenReturn(
                    new PaymentIdentifierResolutionPort.ResolvedPaymentIdentifier("vault-ref"));
            when(identifiers.findByIssuerIdAndIdentifierTypeAndVaultReferenceAndStatus(
                    any(),any(),any(),any())).thenReturn(Optional.of(identifier));
            when(instruments.findById(instrument.id())).thenReturn(Optional.of(instrument));
            when(contracts.findById(contract.id())).thenReturn(Optional.of(contract));
            when(products.findById(product.id())).thenReturn(Optional.of(product));
            when(journal.record(any(),any())).thenAnswer(invocation->invocation.getArgument(0));
            service=new IssuerDecisionService(resolver,identifiers,instruments,
                    contracts,products,security,funding,journal);
        }
    }
}
