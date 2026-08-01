package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "acquiring_contract_detail", uniqueConstraints =
        @UniqueConstraint(name = "uk_acquiring_mid", columnNames = {"acquirer_id", "merchant_acceptor_id"}))
public class AcquiringContractDetail {
    @Id
    @Column(name = "contract_id")
    private UUID contractId;
    @Column(name = "acquirer_id", nullable = false, length = 64, updatable = false)
    private String acquirerId;
    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;
    @Column(name = "merchant_acceptor_id", nullable = false, length = 15, updatable = false)
    private String merchantAcceptorId;
    @Column(nullable = false, length = 4)
    private String mcc;
    @Column(name = "settlement_currency", nullable = false, length = 3)
    private String settlementCurrency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AcceptanceChannel channel;

    protected AcquiringContractDetail() {}

    public static AcquiringContractDetail of(UUID contractId, String acquirerId,
            UUID merchantId, String mid, String mcc, String settlementCurrency,
            AcceptanceChannel channel) {
        if (contractId == null || AcceptanceProduct.blank(acquirerId) || merchantId == null
                || mid == null || !mid.matches("[A-Za-z0-9]{1,15}")
                || mcc == null || !mcc.matches("\\d{4}")
                || !AcceptanceProduct.currency(settlementCurrency) || channel == null) {
            throw new IllegalArgumentException("Invalid acquiring contract detail");
        }
        AcquiringContractDetail value = new AcquiringContractDetail();
        value.contractId = contractId;
        value.acquirerId = acquirerId;
        value.merchantId = merchantId;
        value.merchantAcceptorId = mid;
        value.mcc = mcc;
        value.settlementCurrency = settlementCurrency;
        value.channel = channel;
        return value;
    }

    public UUID contractId() { return contractId; }
    public UUID merchantId() { return merchantId; }
    public String merchantAcceptorId() { return merchantAcceptorId; }
    public String mcc() { return mcc; }
    public String settlementCurrency() { return settlementCurrency; }
    public AcceptanceChannel channel() { return channel; }
}
