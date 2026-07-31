package com.staging.sg.card.issuing.port;

public interface CardSecurityPort {
    SecurityResult verify(SecurityCommand command);
    record SecurityCommand(
            String issuerId, String paymentIdentifierVaultReference,
            String pinBlockHex, String pinKeyDomain, String emvDataHex,
            String transactionId, String correlationId) {
        @Override public String toString(){
            return "SecurityCommand[issuerId="+issuerId
                    +", transactionId="+transactionId+", sensitiveData=REDACTED]";
        }
    }
    record SecurityResult(SecurityStatus status,String responseCode,String arpcHex){}
    enum SecurityStatus { VERIFIED, DECLINED, UNAVAILABLE }
}
