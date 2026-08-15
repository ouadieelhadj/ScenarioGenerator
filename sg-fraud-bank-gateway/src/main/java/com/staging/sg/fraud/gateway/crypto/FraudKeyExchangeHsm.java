package com.staging.sg.fraud.gateway.crypto;

/** Port HSM: aucune API ne reçoit ou ne retourne une ZMK/TAK en clair. */
public interface FraudKeyExchangeHsm {
    TakEnvelope generateTakUnderZmk(String memberId, String zmkKeyReference);
    ImportedTak importTakUnderZmk(String memberId, String zmkKeyReference, String encryptedTak, String expectedKcv);
    record TakEnvelope(String encryptedTak, String kcv, String localKeyReference) {}
    record ImportedTak(String localKeyReference, String kcv) {}
}
