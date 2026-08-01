package com.staging.sg.acquiring.api;

import com.staging.sg.acquiring.domain.*;
import com.staging.sg.acquiring.service.AcquiringAdministrationService;
import com.staging.sg.acquiring.service.TerminalProvisioningService;
import com.staging.sg.common.contract.PaymentContractStatus;
import com.staging.sg.common.contract.PaymentContractType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/acquiring/v1")
public class AcquiringAdminController {
    private final AcquiringAdministrationService administration;
    private final TerminalProvisioningService provisioning;

    public AcquiringAdminController(AcquiringAdministrationService administration,
            TerminalProvisioningService provisioning) {
        this.administration = administration;
        this.provisioning = provisioning;
    }

    @PostMapping("/products")
    public ResponseEntity<ProductView> createProduct(@RequestBody ProductRequest request,
            @RequestHeader("X-Caller-ID") String caller,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        AcceptanceProduct value = administration.createProduct(request.acquirerId(),
                request.productCode(), request.productVersion(), request.channel(),
                request.currency(), caller, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductView.from(value));
    }

    @PostMapping("/products/{id}/submit")
    public ProductView submitProduct(@PathVariable UUID id) {
        return ProductView.from(administration.submitProduct(id));
    }

    @PostMapping("/products/{id}/approve")
    public ProductView approveProduct(@PathVariable UUID id,
            @RequestHeader("X-Caller-ID") String checker,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return ProductView.from(administration.approveProduct(id, checker, correlationId));
    }

