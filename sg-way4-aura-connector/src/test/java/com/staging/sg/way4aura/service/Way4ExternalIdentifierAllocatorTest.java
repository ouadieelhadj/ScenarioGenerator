package com.staging.sg.way4aura.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Way4ExternalIdentifierAllocatorTest {
    @Test void formatsFirstApprovedCarsdbValues() {
        assertEquals("990001000000001", format(990001000000001L, "MID"));
        assertEquals("99000001", format(99000001L, "TID"));
        assertEquals("LCAR00000001", format(1L, "MERCHANT_CONTRACT"));
    }

    @Test void blocksValuesOutsideApprovedRanges() {
        assertThrows(AuraMappingBlockedException.class,
                () -> format(990002000000000L, "MID"));
        assertThrows(AuraMappingBlockedException.class,
                () -> format(100000000L, "TID"));
        assertThrows(AuraMappingBlockedException.class,
                () -> format(100000000L, "MERCHANT_CONTRACT"));
    }
    private static String format(long number,String type){
        return Way4ExternalIdentifierAllocator.format(number,type,990001000000000L,990001999999999L,
                99000000L,99999999L,"LCAR",8);
    }
}
