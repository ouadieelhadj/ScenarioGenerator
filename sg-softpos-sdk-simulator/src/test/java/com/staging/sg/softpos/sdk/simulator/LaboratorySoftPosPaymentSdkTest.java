package com.staging.sg.softpos.sdk.simulator;

import static com.staging.sg.softpos.contracts.SoftPosContracts.AcceptanceChannel.NFC;
import static org.junit.jupiter.api.Assertions.*;
import com.staging.sg.softpos.contracts.SoftPosPaymentSdk.SdkRequest;
import org.junit.jupiter.api.Test;

class LaboratorySoftPosPaymentSdkTest {
    @Test void emitsOpaqueAliasWithoutPan() {
        var result = new LaboratorySoftPosPaymentSdk(true).accept(new SdkRequest(1000, "MAD", NFC));
        assertEquals("LABREF:APPROVED_CARD", result.sdkCredentialReference());
        assertFalse(result.sdkCredentialReference().matches(".*\\d{13,19}.*"));
    }
    @Test void cannotRunInProductionBuild() {
        assertThrows(IllegalStateException.class, () -> new LaboratorySoftPosPaymentSdk(false).accept(new SdkRequest(1000, "MAD", NFC)));
    }
}
