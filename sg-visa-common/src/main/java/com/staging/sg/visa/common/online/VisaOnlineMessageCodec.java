package com.staging.sg.visa.common.online;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;

/** Packs and unpacks only the ISO message body; real Visa transport/header is a separate port. */
public final class VisaOnlineMessageCodec {
    public byte[] pack(ISOMsg message) {
        try {
            message.setPackager(new VisaOnlinePackager());
            return message.pack();
        } catch (ISOException e) {
            throw new IllegalArgumentException("Invalid Visa Online message", e);
        }
    }

    public ISOMsg unpack(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Empty Visa message");
        try {
            ISOMsg message = new ISOMsg();
            message.setPackager(new VisaOnlinePackager());
            message.unpack(bytes);
            return message;
        } catch (ISOException e) {
            throw new IllegalArgumentException("Malformed Visa Online message", e);
        }
    }
}
