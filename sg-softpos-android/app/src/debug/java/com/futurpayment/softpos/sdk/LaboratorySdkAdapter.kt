package com.futurpayment.softpos.sdk

import com.futurpayment.softpos.BuildConfig
import java.util.UUID

class LaboratorySdkAdapter : SoftPosSdkAdapter {
    override suspend fun warmup() { check(BuildConfig.LABORATORY_SDK) }
    override suspend fun accept(command:SdkCommand):SdkOutcome {
        check(BuildConfig.LABORATORY_SDK) { "Laboratory SDK is forbidden in release builds" }
        require(command.amountMinor > 0)
        return SdkOutcome("LABREF:APPROVED_CARD", "LAB-INTEGRITY:${UUID.randomUUID()}", UUID.randomUUID().toString())
    }
}
