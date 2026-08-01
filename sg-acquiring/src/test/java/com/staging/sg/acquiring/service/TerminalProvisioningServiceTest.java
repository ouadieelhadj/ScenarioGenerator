package com.staging.sg.acquiring.service;

import com.staging.sg.acquiring.domain.*;
import com.staging.sg.acquiring.port.ServerPosProvisioningPort;
import com.staging.sg.acquiring.port.ServerPosTerminalConfiguration;
import com.staging.sg.acquiring.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TerminalProvisioningServiceTest {
    @Test
    void projectsAcquisitionTerminalToServerPosThenActivatesIt() {
        TerminalDeviceRepository terminals = mock(TerminalDeviceRepository.class);
        TerminalAssignmentRepository assignments = mock(TerminalAssignmentRepository.class);
        AcquiringContractRepository contracts = mock(AcquiringContractRepository.class);
        AcquiringContractDetailRepository contractDetails = mock(AcquiringContractDetailRepository.class);
        AcquiringDeviceContractDetailRepository deviceDetails = mock(AcquiringDeviceContractDetailRepository.class);
        ServerPosProvisioningPort serverPos = mock(ServerPosProvisioningPort.class);
        AcquiringOutboxEventRepository outbox = mock(AcquiringOutboxEventRepository.class);

        UUID merchantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID outletId = UUID.randomUUID();
        AcquiringContract parent = activeMerchantContract(merchantId, productId);
        AcquiringContract device = activeDeviceContract(merchantId, productId, parent.id());
        AcquiringContractDetail parentDetail = AcquiringContractDetail.of(parent.id(),
                "ACQ1", merchantId, "MERCHANT0000001", "5411", "504",
                AcceptanceChannel.BOTH);
        AcquiringDeviceContractDetail deviceDetail = AcquiringDeviceContractDetail.of(
                device.id(), "ACQ1", outletId, "TERM0001", AcceptanceChannel.TPE,
                true, "BIN", true);
        TerminalDevice terminal = TerminalDevice.inStock("ACQ1", "SN-1", "MODEL-1");
        terminal.assign();
        TerminalAssignment assignment = TerminalAssignment.active(
                terminal.id(), outletId, device.id());

        when(terminals.findById(terminal.id())).thenReturn(Optional.of(terminal));
        when(assignments.findByTerminalDeviceIdAndActiveTrue(terminal.id()))
                .thenReturn(Optional.of(assignment));
        when(contracts.findById(device.id())).thenReturn(Optional.of(device));
        when(contracts.findById(parent.id())).thenReturn(Optional.of(parent));
        when(contractDetails.findById(parent.id())).thenReturn(Optional.of(parentDetail));
        when(deviceDetails.findById(device.id())).thenReturn(Optional.of(deviceDetail));

        TerminalProvisioningService service = new TerminalProvisioningService(terminals,
                assignments, contracts, contractDetails, deviceDetails, serverPos, outbox);
        TerminalDevice ready = service.provision(terminal.id(), "ACQ1", "corr-1");

        ArgumentCaptor<ServerPosTerminalConfiguration> projection =
                ArgumentCaptor.forClass(ServerPosTerminalConfiguration.class);
        verify(serverPos).provision(projection.capture());
        assertEquals("TERM0001", projection.getValue().terminalId());
        assertEquals("MERCHANT0000001", projection.getValue().merchantId());
        assertEquals("BIN", projection.getValue().macData());
        assertEquals(TerminalStatus.READY, ready.status());
        assertEquals(TerminalStatus.ACTIVE,
                service.activate(terminal.id(), "ACQ1", "corr-2").status());
        verify(outbox, times(2)).save(any());
    }

    private static AcquiringContract activeMerchantContract(UUID merchantId, UUID productId) {
        AcquiringContract value = AcquiringContract.merchant("ACQ1", "MC-1", merchantId,
                "SETTLEMENT", productId, "maker-1", "idem-1", "a".repeat(64));
        value.submit();
        value.approve("checker-1");
        return value;
    }

    private static AcquiringContract activeDeviceContract(UUID merchantId, UUID productId,
            UUID parentId) {
        AcquiringContract value = AcquiringContract.device("ACQ1", "DC-1", merchantId,
                parentId, productId, "maker-2", "idem-2", "b".repeat(64));
        value.submit();
        value.approve("checker-2");
        return value;
    }
}
