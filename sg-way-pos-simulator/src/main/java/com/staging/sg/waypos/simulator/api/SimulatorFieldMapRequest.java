package com.staging.sg.waypos.simulator.api;

import java.util.List;
import java.util.Map;

/**
 * Low-level request used by certification harnesses that must reproduce an
 * exact primary-bitmap WayPos message without adding fields to the public
 * transaction DTO for every certification profile.
 */
public record SimulatorFieldMapRequest(
        String mti,
        Map<String, String> fields,
        Map<String, String> binaryFields,
        List<Integer> unsetFields,
        String pin,
        Boolean macEnabled,
        Boolean validate) {
}
