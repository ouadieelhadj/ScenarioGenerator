package com.staging.sg.waypos.server.service;

import com.staging.sg.waypos.server.domain.PosFileUpdate;
import com.staging.sg.waypos.server.repository.PosFileUpdateRepository;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;

@Service
public class WayPosFileUpdateService {
    private final PosFileUpdateRepository updates;

    public WayPosFileUpdateService(PosFileUpdateRepository updates) {
        this.updates = updates;
    }

    @Transactional
    public boolean receive(ISOMsg request, String terminalId) {
        try {
            if (!request.hasField(47) || request.getBytes(47).length == 0) {
                return false;
            }
            byte[] data = request.getBytes(47);
            String fingerprint = ISOUtil.hexString(
                    MessageDigest.getInstance("SHA-256").digest(request.pack()));
            if (!updates.existsByTerminalIdAndMessageFingerprint(
                    terminalId, fingerprint)) {
                updates.save(PosFileUpdate.received(terminalId, fingerprint, data));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
