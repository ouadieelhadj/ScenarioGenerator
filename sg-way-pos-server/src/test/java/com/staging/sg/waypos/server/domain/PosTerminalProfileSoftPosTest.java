package com.staging.sg.waypos.server.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PosTerminalProfileSoftPosTest {
    @Test void legacyProvisioningDefaultsToPhysicalPosWithoutChangingTidMid() {
        PosTerminalProfile terminal = PosTerminalProfile.provisioned("12345678", "123456789012345", true, "BIN", false, "000001");
        assertEquals("PHYSICAL_POS", terminal.getTerminalType()); assertEquals("12345678", terminal.getTerminalId());
        assertEquals("123456789012345", terminal.getMerchantId());
    }
    @Test void softPosProvisioningCarriesMemberAndOutlet() {
        PosTerminalProfile terminal = PosTerminalProfile.provisionedSoftPos("87654321", "543210987654321", "MEMBER-A", "OUTLET-1", true, "BIN", false, "000001");
        assertEquals("SOFTPOS", terminal.getTerminalType()); assertEquals("MEMBER-A", terminal.getMemberId()); assertEquals("OUTLET-1", terminal.getOutletId());
    }
}
