package com.staging.sg.acquiring.api;

import com.staging.sg.acquiring.service.EcommerceTransactionService;
import com.staging.sg.common.ecommerce.EcommercePurchaseRequest;
import com.staging.sg.common.ecommerce.EcommercePurchaseResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/acquiring/v1/ecommerce")
public class EcommerceTransactionController {
    private final EcommerceTransactionService transactions;

    public EcommerceTransactionController(EcommerceTransactionService transactions) {
        this.transactions = transactions;
    }

    @PostMapping("/transactions")
    public EcommercePurchaseResponse purchase(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @RequestBody EcommercePurchaseRequest request) {
        if (!idempotencyKey.equals(request.idempotencyKey())
                || !correlationId.equals(request.correlationId())) {
            throw new IllegalArgumentException(
                    "Headers and ecommerce request identifiers must match");
        }
        return transactions.purchase(request);
    }
}
