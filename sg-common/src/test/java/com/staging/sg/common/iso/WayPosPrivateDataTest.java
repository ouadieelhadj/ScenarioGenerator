package com.staging.sg.common.iso;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WayPosPrivateDataTest {
    @Test
    void roundTripsVersionAndAccountNumberTables() {
        var expected = List.of(
                new WayPosPrivateData.Item("SV", "1.0.0"),
                new WayPosPrivateData.Item("60", "5321962145453348"));
        assertEquals(expected, WayPosPrivateData.decode(
                WayPosPrivateData.encode(expected)));
    }
}
