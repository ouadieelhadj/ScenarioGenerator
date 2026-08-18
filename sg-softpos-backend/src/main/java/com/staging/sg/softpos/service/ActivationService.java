package com.staging.sg.softpos.service;

import com.staging.sg.softpos.contracts.SoftPosContracts.*;
import com.staging.sg.softpos.domain.*;
import com.staging.sg.softpos.repository.SoftPosRepositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;

@Service
public class ActivationService {
    private final ActivationRepository activations; private final DeviceRepository devices;
    public ActivationService(ActivationRepository activations, DeviceRepository devices) { this.activations = activations; this.devices = devices; }

    @Transactional
    public ActivationResponse consume(ActivationConsumeRequest request) {
        SoftPosActivation activation = activations.findLockedByHash(SoftPosHashing.sha256(request.activationCode()))
                .orElseThrow(() -> new IllegalArgumentException("Activation unavailable"));
        activation.consume(Instant.now());
        SoftPosDevice device = SoftPosDevice.activate(activation.getMemberId(), activation.getMerchantId(), activation.getOutletId(),
                activation.getTerminalId(), SoftPosHashing.sha256(request.deviceFingerprint()),
                SoftPosHashing.sha256(request.devicePublicKey()), request.applicationVersion());
        devices.save(device); activations.save(activation);
        return new ActivationResponse(device.getDeviceId(), device.getTerminalId(), device.getStatus(), UUID.randomUUID().toString(), Instant.now().plus(Duration.ofMinutes(5)));
    }

    @Transactional
    public void attest(String memberId, IntegrityVerdictRequest request, Duration validity) {
        SoftPosDevice device = devices.findByDeviceIdAndMemberId(request.deviceId(), memberId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));
        if (request.nonce() == null || request.nonce().isBlank() || request.verdictToken() == null || request.verdictToken().isBlank())
            throw new IllegalArgumentException("Integrity verdict incomplete");
        device.attest(Instant.now().plus(validity)); devices.save(device);
    }
}
