package com.staging.sg.common.iso.sid;

import java.util.Map;
import java.util.Set;

/**
 * Contractual SID field-presence profile extracted from
 * Description Interface Switch SID V3.20 (sections 3.1, 3.3, 3.4 and 3.7).
 */
public record SidMessageProfile(
        String mti,
        Set<Integer> mandatoryFields,
        Set<Integer> conditionalFields,
        Set<Integer> echoedFields
) {
    private static final Map<String, SidMessageProfile> PROFILES = Map.ofEntries(
            Map.entry("1100", profile("1100",
                    ints(2,3,4,6,7,10,11,12,14,15,16,18,19,21,22,24,32,33,37,41,42,43,49,51,53,61,124,128),
                    ints(5,9,23,35,45,48,50,52,55,60,62,127),
                    ints())),
            Map.entry("1110", profile("1110",
                    ints(2,27,38,39,128),
                    ints(48,54,55,62,124,127),
                    ints(3,4,5,6,7,9,10,11,12,15,16,32,33,37,41,42,49,50,51))),
            Map.entry("1200", profile("1200",
                    ints(2,3,4,5,6,7,9,10,11,12,14,15,16,18,19,21,22,24,32,33,37,41,42,43,49,50,51,53,61,128),
                    ints(23,35,45,48,52,55,60,62),
                    ints())),
            Map.entry("1210", profile("1210",
                    ints(27,38,39,128),
                    ints(46,48,54,55,62),
                    ints(2,3,4,5,6,7,9,10,11,12,15,16,32,33,37,41,42,49,50,51))),
            Map.entry("1220", profile("1220",
                    ints(2,3,4,5,6,7,9,10,11,12,14,15,16,18,19,21,22,24,25,32,37,38,39,41,42,43,49,51,53,56,128),
                    ints(23,33,46,48,50,54,55,60,62),
                    ints())),
            Map.entry("1221", profile("1221",
                    ints(2,3,4,5,6,7,9,10,11,12,14,15,16,18,19,21,22,24,25,32,37,38,39,41,42,43,49,51,53,56,128),
                    ints(23,33,46,48,50,54,55,60,62),
                    ints())),
            Map.entry("1230", profile("1230",
                    ints(128),
                    ints(48,62),
                    ints(2,3,4,5,6,7,9,10,11,12,15,16,32,33,37,38,39,41,42,46,49,60))),
            Map.entry("1420", profile("1420",
                    ints(2,3,4,6,7,10,11,12,15,16,19,21,24,25,32,33,37,39,41,42,43,49,50,51,53,56,124,128),
                    ints(5,9,23,30,38,48,60,61,62),
                    ints())),
            Map.entry("1421", profile("1421",
                    ints(2,3,4,6,7,10,11,12,15,16,19,21,24,25,32,33,37,39,41,42,43,49,50,51,53,56,124,128),
                    ints(5,9,23,30,38,48,60,61,62),
                    ints())),
            Map.entry("1430", profile("1430",
                    ints(38,39,53,128),
                    ints(48,124),
                    ints(2,3,4,5,6,7,11,12,15,16,32,33,37,41,42,43,49,50,51)))
    );

    public static SidMessageProfile forMti(String mti) {
        SidMessageProfile profile = PROFILES.get(mti);
        if (profile == null) {
            throw new IllegalArgumentException("MTI transactionnel SID non supporte: " + mti);
        }
        return profile;
    }

    private static SidMessageProfile profile(
            String mti, Set<Integer> mandatory, Set<Integer> conditional, Set<Integer> echoed) {
        return new SidMessageProfile(mti, mandatory, conditional, echoed);
    }

    private static Set<Integer> ints(Integer... values) {
        return Set.of(values);
    }
}
