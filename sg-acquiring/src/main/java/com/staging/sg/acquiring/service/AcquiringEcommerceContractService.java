package com.staging.sg.acquiring.service;

import com.staging.sg.acquiring.domain.*;
import com.staging.sg.acquiring.repository.*;
import com.staging.sg.common.contract.PaymentContractStatus;
import com.staging.sg.common.contract.PaymentContractType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AcquiringEcommerceContractService {
    private final MerchantRepository merchants;
    private final MerchantOutletRepository outlets;
    private final EcommerceStoreRepository stores;
    private final AcceptanceProductRepository products;
    private final AcquiringContractRepository contracts;
    private final AcquiringEcommerceContractDetailRepository details;

    public AcquiringEcommerceContractService(MerchantRepository merchants,
            MerchantOutletRepository outlets, EcommerceStoreRepository stores,
            AcceptanceProductRepository products, AcquiringContractRepository contracts,
            AcquiringEcommerceContractDetailRepository details) {
        this.merchants = merchants; this.outlets = outlets; this.stores = stores;
        this.products = products; this.contracts = contracts; this.details = details;
    }

    @Transactional
    public AcquiringContract create(String acquirerId, String externalReference, UUID merchantId,
            UUID parentContractId, UUID productId, UUID outletId, UUID storeId,
            UUID sourceStoreRequestId, String logicalTerminalId, AcceptanceChannel channel,
            String actor, String idempotencyKey) {
        Merchant merchant = merchants.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown merchant"));
        MerchantOutlet outlet = outlets.findById(outletId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown outlet"));
        EcommerceStore store = stores.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown ecommerce store"));
        AcceptanceProduct product = products.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown acceptance product"));
        AcquiringContract parent = contracts.findById(parentContractId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown parent contract"));
        if (!merchant.isActive() || !merchant.acquirerId().equals(acquirerId)
                || !outlet.isActive() || !outlet.merchantId().equals(merchantId)
                || !store.merchantId().equals(merchantId) || !store.outletId().equals(outletId)
                || !product.isActive() || !product.acquirerId().equals(acquirerId)
                || !product.channel().supportsEcommerce()
                || parent.contractType() != PaymentContractType.ACQUIRING_MERCHANT
                || parent.status() != PaymentContractStatus.ACTIVE)
            throw new IllegalStateException("ECOM-004: ecommerce contract prerequisites are not met");
        String fingerprint = AcquiringFingerprint.of(acquirerId, externalReference, merchantId,
                parentContractId, productId, outletId, storeId, sourceStoreRequestId,
                logicalTerminalId, channel);
        var existing = contracts.findByInstitutionIdAndCreatedByAndCreationIdempotencyKey(
                acquirerId, actor, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().creationMatches(fingerprint))
                throw new IllegalStateException("Idempotency key already used with another ecommerce payload");
            return existing.get();
        }
        AcquiringContract contract = AcquiringContract.ecommerce(acquirerId, externalReference,
                merchantId, parentContractId, productId, actor, idempotencyKey, fingerprint);
        contracts.save(contract);
        details.save(AcquiringEcommerceContractDetail.of(contract.id(), acquirerId, outletId,
                storeId, logicalTerminalId, channel, sourceStoreRequestId));
        return contract;
    }
}
