package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "acquiring_ecommerce_contract_detail", uniqueConstraints =
        @UniqueConstraint(name = "uk_acquiring_ecommerce_tid",
                columnNames = {"acquirer_id", "logical_terminal_id"}))
public class AcquiringEcommerceContractDetail {
    @Id @Column(name = "contract_id") private UUID contractId;
    @Column(name = "acquirer_id", nullable = false, length = 64) private String acquirerId;
    @Column(name = "outlet_id", nullable = false) private UUID outletId;
    @Column(name = "store_id", nullable = false) private UUID storeId;
    @Column(name = "logical_terminal_id", nullable = false, length = 8) private String logicalTerminalId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private AcceptanceChannel channel;
    @Column(name = "source_store_request_id", nullable = false) private UUID sourceStoreRequestId;

    protected AcquiringEcommerceContractDetail() {}
    public static AcquiringEcommerceContractDetail of(UUID contractId, String acquirerId,
            UUID outletId, UUID storeId, String logicalTerminalId, AcceptanceChannel channel,
            UUID sourceStoreRequestId) {
        if (contractId == null || AcceptanceProduct.blank(acquirerId) || outletId == null
                || storeId == null || logicalTerminalId == null
                || !logicalTerminalId.matches("[A-Za-z0-9]{8}") || sourceStoreRequestId == null
                || (channel != AcceptanceChannel.ECOMMERCE && channel != AcceptanceChannel.BOTH))
            throw new IllegalArgumentException("ECOM-004: invalid ecommerce contract detail");
        AcquiringEcommerceContractDetail value = new AcquiringEcommerceContractDetail();
        value.contractId = contractId; value.acquirerId = acquirerId; value.outletId = outletId;
        value.storeId = storeId; value.logicalTerminalId = logicalTerminalId;
        value.channel = channel; value.sourceStoreRequestId = sourceStoreRequestId;
        return value;
    }
    public UUID contractId() { return contractId; }
    public String logicalTerminalId() { return logicalTerminalId; }
}
