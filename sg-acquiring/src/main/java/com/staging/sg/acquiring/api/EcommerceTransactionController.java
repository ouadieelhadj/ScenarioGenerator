package com.staging.sg.acquiring.api;

import com.staging.sg.acquiring.service.EcommerceTransactionService;
import com.staging.sg.acquiring.service.EcommerceRouteResolver;
import com.staging.sg.common.ecommerce.EcommercePurchaseRequest;
import com.staging.sg.common.ecommerce.EcommercePurchaseResponse;
import com.staging.sg.common.ecommerce.EcommerceRoutePreviewRequest;
import com.staging.sg.common.ecommerce.EcommerceRoutePreviewResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/acquiring/v1/ecommerce")
public class EcommerceTransactionController {
    private final EcommerceTransactionService transactions;
    private final EcommerceRouteResolver routes;

    public EcommerceTransactionController(EcommerceTransactionService transactions,
            EcommerceRouteResolver routes) {
        this.transactions = transactions;
        this.routes = routes;
    }

    @PostMapping("/routes/resolve")
    public EcommerceRoutePreviewResponse resolveRoute(
            @RequestBody EcommerceRoutePreviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A payment identifier is required");
        }
        return routes.preview(request.paymentIdentifier());
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
