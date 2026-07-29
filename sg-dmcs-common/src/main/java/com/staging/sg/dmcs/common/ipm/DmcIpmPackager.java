package com.staging.sg.dmcs.common.ipm;

import org.jpos.iso.IFB_BITMAP;
import org.jpos.iso.IFE_CHAR;
import org.jpos.iso.IFE_LLCHAR;
import org.jpos.iso.IFE_LLLCHAR;
import org.jpos.iso.IFE_LLNUM;
import org.jpos.iso.IFE_NUMERIC;
import org.jpos.iso.ISOBasePackager;
import org.jpos.iso.ISOFieldPackager;

/**
 * Packager du premier périmètre Mastercard IPM (ISO 8583:1993).
 *
 * <p>Le guide DMC impose l'encodage EBCDIC pour les données, les MTI et les
 * préfixes de longueur. Les bitmaps restent binaires. Ce packager couvre le
 * socle nécessaire aux fichiers, présentations et premiers cycles de litige.
 * Les champs non encore qualifiés ne sont pas exposés silencieusement : ils
 * seront ajoutés avec leurs règles de présence et leurs tests.</p>
 */
public final class DmcIpmPackager extends ISOBasePackager {

    public DmcIpmPackager() {
        setFieldPackager(buildFields());
    }

    private ISOFieldPackager[] buildFields() {
        ISOFieldPackager[] fields = new ISOFieldPackager[129];

        fields[0] = new IFE_NUMERIC(4, "Message Type Identifier");
        fields[1] = new IFB_BITMAP(16, "Bit Map, Secondary");
        fields[2] = new IFE_LLNUM(19, "Primary Account Number");
        fields[3] = new IFE_NUMERIC(6, "Processing Code");
        fields[4] = new IFE_NUMERIC(12, "Amount, Transaction");
        fields[5] = new IFE_NUMERIC(12, "Amount, Reconciliation");
        fields[6] = new IFE_NUMERIC(12, "Amount, Cardholder Billing");
        fields[9] = new IFE_NUMERIC(8, "Conversion Rate, Reconciliation");
        fields[10] = new IFE_NUMERIC(8, "Conversion Rate, Cardholder Billing");
        fields[12] = new IFE_NUMERIC(12, "Date and Time, Local Transaction");
        fields[14] = new IFE_NUMERIC(4, "Date, Expiration");
        fields[22] = new IFE_CHAR(12, "Point of Service Data Code");
        fields[23] = new IFE_NUMERIC(3, "Card Sequence Number");
        fields[24] = new IFE_NUMERIC(3, "Function Code");
        fields[25] = new IFE_NUMERIC(4, "Message Reason Code");
        fields[26] = new IFE_NUMERIC(4, "Acceptor Business Code");
        fields[30] = new IFE_NUMERIC(24, "Amounts, Original");
        fields[31] = new IFE_LLNUM(23, "Acquirer Reference Data");
        fields[32] = new IFE_LLNUM(11, "Acquiring Institution ID Code");
        fields[33] = new IFE_LLNUM(11, "Forwarding Institution ID Code");
        fields[37] = new IFE_CHAR(12, "Retrieval Reference Number");
        fields[38] = new IFE_CHAR(6, "Approval Code");
        fields[40] = new IFE_CHAR(3, "Service Code");
        fields[41] = new IFE_CHAR(8, "Acceptor Terminal ID");
        fields[42] = new IFE_CHAR(15, "Acceptor ID Code");
        fields[43] = new IFE_LLCHAR(99, "Acceptor Name and Location");
        fields[48] = new IFE_LLLCHAR(999, "Additional Data");
        fields[49] = new IFE_CHAR(3, "Currency Code, Transaction");
        fields[50] = new IFE_CHAR(3, "Currency Code, Reconciliation");
        fields[51] = new IFE_CHAR(3, "Currency Code, Cardholder Billing");
        fields[54] = new IFE_LLLCHAR(120, "Amounts, Additional");
        fields[62] = new IFE_LLLCHAR(999, "Additional Data 2");
        fields[63] = new IFE_LLLCHAR(16, "Transaction Life Cycle ID");
        fields[71] = new IFE_NUMERIC(8, "Message Number");
        fields[72] = new IFE_LLLCHAR(999, "Data Record");
        fields[93] = new IFE_LLNUM(11, "Transaction Destination Institution ID Code");
        fields[94] = new IFE_LLNUM(11, "Transaction Originator Institution ID Code");
        fields[95] = new IFE_CHAR(42, "Card Issuer Reference Data");
        fields[100] = new IFE_LLNUM(11, "Receiving Institution ID Code");
        fields[105] = new IFE_LLLCHAR(999, "Multi-Use Transaction Identification Data");
        fields[123] = new IFE_LLLCHAR(999, "Additional Data 3");
        fields[124] = new IFE_LLLCHAR(999, "Additional Data 4");
        fields[125] = new IFE_LLLCHAR(999, "Additional Data 5");

        return fields;
    }
}
