package com.staging.sg.acquiring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.acquiring.api.MerchantProvisioningRequestV2;
import com.staging.sg.acquiring.api.MerchantProvisioningResultV2;
import com.staging.sg.acquiring.domain.*;
import com.staging.sg.acquiring.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class MerchantProvisioningV2Service {
    private static final String SCHEMA_VERSION = "2.0";

    private final AcquiringAdministrationService administration;
    private final AcquiringEcommerceContractService ecommerceContracts;
    private final AcquiringIdentifierAllocator identifiers;
    private final MerchantRepository merchants;
    private final MerchantOutletRepository outlets;
    private final MerchantLegalProfileRepository legalProfiles;
    private final MerchantRepresentativeRepository representatives;
    private final MerchantBeneficialOwnerRepository beneficialOwners;
    private final AcceptanceProductRepository products;
    private final AcquiringProductBindingRepository bindings;
    private final MerchantOutletProductRepository outletProducts;
    private final AcquiringContractRepository contracts;
    private final EcommerceStoreRepository stores;
    private final EcommerceAcceptanceProfileRepository ecommerceProfiles;
    private final ProvisioningObjectStateRepository states;
    private final ObjectMapper objectMapper;

    public MerchantProvisioningV2Service(AcquiringAdministrationService administration,
            AcquiringEcommerceContractService ecommerceContracts,
            AcquiringIdentifierAllocator identifiers, MerchantRepository merchants,
            MerchantOutletRepository outlets, MerchantLegalProfileRepository legalProfiles,
            MerchantRepresentativeRepository representatives,
            MerchantBeneficialOwnerRepository beneficialOwners,
            AcceptanceProductRepository products, AcquiringProductBindingRepository bindings,
            MerchantOutletProductRepository outletProducts, AcquiringContractRepository contracts,
            EcommerceStoreRepository stores,
            EcommerceAcceptanceProfileRepository ecommerceProfiles,
            ProvisioningObjectStateRepository states, ObjectMapper objectMapper) {
        this.administration = administration; this.ecommerceContracts = ecommerceContracts;
        this.identifiers = identifiers; this.merchants = merchants; this.outlets = outlets;
        this.legalProfiles = legalProfiles; this.representatives = representatives;
        this.beneficialOwners = beneficialOwners; this.products = products;
        this.bindings = bindings; this.outletProducts = outletProducts; this.contracts = contracts;
        this.stores = stores; this.ecommerceProfiles = ecommerceProfiles; this.states = states;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MerchantProvisioningResultV2 provision(MerchantProvisioningRequestV2 request,
            String idempotencyKey, String correlationId) {
        validate(request, idempotencyKey, correlationId);
        List<MerchantProvisioningResultV2.ObjectResult> results = new ArrayList<>();
        Merchant merchant;
        AcquiringContract merchantContract;
        ProvisioningObjectState merchantState;
        ProvisioningObjectState contractState;
        try {
            merchantState = begin("MERCHANT", request.onboardingCaseId(),
                    key(idempotencyKey, "merchant"), request.merchant());
            merchant = provisionMerchant(request, merchantState, correlationId);
            results.add(result("MERCHANT", request.onboardingCaseId(), merchantState));

            UUID contractObjectId = stable(request.onboardingCaseId() + ":merchant-contract");
            contractState = begin("MERCHANT_CONTRACT", contractObjectId,
                    key(idempotencyKey, "merchant-contract"), request.settlement());
            merchantContract = provisionMerchantContract(request, merchant, contractState, correlationId);
            results.add(result("MERCHANT_CONTRACT", contractObjectId, contractState));
        } catch (RuntimeException exception) {
            return new MerchantProvisioningResultV2(SCHEMA_VERSION, null, null,
                    "PROVISIONING_FAILED", List.copyOf(results));
        }

        for (MerchantProvisioningRequestV2.Outlet input : request.outlets()) {
            MerchantOutlet outlet = provisionOutletBranch(request, merchant, merchantContract,
                    input, idempotencyKey, correlationId, results);
            if (outlet == null) continue;
            for (MerchantProvisioningRequestV2.OutletProduct product : safe(input.products()))
                provisionOutletProduct(outlet, product, idempotencyKey, results);
            for (MerchantProvisioningRequestV2.TerminalRequest terminal : safe(input.terminalRequests()))
                provisionTerminalRequest(request, merchant, merchantContract, outlet, terminal,
                        idempotencyKey, correlationId, results);
            for (MerchantProvisioningRequestV2.EcommerceStore store : safe(input.ecommerceStores()))
                provisionEcommerceStore(request, merchant, merchantContract, outlet, store,
                        idempotencyKey, correlationId, results);
        }

        String aggregate = aggregate(results);
        MerchantProvisioningResultV2 response = new MerchantProvisioningResultV2(SCHEMA_VERSION, merchant.id(),
                contractState.allocatedIdentifier(), aggregate, List.copyOf(results));
        return response;
    }

    private Merchant provisionMerchant(MerchantProvisioningRequestV2 request,
            ProvisioningObjectState state, String correlationId) {
        if (state.status() == ProvisioningObjectStatus.PROVISIONED)
            return merchants.findById(UUID.fromString(state.externalReference()))
                    .orElseThrow(() -> new IllegalStateException("PROV-002: merchant state is orphaned"));
        try {
            state.start(); states.save(state);
            MerchantProvisioningRequestV2.LegalMerchant legal = request.merchant();
            Merchant merchant = administration.createMerchant(request.acquirerId(), legal.legalName(),
                    legal.tradingName(), legal.registrationNumber(), legal.headquartersAddress().country(),
                    legal.mcc(), request.maker(), state.idempotencyKey(), correlationId);
            merchant.enrichLegalType(legal.merchantType(), legal.organizationLegalNature());
            merchants.save(merchant);
            administration.submitMerchant(merchant.id());
            administration.approveMerchant(merchant.id(), request.checker(), correlationId);
            persistLegalProfile(merchant.id(), legal);
            state.provisioned(merchant.id().toString()); states.save(state);
            return merchant;
        } catch (RuntimeException exception) {
            fail(state, exception); throw exception;
        }
    }

    private void persistLegalProfile(UUID merchantId, MerchantProvisioningRequestV2.LegalMerchant legal) {
        MerchantProvisioningRequestV2.Address address = legal.headquartersAddress();
        if (!legalProfiles.existsById(merchantId)) legalProfiles.save(MerchantLegalProfile.create(
                merchantId, legal.taxIdentifier(), legal.ice(), legal.legalForm(),
                legal.businessActivity(), legal.associationPurpose(), legal.primaryPhone(),
                legal.primaryEmail(), legal.rib(), address.line1(), address.line2(),
                address.district(), address.city(), address.region(), address.postalCode(), address.country()));
        MerchantProvisioningRequestV2.Representative representative = legal.representative();
        if (representatives.findByMerchantIdAndActiveTrue(merchantId).isEmpty())
            representatives.save(MerchantRepresentative.active(merchantId, representative.title(),
                    representative.firstName(), representative.lastName(), representative.birthDate(),
                    representative.phone(), representative.email(), representative.idType(),
                    representative.idNumber(), representative.residenceCountry(), representative.nationality()));
        if (beneficialOwners.findByMerchantIdAndActiveTrue(merchantId).isEmpty()) {
            for (MerchantProvisioningRequestV2.BeneficialOwner owner : safe(legal.beneficialOwners()))
                beneficialOwners.save(MerchantBeneficialOwner.active(merchantId,
                        owner.firstName(), owner.lastName()));
        }
    }

    private AcquiringContract provisionMerchantContract(MerchantProvisioningRequestV2 request,
            Merchant merchant, ProvisioningObjectState state, String correlationId) {
        if (state.status() == ProvisioningObjectStatus.PROVISIONED)
            return contracts.findById(UUID.fromString(state.externalReference()))
                    .orElseThrow(() -> new IllegalStateException("PROV-002: contract state is orphaned"));
        try {
            state.start();
            if (state.allocatedIdentifier() == null) state.allocate(identifiers.nextMid());
            states.save(state);
            AcceptanceChannel channel = AcceptanceChannel.valueOf(request.acceptanceChannel());
            UUID productId = resolveBinding(request.acquirerId(), ProductBindingUsage.MERCHANT_CONTRACT,
                    channel, request.settlement().currency());
            AcquiringContract contract = administration.createMerchantContract(request.acquirerId(),
                    request.onboardingReference() + ":MERCHANT", merchant.id(),
                    request.settlement().accountReference(), productId,
                    state.allocatedIdentifier(), request.merchant().mcc(),
                    request.settlement().currency(), channel, request.maker(),
                    state.idempotencyKey(), correlationId);
            administration.submitContract(contract.id(), request.acquirerId());
            administration.approveContract(contract.id(), request.acquirerId(),
                    request.checker(), correlationId);
            state.provisioned(contract.id().toString()); states.save(state);
            return contract;
        } catch (RuntimeException exception) {
            fail(state, exception); throw exception;
        }
    }

    private MerchantOutlet provisionOutletBranch(MerchantProvisioningRequestV2 request,
            Merchant merchant, AcquiringContract merchantContract,
            MerchantProvisioningRequestV2.Outlet input, String baseKey, String correlationId,
            List<MerchantProvisioningResultV2.ObjectResult> results) {
        ProvisioningObjectState state = begin("OUTLET", input.sourceOutletId(),
                key(baseKey, "outlet:" + input.sourceOutletId()), input);
        try {
            MerchantOutlet outlet;
            if (state.status() == ProvisioningObjectStatus.PROVISIONED) {
                outlet = outlets.findById(UUID.fromString(state.externalReference()))
                        .orElseThrow(() -> new IllegalStateException("PROV-002: outlet state is orphaned"));
            } else {
                state.start(); states.save(state);
                outlet = outlets.findByMerchantIdAndOutletCode(merchant.id(), input.code()).orElse(null);
                if (outlet == null) outlet = administration.createOutlet(merchant.id(), input.code(),
                        input.name(), input.address().line1(), input.address().country(), correlationId);
                MerchantProvisioningRequestV2.Representative responsible = input.responsible();
                MerchantProvisioningRequestV2.Address address = input.address();
                outlet.enrich(input.principal(), address.line1(), address.line2(), address.district(),
                        address.city(), address.region(), address.postalCode(), address.country(),
                        input.contactPhone(), input.contactEmail(), responsible.title(),
                        responsible.firstName(), responsible.lastName(), responsible.birthDate(),
                        responsible.phone(), responsible.email(), responsible.idType(),
                        responsible.idNumber(), responsible.residenceCountry(), responsible.nationality());
                outlets.save(outlet);
                state.provisioned(outlet.id().toString()); states.save(state);
            }
            results.add(result("OUTLET", input.sourceOutletId(), state));
            return outlet;
        } catch (RuntimeException exception) {
            fail(state, exception); results.add(result("OUTLET", input.sourceOutletId(), state));
            return null;
        }
    }

    private void provisionOutletProduct(MerchantOutlet outlet,
            MerchantProvisioningRequestV2.OutletProduct input, String baseKey,
            List<MerchantProvisioningResultV2.ObjectResult> results) {
        UUID objectId = stable(outlet.id() + ":" + input.productId());
        ProvisioningObjectState state = begin("OUTLET_PRODUCT", objectId,
                key(baseKey, "outlet-product:" + objectId), input);
        try {
            if (state.status() != ProvisioningObjectStatus.PROVISIONED) {
                state.start(); states.save(state);
                AcceptanceProduct product = products.findById(input.productId())
                        .orElseThrow(() -> new IllegalArgumentException("PDV-005: unknown product"));
                if (!product.isActive()) throw new IllegalStateException("PDV-005: inactive product");
                MerchantOutletProduct link = outletProducts
                        .findByOutletIdAndProductIdAndActiveTrue(outlet.id(), input.productId())
                        .orElseGet(() -> outletProducts.save(MerchantOutletProduct.active(outlet.id(),
                                input.productId(), objectId.toString())));
                state.provisioned(link.id().toString()); states.save(state);
            }
        } catch (RuntimeException exception) { fail(state, exception); }
        results.add(result("OUTLET_PRODUCT", objectId, state));
    }

    private void provisionTerminalRequest(MerchantProvisioningRequestV2 request, Merchant merchant,
            AcquiringContract merchantContract, MerchantOutlet outlet,
            MerchantProvisioningRequestV2.TerminalRequest input, String baseKey,
            String correlationId, List<MerchantProvisioningResultV2.ObjectResult> results) {
        for (int ordinal = 1; ordinal <= input.quantity(); ordinal++) {
            UUID objectId = stable(input.sourceRequestId() + ":" + ordinal);
            ProvisioningObjectState state = begin("TPE_DEVICE_CONTRACT", objectId,
                    key(baseKey, "terminal:" + objectId), Map.of("request", input, "ordinal", ordinal));
            try {
                if (state.status() != ProvisioningObjectStatus.PROVISIONED) {
                    state.start();
                    if (state.allocatedIdentifier() == null) state.allocate(identifiers.nextTid());
                    states.save(state);
                    AcquiringContract contract = administration.createDeviceContractFromRequest(
                            request.acquirerId(), request.onboardingReference() + ":TPE:"
                                    + input.sourceRequestId() + ":" + ordinal,
                            merchant.id(), merchantContract.id(), input.productId(), outlet.id(),
                            state.allocatedIdentifier(), AcceptanceChannel.TPE, request.maker(),
                            state.idempotencyKey(), correlationId, input.sourceRequestId(), ordinal,
                            input.modelCode(), input.connectivityCode(),
                            String.join(",", safe(input.optionCodes())));
                    administration.submitContract(contract.id(), request.acquirerId());
                    administration.approveContract(contract.id(), request.acquirerId(),
                            request.checker(), correlationId);
                    state.provisioned(contract.id().toString()); states.save(state);
                }
            } catch (RuntimeException exception) { fail(state, exception); }
            results.add(result("TPE_DEVICE_CONTRACT", objectId, state));
        }
    }

    private void provisionEcommerceStore(MerchantProvisioningRequestV2 request, Merchant merchant,
            AcquiringContract merchantContract, MerchantOutlet outlet,
            MerchantProvisioningRequestV2.EcommerceStore input, String baseKey,
            String correlationId, List<MerchantProvisioningResultV2.ObjectResult> results) {
        ProvisioningObjectState storeState = begin("ECOMMERCE_STORE", input.sourceRequestId(),
                key(baseKey, "store:" + input.sourceRequestId()), input);
        EcommerceStore store;
        try {
            if (storeState.status() == ProvisioningObjectStatus.PROVISIONED) {
                store = stores.findById(UUID.fromString(storeState.externalReference()))
                        .orElseThrow(() -> new IllegalStateException("PROV-002: store state is orphaned"));
            } else {
                storeState.start(); states.save(storeState);
                store = stores.findByMerchantIdAndStoreCode(merchant.id(), input.storeCode()).orElse(null);
                if (store == null) store = administration.createStore(merchant.id(), outlet.id(),
                        input.storeCode(), input.name(), input.allowedDomain(), input.returnUrl(),
                        input.notificationUrl(), correlationId);
                if (store.status() == EcommerceStatus.DRAFT) administration.readyStore(store.id());
                if (store.status() != EcommerceStatus.ACTIVE) administration.activateStore(store.id(), correlationId);
                storeState.provisioned(store.id().toString()); states.save(storeState);
            }
        } catch (RuntimeException exception) {
            fail(storeState, exception); results.add(result("ECOMMERCE_STORE", input.sourceRequestId(), storeState));
            return;
        }
        results.add(result("ECOMMERCE_STORE", input.sourceRequestId(), storeState));

        UUID contractObjectId = stable(input.sourceRequestId() + ":ecommerce-contract");
        ProvisioningObjectState contractState = begin("ECOMMERCE_CONTRACT", contractObjectId,
                key(baseKey, "ecommerce-contract:" + input.sourceRequestId()), input);
        AcquiringContract ecommerceContract;
        try {
            if (contractState.status() == ProvisioningObjectStatus.PROVISIONED) {
                ecommerceContract = contracts.findById(UUID.fromString(contractState.externalReference()))
                        .orElseThrow(() -> new IllegalStateException("PROV-002: ecommerce contract state is orphaned"));
            } else {
                contractState.start();
                if (contractState.allocatedIdentifier() == null) contractState.allocate(identifiers.nextTid());
                states.save(contractState);
                ecommerceContract = ecommerceContracts.create(request.acquirerId(),
                        request.onboardingReference() + ":ECOM:" + input.sourceRequestId(),
                        merchant.id(), merchantContract.id(), input.productId(), outlet.id(), store.id(),
                        input.sourceRequestId(), contractState.allocatedIdentifier(), AcceptanceChannel.ECOMMERCE,
                        request.maker(), contractState.idempotencyKey());
                administration.submitContract(ecommerceContract.id(), request.acquirerId());
                administration.approveContract(ecommerceContract.id(), request.acquirerId(),
                        request.checker(), correlationId);
                contractState.provisioned(ecommerceContract.id().toString()); states.save(contractState);
            }
        } catch (RuntimeException exception) {
            fail(contractState, exception); results.add(result("ECOMMERCE_CONTRACT", contractObjectId, contractState));
            return;
        }
        results.add(result("ECOMMERCE_CONTRACT", contractObjectId, contractState));

        UUID profileObjectId = stable(input.sourceRequestId() + ":ecommerce-profile");
        ProvisioningObjectState profileState = begin("ECOMMERCE_PROFILE", profileObjectId,
                key(baseKey, "ecommerce-profile:" + input.sourceRequestId()), input);
        try {
            if (profileState.status() != ProvisioningObjectStatus.PROVISIONED) {
                profileState.start(); states.save(profileState);
                EcommerceAcceptanceProfile profile = ecommerceProfiles
                        .findByStoreIdAndContractId(store.id(), ecommerceContract.id()).orElse(null);
                if (profile == null) profile = administration.createEcommerceProfile(request.acquirerId(),
                        store.id(), ecommerceContract.id(), contractState.allocatedIdentifier(),
                        input.currency(), input.captureMode(), correlationId);
                profileState.provisioned(profile.id().toString()); states.save(profileState);
            }
        } catch (RuntimeException exception) { fail(profileState, exception); }
        results.add(result("ECOMMERCE_PROFILE", profileObjectId, profileState));
    }

    private ProvisioningObjectState begin(String type, UUID objectId, String key, Object payload) {
        String hash = fingerprint(payload);
        ProvisioningObjectState state = states.findByIdempotencyKey(key).orElse(null);
        if (state == null) return states.save(ProvisioningObjectState.pending(type, objectId, key, hash));
        state.requirePayload(hash);
        return state;
    }

    private UUID resolveBinding(String acquirerId, ProductBindingUsage usage,
            AcceptanceChannel channel, String currency) {
        List<AcquiringProductBinding> resolved = bindings.resolve(acquirerId, usage, channel,
                currency, Instant.now());
        if (resolved.size() != 1)
            throw new IllegalStateException("PDV-005: exactly one active " + usage + " binding is required");
        return resolved.get(0).productId();
    }

    private void fail(ProvisioningObjectState state, RuntimeException exception) {
        boolean retryable = !(exception instanceof IllegalArgumentException)
                && !(exception instanceof IllegalStateException);
        String code = retryable ? "TECHNICAL_RETRYABLE" : "FUNCTIONAL_FINAL";
        state.failed(code, exception.getMessage(), retryable,
                retryable ? Instant.now().plusSeconds(backoffSeconds(state)) : null);
        states.save(state);
    }

    static long backoffSeconds(ProvisioningObjectState state) {
        long base = Math.min(1800L, 30L << Math.min(6, Math.max(0, state.attemptCount() - 1)));
        double jitter = ((Math.floorMod(state.idempotencyKey().hashCode(), 401) - 200) / 1000.0);
        return Math.max(1L, Math.round(base * (1.0 + jitter)));
    }

    private MerchantProvisioningResultV2.ObjectResult result(String type, UUID sourceId,
            ProvisioningObjectState state) {
        return new MerchantProvisioningResultV2.ObjectResult(type, sourceId, state.status(),
                state.externalReference(), state.allocatedIdentifier(), state.lastErrorCode(),
                state.lastErrorMessage());
    }

    private static String aggregate(List<MerchantProvisioningResultV2.ObjectResult> results) {
        long success = results.stream().filter(value -> value.status() == ProvisioningObjectStatus.PROVISIONED).count();
        long failed = results.stream().filter(value -> value.status() == ProvisioningObjectStatus.FAILED_FINAL
                || value.status() == ProvisioningObjectStatus.FAILED_RETRYABLE).count();
        if (failed == 0 && success == results.size()) return "PROVISIONED";
        if (success > 0 && failed > 0) return "PARTIALLY_PROVISIONED";
        if (success == 0 && failed > 0) return "PROVISIONING_FAILED";
        return "PROVISIONING";
    }

    private void validate(MerchantProvisioningRequestV2 request, String key, String correlationId) {
        if (request == null || !SCHEMA_VERSION.equals(request.schemaVersion()))
            throw new IllegalArgumentException("Unsupported schemaVersion; expected 2.0");
        if (request.onboardingCaseId() == null || blank(request.onboardingReference())
                || blank(request.acquirerId()) || request.merchant() == null
                || request.settlement() == null || blank(request.acceptanceChannel())
                || request.outlets() == null || request.outlets().isEmpty()
                || blank(request.maker()) || blank(request.checker())
                || request.maker().equals(request.checker()) || blank(key) || key.length() > 128
                || blank(correlationId))
            throw new IllegalArgumentException("Invalid merchant provisioning v2 request");
        long principals = request.outlets().stream().filter(MerchantProvisioningRequestV2.Outlet::principal).count();
        if (principals != 1) throw new IllegalArgumentException("PDV-002: exactly one principal outlet is required");
        if (request.outlets().stream().anyMatch(value -> value.sourceOutletId() == null))
            throw new IllegalArgumentException("PDV-003: sourceOutletId is required");
        AcceptanceChannel.valueOf(request.acceptanceChannel());
    }

    private String fingerprint(Object value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot fingerprint provisioning payload", exception);
        }
    }
    private static UUID stable(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String key(String base, String suffix) { return base + ":" + suffix; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
}
