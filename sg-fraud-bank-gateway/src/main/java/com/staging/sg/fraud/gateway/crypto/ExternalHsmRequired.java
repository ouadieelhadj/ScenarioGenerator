package com.staging.sg.fraud.gateway.crypto;

public class ExternalHsmRequired implements FraudKeyExchangeHsm{
 public TakEnvelope generateTakUnderZmk(String memberId,String ref){throw new IllegalStateException("External HSM adapter is required for TAK generation");}
 public ImportedTak importTakUnderZmk(String memberId,String ref,String encrypted,String kcv){throw new IllegalStateException("External HSM adapter is required for TAK import");}
}
