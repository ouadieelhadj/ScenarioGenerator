package com.futurpayment.softpos.payment

import com.futurpayment.softpos.api.*
import com.futurpayment.softpos.sdk.*
import java.util.UUID

class PaymentCoordinator(private val sdk:SoftPosSdkAdapter,private val backend:SoftPosBackendClient) {
    suspend fun purchase(deviceId:String,amountMinor:Long,currency:String,channel:AcceptanceChannel):MobilePaymentResult {
        val transactionId=UUID.randomUUID().toString(); val outcome=sdk.accept(SdkCommand(amountMinor,currency,channel))
        return backend.submit(deviceId,MobilePayment(transactionId,transactionId,channel,amountMinor,currency,outcome.credentialReference,outcome.integrityReference))
    }
    suspend fun resolveUnknown(deviceId:String,transactionId:String):MobilePaymentResult = backend.status(deviceId,transactionId)
}
