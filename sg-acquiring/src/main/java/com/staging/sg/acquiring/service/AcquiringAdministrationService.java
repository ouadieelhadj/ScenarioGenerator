package com.staging.sg.acquiring.service;

import com.staging.sg.acquiring.domain.*;
import com.staging.sg.acquiring.repository.*;
import com.staging.sg.common.contract.PaymentContractStatus;
import com.staging.sg.common.contract.PaymentContractType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AcquiringAdministrationService {
    private final AcceptanceProductRepository products;
    private final MerchantRepository merchants;
    private final MerchantOutletRepository outlets;
    private final AcquiringContractRepository contracts;
    private final AcquiringContractDetailRepository contractDetails;
    private final AcquiringDeviceContractDetailRepository deviceDetails;
    private final TerminalDeviceRepository terminals;
    private final TerminalAssignmentRepository assignments;
    private final EcommerceStoreRepository stores;
    private final EcommerceAcceptanceProfileRepository ecommerceProfiles;
    private final AcquiringOutboxEventRepository outbox;

    public AcquiringAdministrationService(AcceptanceProductRepository products,
            MerchantRepository merchants, MerchantOutletRepository outlets,
            AcquiringContractRepository contracts,
            AcquiringContractDetailRepository contractDetails,
            AcquiringDeviceContractDetailRepository deviceDetails,
            TerminalDeviceRepository terminals,
            TerminalAssignmentRepository assignments,
            EcommerceStoreRepository stores,
            EcommerceAcceptanceProfileRepository ecommerceProfiles,
            AcquiringOutboxEventRepository outbox) {
        this.products = products;
        this.merchants = merchants;
        this.outlets = outlets;
        this.contracts = contracts;
        this.contractDetails = contractDetails;
        this.deviceDetails = deviceDetails;
        this.terminals = terminals;
        this.assignments = assignments;
        this.stores = stores;
        this.ecommerceProfiles = ecommerceProfiles;
        this.outbox = outbox;
    }

    @Transactional
    public AcceptanceProduct createProduct(String acquirerId, String productCode,
            int version, AcceptanceChannel channel, String currency, String caller,
            String correlationId) {
        AcceptanceProduct product = AcceptanceProduct.draft(acquirerId, productCode,
                version, channel, currency, caller);
        products.save(product);
        emit("AcceptanceProduct", product.id(), "AcceptanceProductCreated",
                correlationId, "{\"acquirerId\":\"" + safe(acquirerId) + "\"}");
        return product;
    }

    @Transactional
    public AcceptanceProduct submitProduct(UUID id) {
        AcceptanceProduct product = product(id);
        product.submit();
        return products.save(product);
    }

    @Transactional
    public AcceptanceProduct approveProduct(UUID id, String checker,
            String correlationId) {
        AcceptanceProduct product = product(id);
        product.approve(checker);
        products.save(product);
        emit("AcceptanceProduct", product.id(), "AcceptanceProductActivated",
                correlationId, "{\"productCode\":\"" + safe(product.productCode()) + "\"}");
        return product;
    }

    @Transactional
    public Merchant createMerchant(String acquirerId, String legalName,
            String tradingName, String registrationNumber, String country,
            String mcc, String caller, String idempotencyKey, String correlationId) {
        String fingerprint = AcquiringFingerprint.of(acquirerId, legalName, tradingName,
                registrationNumber, country, mcc);
        var existing = merchants.findByAcquirerIdAndCreatedByAndCreationIdempotencyKey(
                acquirerId, caller, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().creationMatches(fingerprint)) {
                throw new IllegalStateException(
                        "Idempotency key already used with another merchant payload");
            }
            return existing.get();
        }
        Merchant merchant = Merchant.draft(acquirerId, legalName, tradingName,
                registrationNumber, country, mcc, caller, idempotencyKey, fingerprint);
        merchants.save(merchant);
        emit("Merchant", merchant.id(), "MerchantCreated", correlationId,
                "{\"acquirerId\":\"" + safe(acquirerId) + "\"}");
        return merchant;
    }

    @Transactional
    public Merchant submitMerchant(UUID id) {
        Merchant merchant = merchant(id);
        merchant.submit();
        return merchants.save(merchant);
    }

    @Transactional
    public Merchant approveMerchant(UUID id, String checker, String correlationId) {
        Merchant merchant = merchant(id);
        if (merchant.approve(checker)) {
            merchants.save(merchant);
            emit("Merchant", merchant.id(), "MerchantApproved", correlationId,
                    "{\"status\":\"ACTIVE\"}");
        }
        return merchant;
    }

    @Transactional
    public MerchantOutlet createOutlet(UUID merchantId, String outletCode,
            String name, String addressLine, String country, String correlationId) {
        Merchant merchant = merchant(merchantId);
        require(merchant.isActive(), "An active merchant is required");
        MerchantOutlet outlet = MerchantOutlet.active(merchantId, outletCode, name,
                addressLine, country);
        outlets.save(outlet);
        emit("MerchantOutlet", outlet.id(), "MerchantOutletCreated", correlationId,
                "{\"merchantId\":\"" + merchantId + "\"}");
        return outlet;
    }

    @Transactional
    public AcquiringContract createMerchantContract(String acquirerId,
            String externalReference, UUID merchantId, String settlementAccountReference,
            UUID productId, String mid, String mcc, String settlementCurrency,
            AcceptanceChannel channel, String caller, String idempotencyKey,
            String correlationId) {
        Merchant merchant = ownedActiveMerchant(merchantId, acquirerId);
        AcceptanceProduct product = activeProduct(productId, acquirerId);
        require(product.channel() == channel || product.channel() == AcceptanceChannel.BOTH,
                "The product does not support the requested channel");
        String fingerprint = AcquiringFingerprint.of(acquirerId, externalReference,
                merchantId, settlementAccountReference, productId, mid, mcc,
                settlementCurrency, channel);
        var existing = contracts.findByInstitutionIdAndCreatedByAndCreationIdempotencyKey(
                acquirerId, caller, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().creationMatches(fingerprint)) {
                throw new IllegalStateException(
                        "Idempotency key already used with another contract payload");
            }
            return existing.get();
        }
        AcquiringContract contract = AcquiringContract.merchant(acquirerId,
                externalReference, merchant.id(), settlementAccountReference,
                productId, caller, idempotencyKey, fingerprint);
        contracts.save(contract);
        contractDetails.save(AcquiringContractDetail.of(contract.id(), acquirerId,
                merchantId, mid, mcc, settlementCurrency, channel));
        emit("PaymentContract", contract.id(), "AcquiringContractCreated",
                correlationId, contractPayload(contract));
        return contract;
    }

    @Transactional
    public AcquiringContract createDeviceContract(String acquirerId,
            String externalReference, UUID merchantId, UUID parentContractId,
            UUID productId, UUID outletId, String terminalId,
            AcceptanceChannel channel, boolean extendedSet, String macData,
            boolean macRequired, String caller, String idempotencyKey,
            String correlationId) {
        Merchant merchant = ownedActiveMerchant(merchantId, acquirerId);
        MerchantOutlet outlet = outlet(outletId);
        require(outlet.isActive() && outlet.merchantId().equals(merchantId),
                "An active outlet owned by the merchant is required");
        AcquiringContract parent = ownedContract(parentContractId, acquirerId);
        require(parent.contractType() == PaymentContractType.ACQUIRING_MERCHANT
                        && parent.status() == PaymentContractStatus.ACTIVE
                        && parent.merchantId().equals(merchantId),
                "An active merchant acquiring contract is required");
        AcceptanceProduct product = activeProduct(productId, acquirerId);
        require(product.channel().supportsTpe() && channel.supportsTpe(),
                "A TPE-enabled product and channel are required");
        String fingerprint = AcquiringFingerprint.of(acquirerId, externalReference,
                merchantId, parentContractId, productId, outletId, terminalId,
                channel, extendedSet, macData, macRequired);
        var existing = contracts.findByInstitutionIdAndCreatedByAndCreationIdempotencyKey(
                acquirerId, caller, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().creationMatches(fingerprint)) {
                throw new IllegalStateException(
                        "Idempotency key already used with another contract payload");
            }
            return existing.get();
        }
        AcquiringContract contract = AcquiringContract.device(acquirerId,
                externalReference, merchant.id(), parentContractId, productId,
                caller, idempotencyKey, fingerprint);
        contracts.save(contract);
        deviceDetails.save(AcquiringDeviceContractDetail.of(contract.id(), acquirerId,
                outletId, terminalId, channel, extendedSet, macData, macRequired));
        emit("PaymentContract", contract.id(), "DeviceContractCreated",
                correlationId, contractPayload(contract));
        return contract;
    }

    @Transactional
    public AcquiringContract createDeviceContractFromRequest(String acquirerId,
            String externalReference, UUID merchantId, UUID parentContractId,
            UUID productId, UUID outletId, String terminalId,
            AcceptanceChannel channel, String caller, String idempotencyKey,
            String correlationId, UUID sourceTerminalRequestId, int ordinal,
            String modelCode, String connectivityCode, String optionCodes) {
        Merchant merchant = ownedActiveMerchant(merchantId, acquirerId);
        MerchantOutlet outlet = outlet(outletId);
        require(outlet.isActive() && outlet.merchantId().equals(merchantId),
                "An active outlet owned by the merchant is required");
        AcquiringContract parent = ownedContract(parentContractId, acquirerId);
        require(parent.contractType() == PaymentContractType.ACQUIRING_MERCHANT
                        && parent.status() == PaymentContractStatus.ACTIVE,
                "An active merchant acquiring contract is required");
        AcceptanceProduct product = activeProduct(productId, acquirerId);
        require(product.channel().supportsTpe() && channel.supportsTpe(),
                "A TPE-enabled product and channel are required");
        String fingerprint = AcquiringFingerprint.of(acquirerId, externalReference, merchantId,
                parentContractId, productId, outletId, terminalId, channel,
                sourceTerminalRequestId, ordinal, modelCode, connectivityCode, optionCodes);
        var existing = contracts.findByInstitutionIdAndCreatedByAndCreationIdempotencyKey(
                acquirerId, caller, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().creationMatches(fingerprint))
                throw new IllegalStateException("Idempotency key already used with another TPE payload");
            return existing.get();
        }
        AcquiringContract contract = AcquiringContract.device(acquirerId, externalReference,
                merchant.id(), parentContractId, productId, caller, idempotencyKey, fingerprint);
        contracts.save(contract);
        deviceDetails.save(AcquiringDeviceContractDetail.ofRequest(contract.id(), acquirerId,
                outletId, terminalId, channel, true, "HEX", true, sourceTerminalRequestId,
                ordinal, modelCode, connectivityCode, optionCodes));
        emit("PaymentContract", contract.id(), "DeviceContractCreated", correlationId,
                contractPayload(contract));
        return contract;
    }

    @Transactional
    public AcquiringContract submitContract(UUID id, String acquirerId) {
        AcquiringContract contract = ownedContract(id, acquirerId);
        contract.submit();
        return contracts.save(contract);
    }

    @Transactional
    public AcquiringContract approveContract(UUID id, String acquirerId,
            String checker, String correlationId) {
        AcquiringContract contract = ownedContract(id, acquirerId);
        if (contract.contractType() == PaymentContractType.ACQUIRING_DEVICE
                || contract.contractType() == PaymentContractType.ACQUIRING_ECOMMERCE) {
            AcquiringContract parent = ownedContract(contract.parentContractId(), acquirerId);
            require(parent.status() == PaymentContractStatus.ACTIVE,
                    "The parent contract must remain active");
        }
        if (contract.approve(checker)) {
            contracts.save(contract);
            emit("PaymentContract", contract.id(), "AcquiringContractActivated",
                    correlationId, contractPayload(contract));
        }
        return contract;
    }

    @Transactional
    public TerminalDevice registerTerminal(String acquirerId, String serialNumber,
            String modelCode, String correlationId) {
        TerminalDevice terminal = TerminalDevice.inStock(acquirerId, serialNumber, modelCode);
        terminals.save(terminal);
        emit("TerminalDevice", terminal.id(), "TerminalRegistered", correlationId,
                "{\"acquirerId\":\"" + safe(acquirerId) + "\"}");
        return terminal;
    }

    @Transactional
    public TerminalAssignment assignTerminal(UUID terminalDeviceId,
            UUID deviceContractId, String acquirerId, String correlationId) {
        TerminalDevice terminal = terminal(terminalDeviceId);
        require(terminal.acquirerId().equals(acquirerId), "Unknown terminal");
        require(assignments.findByTerminalDeviceIdAndActiveTrue(terminalDeviceId).isEmpty(),
                "The terminal already has an active assignment");
        AcquiringContract contract = ownedContract(deviceContractId, acquirerId);
        require(contract.contractType() == PaymentContractType.ACQUIRING_DEVICE
                        && contract.status() == PaymentContractStatus.ACTIVE,
                "An active device contract is required");
        AcquiringDeviceContractDetail detail = deviceDetails.findById(deviceContractId)
                .orElseThrow(() -> new IllegalStateException("Missing device contract detail"));
        terminal.assign();
        terminals.save(terminal);
        TerminalAssignment assignment = TerminalAssignment.active(terminalDeviceId,
                detail.outletId(), deviceContractId);
        assignments.save(assignment);
        emit("TerminalDevice", terminal.id(), "TerminalAssigned", correlationId,
                "{\"contractId\":\"" + deviceContractId + "\"}");
        return assignment;
    }

    @Transactional
    public EcommerceStore createStore(UUID merchantId, String storeCode,
            String name, String allowedDomain, String returnUrl,
            String notificationUrl, String correlationId) {
        MerchantOutlet principal = outlets.findFirstByMerchantIdAndPrincipalTrueAndActiveTrue(merchantId)
                .orElseThrow(() -> new IllegalStateException(
                        "ECOM-001: an active principal outlet is required"));
        return createStore(merchantId, principal.id(), storeCode, name, allowedDomain,
                returnUrl, notificationUrl, correlationId);
    }

    @Transactional
    public EcommerceStore createStore(UUID merchantId, UUID outletId, String storeCode,
            String name, String allowedDomain, String returnUrl,
            String notificationUrl, String correlationId) {
        require(merchant(merchantId).isActive(), "An active merchant is required");
        MerchantOutlet outlet = outlet(outletId);
        require(outlet.isActive() && outlet.merchantId().equals(merchantId),
                "ECOM-001: an active outlet owned by the merchant is required");
        EcommerceStore store = EcommerceStore.draft(merchantId, outletId, storeCode, name,
                allowedDomain, returnUrl, notificationUrl);
        stores.save(store);
        emit("EcommerceStore", store.id(), "EcommerceStoreCreated", correlationId,
                "{\"merchantId\":\"" + merchantId + "\"}");
        return store;
    }

    @Transactional
    public EcommerceStore readyStore(UUID id) {
        EcommerceStore store = store(id);
        store.ready();
        return stores.save(store);
    }

    @Transactional
    public EcommerceStore activateStore(UUID id, String correlationId) {
        EcommerceStore store = store(id);
        store.activate();
        stores.save(store);
        emit("EcommerceStore", id, "EcommerceStoreActivated", correlationId,
                "{\"status\":\"ACTIVE\"}");
        return store;
    }

    @Transactional
    public EcommerceAcceptanceProfile createEcommerceProfile(String acquirerId,
            UUID storeId, UUID contractId, String logicalTerminalId,
            String currency, String captureMode, String correlationId) {
        EcommerceStore store = store(storeId);
        require(store.status() == EcommerceStatus.ACTIVE,
                "An active ecommerce store is required");
        AcquiringContract contract = ownedContract(contractId, acquirerId);
        require(contract.contractType() == PaymentContractType.ACQUIRING_ECOMMERCE
                        && contract.status() == PaymentContractStatus.ACTIVE,
                "An active ecommerce contract is required");
        EcommerceAcceptanceProfile profile = EcommerceAcceptanceProfile.active(
                acquirerId, storeId, contractId, logicalTerminalId, currency, captureMode);
        ecommerceProfiles.save(profile);
        emit("EcommerceAcceptanceProfile", profile.id(), "EcommerceProfileActivated",
                correlationId, "{\"contractId\":\"" + contractId + "\"}");
        return profile;
    }

    Merchant merchant(UUID id) {
        return merchants.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown merchant"));
    }

    MerchantOutlet outlet(UUID id) {
        return outlets.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown outlet"));
    }

    AcquiringContract ownedContract(UUID id, String acquirerId) {
        AcquiringContract contract = contracts.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown acquiring contract"));
        require(contract.institutionId().equals(acquirerId), "Unknown acquiring contract");
        return contract;
    }

    TerminalDevice terminal(UUID id) {
        return terminals.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown terminal"));
    }

    private Merchant ownedActiveMerchant(UUID id, String acquirerId) {
        Merchant merchant = merchant(id);
        require(merchant.acquirerId().equals(acquirerId) && merchant.isActive(),
                "An active merchant owned by the acquirer is required");
        return merchant;
    }

    private AcceptanceProduct activeProduct(UUID id, String acquirerId) {
        AcceptanceProduct product = product(id);
        require(product.acquirerId().equals(acquirerId) && product.isActive(),
                "An active acceptance product owned by the acquirer is required");
        return product;
    }

    private AcceptanceProduct product(UUID id) {
        return products.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown acceptance product"));
    }

    private EcommerceStore store(UUID id) {
        return stores.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown ecommerce store"));
    }

    private void emit(String aggregateType, UUID aggregateId, String eventType,
            String correlationId, String payload) {
        outbox.save(AcquiringOutboxEvent.pending(aggregateType, aggregateId,
                eventType, correlationId, payload));
    }

    private static String contractPayload(AcquiringContract contract) {
        return "{\"contractType\":\"" + contract.contractType()
                + "\",\"status\":\"" + contract.status() + "\"}";
    }

    private static String safe(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
