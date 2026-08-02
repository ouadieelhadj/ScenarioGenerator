package com.staging.sg.visa.common.online;

public record VisaOnlineReferences(String aci, String transactionId,
        String validationCode, String provenance) {
    public VisaOnlineReferences {
        if (aci == null || transactionId == null || validationCode == null) {
            throw new IllegalArgumentException("Incomplete Visa Online references");
        }
    }
}
