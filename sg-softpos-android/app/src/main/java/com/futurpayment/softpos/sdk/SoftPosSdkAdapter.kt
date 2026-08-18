package com.futurpayment.softpos.sdk

enum class AcceptanceChannel { NFC, QR_MPM, QR_CPM }
data class SdkCommand(val amountMinor:Long,val currency:String,val channel:AcceptanceChannel)
data class SdkOutcome(val credentialReference:String,val integrityReference:String,val providerReference:String)
interface SoftPosSdkAdapter { suspend fun warmup(); suspend fun accept(command:SdkCommand):SdkOutcome }
