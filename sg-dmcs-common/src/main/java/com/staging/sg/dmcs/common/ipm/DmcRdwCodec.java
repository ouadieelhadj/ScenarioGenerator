package com.staging.sg.dmcs.common.ipm;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Record Descriptor Word du fichier VBS IPM.
 *
 * <p>Le RDW occupe quatre octets. Les deux premiers contiennent la longueur
 * totale de l'enregistrement, RDW inclus, en ordre réseau. Les deux derniers
 * sont réservés et positionnés à zéro.</p>
 */
public final class DmcRdwCodec {

    public static final int RDW_LENGTH = 4;

    private DmcRdwCodec() {
    }

    public static void write(OutputStream output, byte[] message) throws IOException {
        if (message == null || message.length == 0) {
            throw new IllegalArgumentException("Un message IPM vide est interdit");
        }
        int recordLength = message.length + RDW_LENGTH;
        if (recordLength > 0xFFFF) {
            throw new IllegalArgumentException("Message IPM trop long: " + message.length);
        }
        DataOutputStream data = new DataOutputStream(output);
        data.writeShort(recordLength);
        data.writeShort(0);
        data.write(message);
    }

    /**
     * @return le prochain message, ou {@code null} lorsque l'EOF est atteint
     * exactement entre deux enregistrements.
     */
    public static byte[] read(InputStream input) throws IOException {
        DataInputStream data = new DataInputStream(input);
        int first = data.read();
        if (first < 0) {
            return null;
        }
        int second = data.read();
        int reserved1 = data.read();
        int reserved2 = data.read();
        if (second < 0 || reserved1 < 0 || reserved2 < 0) {
            throw new EOFException("RDW tronqué");
        }
        if (reserved1 != 0 || reserved2 != 0) {
            throw new IOException("Octets réservés RDW non nuls");
        }
        int recordLength = (first << 8) | second;
        if (recordLength <= RDW_LENGTH) {
            throw new IOException("Longueur RDW invalide: " + recordLength);
        }
        byte[] message = new byte[recordLength - RDW_LENGTH];
        data.readFully(message);
        return message;
    }
}
