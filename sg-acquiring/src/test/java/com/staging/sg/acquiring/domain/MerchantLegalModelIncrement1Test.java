package com.staging.sg.acquiring.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MerchantLegalModelIncrement1Test {
    @Test
    void distinguishesAssociationAndFoundation() {
        Merchant association = merchant("association");
        association.enrichLegalType("ASSOCIATION_FOUNDATION", "ASSOCIATION");
        assertEquals("ASSOCIATION", association.organizationLegalNature());

        Merchant foundation = merchant("foundation");
        foundation.enrichLegalType("ASSOCIATION_FOUNDATION", "FOUNDATION");
        assertEquals("FOUNDATION", foundation.organizationLegalNature());
    }

    @Test
    void storesStructuredLegalProfileWithoutBankingFormatGuess() {
        Merchant merchant = merchant("profile");
        MerchantLegalProfile profile = MerchantLegalProfile.create(merchant.id(), "IF-1", "ICE-1",
                "SARL", "Retail", null, "+212500000000", "merchant@example.test",
                "1234 5678 9012 3456 7890 1234", "1 Main Street", null,
                "District", "Rabat", "Rabat-Sale-Kenitra", "10000", "MA");
        assertEquals("123456789012345678901234", profile.rib());
        assertEquals("Rabat", profile.city());
    }

    @Test
    void enrichesOutletContactsAndPrincipalFlag() {
        Merchant merchant = merchant("outlet");
        MerchantOutlet outlet = MerchantOutlet.active(merchant.id(), "OUT-1", "Outlet",
                "Legacy address", "MA");
        outlet.enrich(true, "1 Main Street", null, "District", "Rabat", null, "10000", "MA",
                "+212511111111", "outlet@example.test", "MR", "Outlet", "Manager",
                LocalDate.of(1985, 2, 3), "+212522222222", "manager@example.test",
                "CIN", "CD5678", "MA", "MA");
        assertTrue(outlet.principal());
        assertEquals("Rabat", outlet.city());
        assertEquals(LocalDate.of(1985, 2, 3), outlet.responsibleBirthDate());
        assertEquals("CD5678", outlet.responsibleIdNumber());
    }

    @Test
    void createsRepresentativeAndBeneficialOwner() {
        Merchant merchant = merchant("people");
        MerchantRepresentative representative = MerchantRepresentative.active(merchant.id(), "MR",
                "Legal", "Representative", LocalDate.of(1980, 1, 1), "+212500000000",
                "legal@example.test", "CIN", "AB1234", "MA", "MA");
        MerchantBeneficialOwner owner = MerchantBeneficialOwner.active(merchant.id(), "Owner", "One");
        assertTrue(representative.active());
        assertTrue(owner.active());
    }

    private static Merchant merchant(String suffix) {
        return Merchant.draft("ACQ-01", "Legal " + suffix, "Trade " + suffix,
                "RC-" + suffix, "MA", "5411", "maker", "idem-" + suffix, "a".repeat(64));
    }
}
