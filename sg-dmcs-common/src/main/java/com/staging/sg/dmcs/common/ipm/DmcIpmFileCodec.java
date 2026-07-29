package com.staging.sg.dmcs.common.ipm;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Lecture et écriture streaming des fichiers IPM composés de messages jPOS
 * précédés de leur RDW.
 */
public final class DmcIpmFileCodec {

    private final ISOPackager packager;

    public DmcIpmFileCodec(ISOPackager packager) {
        this.packager = packager;
    }

    public void write(OutputStream output, List<ISOMsg> messages)
            throws IOException, ISOException {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Un fichier IPM doit contenir des messages");
        }
        for (ISOMsg message : messages) {
            message.setPackager(packager);
            DmcRdwCodec.write(output, message.pack());
        }
    }

    public List<ISOMsg> read(InputStream input) throws IOException, ISOException {
        List<ISOMsg> messages = new ArrayList<>();
        byte[] packed;
        while ((packed = DmcRdwCodec.read(input)) != null) {
            ISOMsg message = new ISOMsg();
            message.setPackager(packager);
            message.unpack(packed);
            messages.add(message);
        }
        return messages;
    }
}
