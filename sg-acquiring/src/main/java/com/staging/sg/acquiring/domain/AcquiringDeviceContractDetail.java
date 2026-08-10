package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "acquiring_device_contract_detail", uniqueConstraints =
        @UniqueConstraint(name = "uk_acquiring_tid", columnNames = {"acquirer_id", "terminal_id"}))
public class AcquiringDeviceContractDetail {
    @Id
    @Column(name = "contract_id")
    private UUID contractId;
    @Column(name = "acquirer_id", nullable = false, length = 64, updatable = false)
    private String acquirerId;
    @Column(name = "outlet_id", nullable = false, updatable = false)
    private UUID outletId;
    @Column(name = "terminal_id", nullable = false, length = 8, updatable = false)
    private String terminalId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AcceptanceChannel channel;
    @Column(name = "extended_set", nullable = false)
    private boolean extendedSet;
    @Column(name = "mac_data", nullable = false, length = 3)
    private String macData;
    @Column(name = "mac_required", nullable = false)
    private boolean macRequired;
    @Column(name = "source_terminal_request_id") private UUID sourceTerminalRequestId;
    @Column(name = "request_ordinal") private Integer requestOrdinal;
    @Column(name = "requested_model_code", length = 64) private String requestedModelCode;
    @Column(name = "requested_connectivity_code", length = 64) private String requestedConnectivityCode;
    @Column(name = "requested_option_codes", length = 1000) private String requestedOptionCodes;

    protected AcquiringDeviceContractDetail() {}

    public static AcquiringDeviceContractDetail of(UUID contractId, String acquirerId,
            UUID outletId, String terminalId, AcceptanceChannel channel,
            boolean extendedSet, String macData, boolean macRequired) {
        if (contractId == null || AcceptanceProduct.blank(acquirerId) || outletId == null
                || terminalId == null || !terminalId.matches("[A-Za-z0-9]{8}")
                || channel == null || !channel.supportsTpe()
                || !("BIN".equals(macData) || "HEX".equals(macData))) {
            throw new IllegalArgumentException("Invalid device contract detail");
        }
        AcquiringDeviceContractDetail value = new AcquiringDeviceContractDetail();
        value.contractId = contractId;
        value.acquirerId = acquirerId;
        value.outletId = outletId;
        value.terminalId = terminalId;
        value.channel = channel;
        value.extendedSet = extendedSet;
        value.macData = macData;
        value.macRequired = macRequired;
        return value;
    }

    public static AcquiringDeviceContractDetail ofRequest(UUID contractId, String acquirerId,
            UUID outletId, String terminalId, AcceptanceChannel channel,
            boolean extendedSet, String macData, boolean macRequired,
            UUID sourceTerminalRequestId, int requestOrdinal, String modelCode,
            String connectivityCode, String optionCodes) {
        AcquiringDeviceContractDetail value = of(contractId, acquirerId, outletId, terminalId,
                channel, extendedSet, macData, macRequired);
        if (sourceTerminalRequestId == null || requestOrdinal < 1
                || AcceptanceProduct.blank(modelCode) || AcceptanceProduct.blank(connectivityCode))
            throw new IllegalArgumentException("TPE-001/TPE-002/TPE-003: invalid source request");
        value.sourceTerminalRequestId = sourceTerminalRequestId;
        value.requestOrdinal = requestOrdinal;
        value.requestedModelCode = modelCode;
        value.requestedConnectivityCode = connectivityCode;
        value.requestedOptionCodes = optionCodes == null ? "" : optionCodes;
        return value;
    }

    public UUID contractId() { return contractId; }
    public UUID outletId() { return outletId; }
    public String terminalId() { return terminalId; }
    public AcceptanceChannel channel() { return channel; }
    public boolean extendedSet() { return extendedSet; }
    public String macData() { return macData; }
    public boolean macRequired() { return macRequired; }
    public UUID sourceTerminalRequestId() { return sourceTerminalRequestId; }
    public Integer requestOrdinal() { return requestOrdinal; }
    public String requestedModelCode() { return requestedModelCode; }
    public String requestedConnectivityCode() { return requestedConnectivityCode; }
    public String requestedOptionCodes() { return requestedOptionCodes; }
}
