package com.staging.sg.way4aura.service;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class Way4ExternalIdentifierAllocator {
    private final boolean enabled;
    private final String environment;
    private final String activeProfiles;
    private final long midMin, midMax, tidMin, tidMax;
    private final String merchantContractPrefix;
    private final int merchantContractWidth;
    private final EntityManager entityManager;

    public Way4ExternalIdentifierAllocator(
            @Value("${way4-aura.external-allocation.enabled:false}") boolean enabled,
            @Value("${way4-aura.external-allocation.environment:DISABLED}") String environment,
            @Value("${spring.profiles.active:}") String activeProfiles,
            @Value("${way4-aura.external-allocation.mid-min:990001000000000}") long midMin,
            @Value("${way4-aura.external-allocation.mid-max:990001999999999}") long midMax,
            @Value("${way4-aura.external-allocation.tid-min:99000000}") long tidMin,
            @Value("${way4-aura.external-allocation.tid-max:99999999}") long tidMax,
            @Value("${way4-aura.external-allocation.merchant-contract-prefix:LCAR}") String merchantContractPrefix,
            @Value("${way4-aura.external-allocation.merchant-contract-width:8}") int merchantContractWidth,
            EntityManager entityManager) {
        this.enabled = enabled;
        this.environment = environment;
        this.activeProfiles = activeProfiles;
        this.midMin=midMin; this.midMax=midMax; this.tidMin=tidMin; this.tidMax=tidMax;
        this.merchantContractPrefix=merchantContractPrefix; this.merchantContractWidth=merchantContractWidth;
        this.entityManager = entityManager;
    }

    @Transactional
    public AllocatedIdentifiers allocate(UUID onboardingCaseId, String applicationRegNumber,
            UUID outletId, UUID terminalRequestId, int terminalOrdinal) {
        requireCarsdb();
        String merchantContract = allocate("MERCHANT_CONTRACT", applicationRegNumber,
                "way4_merchant_contract_number_seq", "MERCHANT_CONTRACT");
        String mid = allocate("MID", outletId.toString(), "way4_external_mid_seq", "MID");
        String tid = allocate("TID", terminalRequestId + ":" + terminalOrdinal,
                "way4_external_tid_seq", "TID");
        return new AllocatedIdentifiers(mid, tid, merchantContract);
    }

    private String allocate(String type, String businessKey, String sequence, String format) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, type + ":" + businessKey).getSingleResult();
        var existing = entityManager.createNativeQuery("select allocated_value from way4_external_identifier_allocation "
                        + "where allocation_type=?1 and business_key=?2")
                .setParameter(1, type).setParameter(2, businessKey).getResultList();
        if (!existing.isEmpty()) return existing.get(0).toString();
        long number = ((Number) entityManager.createNativeQuery("select nextval('" + sequence + "')")
                .getSingleResult()).longValue();
        String value = format(number, format, midMin, midMax, tidMin, tidMax,
                merchantContractPrefix, merchantContractWidth);
        entityManager.createNativeQuery("insert into way4_external_identifier_allocation "
                        + "(id,allocation_type,business_key,allocated_value,created_at) "
                        + "values (?1,?2,?3,?4,current_timestamp)")
                .setParameter(1, UUID.randomUUID()).setParameter(2, type)
                .setParameter(3, businessKey).setParameter(4, value).executeUpdate();
        return value;
    }

    private void requireCarsdb() {
        if (!enabled || !"CARSDB".equalsIgnoreCase(environment)
                || activeProfiles.toLowerCase(Locale.ROOT).contains("prod"))
            throw new AuraMappingBlockedException("External WAY4 allocation is restricted to CARSDB recipe");
        if (midMin != 990001000000000L || midMax != 990001999999999L
                || tidMin != 99000000L || tidMax != 99999999L)
            throw new AuraMappingBlockedException("External WAY4 allocation ranges are not approved for CARSDB");
        String database = entityManager.createNativeQuery("select current_database()").getSingleResult().toString();
        if (!"CARSDB".equalsIgnoreCase(database))
            throw new AuraMappingBlockedException("External WAY4 allocation cannot run outside database CARSDB");
    }

    static String format(long number, String type, long midMin, long midMax, long tidMin, long tidMax,
            String contractPrefix, int contractWidth) {
        return switch (type) {
            case "MID" -> inRange(number, midMin, midMax, "MID");
            case "TID" -> inRange(number, tidMin, tidMax, "TID");
            case "MERCHANT_CONTRACT" -> {
                if (contractPrefix == null || !contractPrefix.matches("[A-Z0-9]{1,12}")
                        || contractWidth < 1 || contractWidth > 18
                        || number < 1 || Long.toString(number).length() > contractWidth)
                    throw new AuraMappingBlockedException("Merchant contract range is exhausted");
                yield contractPrefix + String.format("%0" + contractWidth + "d", number);
            }
            default -> throw new IllegalArgumentException("Unknown allocation type");
        };
    }

    private static String inRange(long number, long min, long max, String label) {
        if (number < min || number > max) throw new AuraMappingBlockedException(label + " range is exhausted");
        return Long.toString(number);
    }

    public record AllocatedIdentifiers(String mid, String tid, String merchantContractNumber) {}
}
