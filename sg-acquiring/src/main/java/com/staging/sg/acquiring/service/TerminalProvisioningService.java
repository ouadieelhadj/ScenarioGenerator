package com.staging.sg.acquiring.service;

import com.staging.sg.acquiring.domain.*;
import com.staging.sg.acquiring.port.ServerPosProvisioningPort;
import com.staging.sg.acquiring.port.ServerPosTerminalConfiguration;
import com.staging.sg.acquiring.repository.*;
import com.staging.sg.common.contract.PaymentContractStatus;
import com.staging.sg.common.contract.PaymentContractType;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TerminalProvisioningService {
    private final TerminalDeviceRepository terminals;
    private final TerminalAssignmentRepository assignments;
    private final AcquiringContractRepository contracts;
    private final AcquiringContractDetailRepository contractDetails;
    private final AcquiringDeviceContractDetailRepository deviceDetails;
    private final ServerPosProvisioningPort serverPos;
    private final AcquiringOutboxEventRepository outbox;

    public TerminalProvisioningService(TerminalDeviceRepository terminals,
            TerminalAssignmentRepository assignments,
            AcquiringContractRepository contracts,
            AcquiringContractDetailRepository contractDetails,
            AcquiringDeviceContractDetailRepository deviceDetails,
            ServerPosProvisioningPort serverPos,
            AcquiringOutboxEventRepository outbox) {
        this.terminals = terminals;
        this.assignments = assignments;
        this.contracts = contracts;
        this.contractDetails = contractDetails;
        this.deviceDetails = deviceDetails;
        this.serverPos = serverPos;
        this.outbox = outbox;
    }

    public TerminalDevice provision(UUID terminalDeviceId, String acquirerId,
            String correlationId) {
        TerminalDevice terminal = terminal(terminalDeviceId, acquirerId);
        if (terminal.status() != TerminalStatus.ASSIGNED
                && terminal.status() != TerminalStatus.PROVISIONING) {
            throw new IllegalStateException(
                    "Only an assigned or provisioning terminal can be provisioned");
        }
        TerminalAssignment assignment = assignments
                .findByTerminalDeviceIdAndActiveTrue(terminalDeviceId)
                .orElseThrow(() -> new IllegalStateException("Active assignment is required"));
        AcquiringContract deviceContract = contracts.findById(assignment.deviceContractId())
                .orElseThrow(() -> new IllegalStateException("Device contract is missing"));
        if (!deviceContract.institutionId().equals(acquirerId)
                || deviceContract.contractType() != PaymentContractType.ACQUIRING_DEVICE
                || deviceContract.status() != PaymentContractStatus.ACTIVE) {
            throw new IllegalStateException("An active device contract is required");
        }
        AcquiringContract parent = contracts.findById(deviceContract.parentContractId())
                .orElseThrow(() -> new IllegalStateException("Parent contract is missing"));
        if (parent.status() != PaymentContractStatus.ACTIVE) {
            throw new IllegalStateException("The parent merchant contract must be active");
        }
        AcquiringContractDetail parentDetail = contractDetails.findById(parent.id())
                .orElseThrow(() -> new IllegalStateException("Merchant contract detail is missing"));
        AcquiringDeviceContractDetail deviceDetail = deviceDetails.findById(deviceContract.id())
                .orElseThrow(() -> new IllegalStateException("Device contract detail is missing"));

        terminal.provisioning();
        terminals.save(terminal);
        serverPos.provision(new ServerPosTerminalConfiguration(
                terminal.id(), deviceContract.id(), deviceDetail.terminalId(),
                parentDetail.merchantAcceptorId(), deviceDetail.extendedSet(),
                deviceDetail.macData(), deviceDetail.macRequired(), "000000"));
        terminal.ready();
        terminals.save(terminal);
        outbox.save(AcquiringOutboxEvent.pending("TerminalDevice", terminal.id(),
                "TerminalProvisioned", correlationId,
                "{\"terminalId\":\"" + deviceDetail.terminalId() + "\"}"));
        return terminal;
    }

    public TerminalDevice activate(UUID terminalDeviceId, String acquirerId,
            String correlationId) {
        TerminalDevice terminal = terminal(terminalDeviceId, acquirerId);
        terminal.activate();
        terminals.save(terminal);
        outbox.save(AcquiringOutboxEvent.pending("TerminalDevice", terminal.id(),
                "TerminalActivated", correlationId, "{\"status\":\"ACTIVE\"}"));
        return terminal;
    }

    private TerminalDevice terminal(UUID id, String acquirerId) {
        TerminalDevice terminal = terminals.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown terminal"));
        if (!terminal.acquirerId().equals(acquirerId)) {
            throw new IllegalArgumentException("Unknown terminal");
        }
        return terminal;
    }
}
