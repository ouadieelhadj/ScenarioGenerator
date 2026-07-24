package com.staging.sg.common.iso;

import org.jpos.iso.*;

/**
 * Champ binaire a longueur variable, prefixe par 3 chiffres EBCDIC.
 *
 * Necessaire pour le DE55 (ICC System-Related Data) : la specification
 * DMAS le declare "b...255; LLLVAR", et le reseau attend la longueur
 * ecrite en EBCDIC comme tous les autres champs LLLVAR, suivie des
 * octets bruts.
 *
 *   IFB_LLLBINARY ecrit la longueur en BCD binaire (0x01 0x36) : le
 *   reseau ne sait pas la lire et le parsing du DE55 echoue.
 *   IFE_LLLCHAR ecrirait la longueur en EBCDIC mais convertirait aussi
 *   les DONNEES en EBCDIC, ce qui corromprait le TLV.
 *
 * Ici : longueur "136" en EBCDIC (F1F3F6), puis les octets tels quels.
 */
public class IFE_LLLBINARY extends ISOFieldPackager {

    public IFE_LLLBINARY() {
        super();
    }

    public IFE_LLLBINARY(int len, String description) {
        super(len, description);
    }

    @Override
    public byte[] pack(ISOComponent c) throws ISOException {
        byte[] data = (byte[]) c.getValue();
        if (data.length > getLength()) {
            throw new ISOException("Field length " + data.length
                    + " too long, max " + getLength());
        }
        // longueur sur 3 positions, en EBCDIC
        String lenStr = ISOUtil.zeropad(Integer.toString(data.length), 3);
        byte[] lenBytes = ISOUtil.asciiToEbcdic(lenStr);

        byte[] out = new byte[3 + data.length];
        System.arraycopy(lenBytes, 0, out, 0, 3);
        System.arraycopy(data,     0, out, 3, data.length);
        return out;
    }

    @Override
    public int unpack(ISOComponent c, byte[] b, int offset) throws ISOException {
        // 3 chiffres EBCDIC de longueur
        String lenStr = ISOUtil.ebcdicToAscii(b, offset, 3);
        int len;
        try {
            len = Integer.parseInt(lenStr.trim());
        } catch (NumberFormatException e) {
            throw new ISOException("Invalid LLL for binary field : '" + lenStr + "'");
        }
        if (len < 0 || len > getLength()) {
            throw new ISOException("Invalid LLL value " + len + " (max " + getLength() + ")");
        }
        byte[] value = new byte[len];
        System.arraycopy(b, offset + 3, value, 0, len);
        c.setValue(value);
        return 3 + len;
    }

    @Override
    public int getMaxPackedLength() {
        return 3 + getLength();
    }

    @Override
    public ISOComponent createComponent(int fieldNumber) {
        return new ISOBinaryField(fieldNumber);
    }
}