    @PostMapping("/merchants")
    public ResponseEntity<MerchantView> createMerchant(@RequestBody MerchantRequest request,
            @RequestHeader("X-Caller-ID") String caller,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        Merchant value = administration.createMerchant(request.acquirerId(),
                request.legalName(), request.tradingName(), request.registrationNumber(),
                request.country(), request.mcc(), caller, idempotencyKey, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(MerchantView.from(value));
    }

    @PostMapping("/merchants/{id}/submit")
    public MerchantView submitMerchant(@PathVariable UUID id) {
        return MerchantView.from(administration.submitMerchant(id));
    }

    @PostMapping("/merchants/{id}/approve")
    public MerchantView approveMerchant(@PathVariable UUID id,
            @RequestHeader("X-Caller-ID") String checker,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return MerchantView.from(administration.approveMerchant(id, checker, correlationId));
    }

    @PostMapping("/merchants/{merchantId}/outlets")
    public ResponseEntity<OutletView> createOutlet(@PathVariable UUID merchantId,
            @RequestBody OutletRequest request,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        MerchantOutlet value = administration.createOutlet(merchantId, request.outletCode(),
                request.name(), request.addressLine(), request.country(), correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(OutletView.from(value));
    }

    @PostMapping("/contracts/merchant")
    public ResponseEntity<ContractView> createMerchantContract(
            @RequestBody MerchantContractRequest request,
            @RequestHeader("X-Caller-ID") String caller,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        AcquiringContract value = administration.createMerchantContract(
                request.acquirerId(), request.externalReference(), request.merchantId(),
                request.settlementAccountReference(), request.productId(), request.mid(),
                request.mcc(), request.settlementCurrency(), request.channel(), caller,
                idempotencyKey, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ContractView.from(value));
    }

    @PostMapping("/contracts/device")
    public ResponseEntity<ContractView> createDeviceContract(
            @RequestBody DeviceContractRequest request,
            @RequestHeader("X-Caller-ID") String caller,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        AcquiringContract value = administration.createDeviceContract(
                request.acquirerId(), request.externalReference(), request.merchantId(),
                request.parentContractId(), request.productId(), request.outletId(),
                request.terminalId(), request.channel(), request.extendedSet(),
                request.macData(), request.macRequired(), caller, idempotencyKey,
                correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ContractView.from(value));
    }

    @PostMapping("/contracts/{id}/submit")
    public ContractView submitContract(@PathVariable UUID id,
            @RequestParam String acquirerId) {
        return ContractView.from(administration.submitContract(id, acquirerId));
    }

    @PostMapping("/contracts/{id}/approve")
    public ContractView approveContract(@PathVariable UUID id,
            @RequestParam String acquirerId,
            @RequestHeader("X-Caller-ID") String checker,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return ContractView.from(administration.approveContract(
                id, acquirerId, checker, correlationId));
    }

    @PostMapping("/terminals")
    public ResponseEntity<TerminalView> registerTerminal(@RequestBody TerminalRequest request,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        TerminalDevice value = administration.registerTerminal(request.acquirerId(),
                request.serialNumber(), request.modelCode(), correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(TerminalView.from(value));
    }

    @PostMapping("/terminals/{terminalId}/assignments")
    public ResponseEntity<AssignmentView> assignTerminal(@PathVariable UUID terminalId,
            @RequestBody AssignmentRequest request,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        TerminalAssignment value = administration.assignTerminal(terminalId,
                request.deviceContractId(), request.acquirerId(), correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(AssignmentView.from(value));
    }

    @PostMapping("/terminals/{terminalId}/provision")
    public TerminalView provisionTerminal(@PathVariable UUID terminalId,
            @RequestParam String acquirerId,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return TerminalView.from(provisioning.provision(terminalId, acquirerId, correlationId));
    }

    @PostMapping("/terminals/{terminalId}/activate")
    public TerminalView activateTerminal(@PathVariable UUID terminalId,
            @RequestParam String acquirerId,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return TerminalView.from(provisioning.activate(terminalId, acquirerId, correlationId));
    }

    @PostMapping("/ecommerce/stores")
    public ResponseEntity<StoreView> createStore(@RequestBody StoreRequest request,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        EcommerceStore value = administration.createStore(request.merchantId(),
                request.storeCode(), request.name(), request.allowedDomain(),
                request.returnUrl(), request.notificationUrl(), correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(StoreView.from(value));
    }

    @PostMapping("/ecommerce/stores/{id}/ready")
    public StoreView readyStore(@PathVariable UUID id) {
        return StoreView.from(administration.readyStore(id));
    }

    @PostMapping("/ecommerce/stores/{id}/activate")
    public StoreView activateStore(@PathVariable UUID id,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return StoreView.from(administration.activateStore(id, correlationId));
    }

    @PostMapping("/ecommerce/profiles")
    public ResponseEntity<EcommerceProfileView> createEcommerceProfile(
            @RequestBody EcommerceProfileRequest request,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        EcommerceAcceptanceProfile value = administration.createEcommerceProfile(
                request.acquirerId(), request.storeId(), request.contractId(),
                request.logicalTerminalId(), request.currency(), request.captureMode(),
                correlationId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EcommerceProfileView.from(value));
    }

    public record ProductRequest(String acquirerId, String productCode,
            int productVersion, AcceptanceChannel channel, String currency) {}
    public record MerchantRequest(String acquirerId, String legalName,
            String tradingName, String registrationNumber, String country, String mcc) {}
    public record OutletRequest(String outletCode, String name,
            String addressLine, String country) {}
    public record MerchantContractRequest(String acquirerId, String externalReference,
            UUID merchantId, String settlementAccountReference, UUID productId,
            String mid, String mcc, String settlementCurrency, AcceptanceChannel channel) {}
    public record DeviceContractRequest(String acquirerId, String externalReference,
            UUID merchantId, UUID parentContractId, UUID productId, UUID outletId,
            String terminalId, AcceptanceChannel channel, boolean extendedSet,
            String macData, boolean macRequired) {}
    public record TerminalRequest(String acquirerId, String serialNumber, String modelCode) {}
    public record AssignmentRequest(String acquirerId, UUID deviceContractId) {}
    public record StoreRequest(UUID merchantId, String storeCode, String name,
            String allowedDomain, String returnUrl, String notificationUrl) {}
    public record EcommerceProfileRequest(String acquirerId, UUID storeId,
            UUID contractId, String logicalTerminalId, String currency, String captureMode) {}

    public record ProductView(UUID id, String acquirerId, String productCode,
            int productVersion, AcceptanceChannel channel, String currency,
            ApprovalStatus status) {
        static ProductView from(AcceptanceProduct value) {
            return new ProductView(value.id(), value.acquirerId(), value.productCode(),
                    value.productVersion(), value.channel(), value.defaultCurrency(), value.status());
        }
    }

    public record MerchantView(UUID id, String acquirerId, String legalName,
            String tradingName, String registrationNumber, String country,
            String mcc, ApprovalStatus status) {
        static MerchantView from(Merchant value) {
            return new MerchantView(value.id(), value.acquirerId(), value.legalName(),
                    value.tradingName(), value.registrationNumber(), value.country(),
                    value.mcc(), value.status());
        }
    }

    public record OutletView(UUID id, UUID merchantId, String outletCode,
            String name, String country, boolean active) {
        static OutletView from(MerchantOutlet value) {
            return new OutletView(value.id(), value.merchantId(), value.outletCode(),
                    value.name(), value.country(), value.isActive());
        }
    }

    public record ContractView(UUID id, String institutionId, String externalReference,
            UUID merchantId, UUID productId, PaymentContractType contractType,
            UUID parentContractId, PaymentContractStatus status) {
        static ContractView from(AcquiringContract value) {
            return new ContractView(value.id(), value.institutionId(),
                    value.externalReference(), value.merchantId(), value.productId(),
                    value.contractType(), value.parentContractId(), value.status());
        }
    }

    public record TerminalView(UUID id, String acquirerId, String serialNumber,
            String modelCode, TerminalStatus status) {
        static TerminalView from(TerminalDevice value) {
            return new TerminalView(value.id(), value.acquirerId(), value.serialNumber(),
                    value.modelCode(), value.status());
        }
    }

    public record AssignmentView(UUID id, UUID terminalDeviceId, UUID outletId,
            UUID deviceContractId, boolean active) {
        static AssignmentView from(TerminalAssignment value) {
            return new AssignmentView(value.id(), value.terminalDeviceId(), value.outletId(),
                    value.deviceContractId(), value.isActive());
        }
    }

    public record StoreView(UUID id, UUID merchantId, String storeCode,
            String name, String allowedDomain, EcommerceStatus status) {
        static StoreView from(EcommerceStore value) {
            return new StoreView(value.id(), value.merchantId(), value.storeCode(),
                    value.name(), value.allowedDomain(), value.status());
        }
    }

    public record EcommerceProfileView(UUID id, UUID storeId, UUID contractId,
            String logicalTerminalId, String currency, String captureMode,
            boolean active) {
        static EcommerceProfileView from(EcommerceAcceptanceProfile value) {
            return new EcommerceProfileView(value.id(), value.storeId(), value.contractId(),
                    value.logicalTerminalId(), value.currency(), value.captureMode(), value.isActive());
        }
    }
}
