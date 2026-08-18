package com.futurpayment.softpos.api

import com.futurpayment.softpos.sdk.AcceptanceChannel

data class MobilePayment(val clientTransactionId:String,val idempotencyKey:String,val acceptanceChannel:AcceptanceChannel,val amountMinor:Long,val currency:String,val sdkCredentialReference:String,val integrityReference:String)
data class MobilePaymentResult(val clientTransactionId:String,val status:String,val responseCode:String?,val receiptReference:String?)
interface SoftPosBackendClient {
    suspend fun consumeActivation(code:String, publicKey:String, fingerprint:String, appVersion:String):String
    suspend fun submit(deviceId:String,payment:MobilePayment):MobilePaymentResult
    suspend fun status(deviceId:String,clientTransactionId:String):MobilePaymentResult
}
