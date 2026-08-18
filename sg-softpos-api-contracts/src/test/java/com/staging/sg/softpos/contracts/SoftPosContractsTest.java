package com.staging.sg.softpos.contracts;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.RecordComponent;
import java.util.*;
import org.junit.jupiter.api.Test;

class SoftPosContractsTest {
    @Test void mobilePaymentContractExcludesSensitiveCardFields() {
        Set<String> names = new HashSet<>();
        for (RecordComponent field : SoftPosContracts.PaymentRequest.class.getRecordComponents()) names.add(field.getName().toLowerCase());
        assertFalse(names.contains("pan")); assertFalse(names.contains("expiry"));
        assertFalse(names.contains("pin")); assertFalse(names.contains("pinblock"));
        assertFalse(names.contains("key")); assertTrue(names.contains("sdkcredentialreference"));
    }
    @Test void threeAcceptanceChannelsAreStable() {
        assertArrayEquals(new SoftPosContracts.AcceptanceChannel[]{SoftPosContracts.AcceptanceChannel.NFC,
                SoftPosContracts.AcceptanceChannel.QR_MPM, SoftPosContracts.AcceptanceChannel.QR_CPM},
                SoftPosContracts.AcceptanceChannel.values());
    }
}
