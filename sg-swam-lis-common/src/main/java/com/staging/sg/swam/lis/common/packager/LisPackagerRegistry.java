package com.staging.sg.swam.lis.common.packager;

import org.jpos.iso.ISOPackager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned catalogue of jPOS packagers supported by the LIS 4.13 implementation. */
public final class LisPackagerRegistry {
    private final Map<LisRecordKey, ISOPackager> packagers;

    public LisPackagerRegistry() {
        Map<LisRecordKey, ISOPackager> values = new LinkedHashMap<>();
        values.put(new LisRecordKey("90", 0), new LisTc90Tcr0Packager());
        values.put(new LisRecordKey("91", 0), new LisTc91Tcr0Packager());
        for (String code : new String[] {"92", "94", "96", "98", "80"}) {
            values.put(new LisRecordKey(code, 0), new LisLogicalHeaderPackager());
        }
        this.packagers = Collections.unmodifiableMap(values);
    }

    public ISOPackager require(String transactionCode, int tcrSequence) {
        LisRecordKey key = new LisRecordKey(transactionCode, tcrSequence);
        ISOPackager packager = packagers.get(key);
        if (packager == null) {
            throw new IllegalArgumentException(
                    "Unsupported LIS 4.13 record TC" + transactionCode + "/TCR" + tcrSequence);
        }
        return packager;
    }

    public Map<LisRecordKey, ISOPackager> supported() {
        return packagers;
    }
}
