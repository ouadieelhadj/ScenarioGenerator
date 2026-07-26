package com.staging.sg.swam.lis.common.packager;

import org.jpos.iso.ISOBasePackager;
import org.jpos.iso.ISOFieldPackager;

/**
 * Base jPOS for LIS physical records.
 *
 * LIS records have no ISO 8583 MTI and no bitmap. jPOS field 0 is therefore
 * deliberately used for LIS F.001 (the two-character transaction code).
 */
public abstract class LisFixedRecordPackager extends ISOBasePackager {
    public static final int RECORD_LENGTH = 256;

    protected LisFixedRecordPackager(ISOFieldPackager... fields) {
        setFieldPackager(fields);
    }

    @Override
    protected boolean emitBitMap() {
        return false;
    }

    @Override
    protected int getFirstField() {
        // ISOBasePackager always handles field 0 first, then starts here.
        return 1;
    }
}
