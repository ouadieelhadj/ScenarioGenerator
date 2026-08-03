package com.staging.sg.ecommerce.simulator.service;

import com.staging.sg.common.ecommerce.EcommerceNetworkRoute;
import com.staging.sg.common.ecommerce.EcommercePurchaseResponse;
import com.staging.sg.common.threeds.ThreeDsFlow;
import com.staging.sg.ecommerce.simulator.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MerchantStorefrontService {
    private static final String ACQUIRER_ID = "ACQECOM";
    private static final String CURRENCY = "504";
    private static final Map<String, MerchantCatalogProduct> CATALOG = catalogData();

    private final EcommerceSimulatorClient client;
    private final Path profileIdFile;
    private final MerchantSiteType siteType;
    private final Map<UUID, MerchantOrderResponse> orders = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> checkoutOrders = new ConcurrentHashMap<>();

    public MerchantStorefrontService(EcommerceSimulatorClient client,
            @Value("${ecommerce-simulator.ui.profile-id-file:${user.dir}/runtime/acquiring-ecommerce-e2e/profile-id}")
            String profileIdFile,
            @Value("${ecommerce-simulator.site-type:NATIONAL}") MerchantSiteType siteType) {
        this.client = client;
        this.profileIdFile = Path.of(profileIdFile);
        this.siteType = siteType;
    }

    public List<MerchantCatalogProduct> catalog() {
        return List.copyOf(CATALOG.values());
    }

    public MerchantOrderResponse createOrder(MerchantOrderCreateRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()
                || request.items().size() > 10) {
            throw new IllegalArgumentException("Le panier marchand est invalide");
        }
        List<MerchantOrderLine> lines = new ArrayList<>();
        long total = 0;
        Set<String> uniqueProducts = new HashSet<>();
        for (MerchantOrderItemRequest item : request.items()) {
            if (item == null || item.productId() == null || item.quantity() < 1
                    || item.quantity() > 5 || !uniqueProducts.add(item.productId())) {
                throw new IllegalArgumentException("Un article du panier est invalide");
            }
            MerchantCatalogProduct product = CATALOG.get(item.productId());
            if (product == null) {
                throw new IllegalArgumentException("Article marchand inconnu");
            }
            long lineTotal = Math.multiplyExact(product.unitPriceMinor(), item.quantity());
            total = Math.addExact(total, lineTotal);
            lines.add(new MerchantOrderLine(product.id(), product.name(), item.quantity(),
                    product.unitPriceMinor(), lineTotal));
        }
        UUID orderId = UUID.randomUUID();
        MerchantOrderResponse order = new MerchantOrderResponse(orderId,
                "ATLAS-" + orderId.toString().substring(0, 8).toUpperCase(Locale.ROOT),
                List.copyOf(lines), total, CURRENCY, "AWAITING_PAYMENT");
        orders.put(orderId, order);
        return order;
    }

    public MerchantOrderResponse order(UUID orderId) {
        return requireOrder(orderId);
    }

    public MerchantPaymentStartResponse startPayment(UUID orderId,
            MerchantCardPaymentRequest payment) {
        MerchantOrderResponse order = requireOrder(orderId);
        if (!"AWAITING_PAYMENT".equals(order.status())) {
            throw new IllegalStateException("Cette commande ne peut plus etre payee");
        }
        String pan = digits(payment == null ? null : payment.pan());
        String expiry = digits(payment == null ? null : payment.expiry());
        if (payment == null || blank(payment.cardholder())
                || payment.cardholder().trim().length() > 40
                || !pan.matches("\\d{12,19}") || !expiry.matches("\\d{4}")) {
            throw new IllegalArgumentException("Les donnees de carte sont invalides");
        }
        UUID transactionId = UUID.randomUUID();
        SimulatorPurchaseRequest request = new SimulatorPurchaseRequest(
                transactionId.toString(), "merchant-" + transactionId,
                "merchant-idem-" + transactionId, ACQUIRER_ID, profileId(),
                order.orderReference(), order.totalMinor(), order.currency(), pan, expiry,
                EcommerceNetworkRoute.AUTO, siteType, null, ThreeDsFlow.CHALLENGE,
                null, null);
        InteractiveCheckoutStartResponse started = client.startAutomatic(request);
        if (started.checkoutId() != null) {
            checkoutOrders.put(started.checkoutId(), orderId);
        } else if (started.purchase() != null) {
            orders.put(orderId, withPaymentStatus(order, started.purchase()));
        }
        return new MerchantPaymentStartResponse(orderId, order.orderReference(),
                started.state(), started.checkoutId(), started.challengeUrl(),
                started.purchase());
    }

    public MerchantPaymentReceipt completePayment(UUID checkoutId) {
        UUID orderId = checkoutOrders.get(checkoutId);
        if (orderId == null) {
            throw new IllegalArgumentException("Checkout marchand inconnu");
        }
        EcommercePurchaseResponse payment = client.completeInteractive(checkoutId);
        MerchantOrderResponse completed = withPaymentStatus(requireOrder(orderId), payment);
        orders.put(orderId, completed);
        checkoutOrders.remove(checkoutId);
        return new MerchantPaymentReceipt(completed, payment);
    }

    public MerchantSiteType siteType() {
        return siteType;
    }

    private UUID profileId() {
        try {
            return UUID.fromString(Files.readString(profileIdFile).trim());
        } catch (Exception exception) {
            throw new IllegalStateException("Le profil e-commerce n'est pas provisionne");
        }
    }

    private MerchantOrderResponse requireOrder(UUID orderId) {
        MerchantOrderResponse order = orders.get(orderId);
        if (order == null) throw new IllegalArgumentException("Commande marchande inconnue");
        return order;
    }

    private static MerchantOrderResponse withPaymentStatus(MerchantOrderResponse order,
            EcommercePurchaseResponse payment) {
        String status = payment != null && "APPROVED".equals(payment.status())
                ? "PAID" : "PAYMENT_FAILED";
        return new MerchantOrderResponse(order.orderId(), order.orderReference(),
                order.lines(), order.totalMinor(), order.currency(), status);
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Map<String, MerchantCatalogProduct> catalogData() {
        Map<String, MerchantCatalogProduct> products = new LinkedHashMap<>();
        products.put("SG-LAB", new MerchantCatalogProduct("SG-LAB",
                "Pack ScenarioGenerator Lab", "Laboratoire monetique",
                "Acces a une campagne guidee de simulation monétique de bout en bout.",
                List.of("Activation immediate", "Parcours Issuing et Acquiring",
                        "Paiement protege par 3-D Secure"),
                1000, CURRENCY, "SG", "Le plus choisi"));
        products.put("POS-DISCOVERY", new MerchantCatalogProduct("POS-DISCOVERY",
                "Atelier Terminal POS", "Acquisition TPE",
                "Session pratique de prise en main du terminal et de ServerPOS.",
                List.of("Achat et annulation", "RKI sandbox", "Journal de transaction"),
                1500, CURRENCY, "POS", "Nouveau"));
        products.put("3DS-CHALLENGE", new MerchantCatalogProduct("3DS-CHALLENGE",
                "Parcours 3DS Challenge", "E-commerce securise",
                "Demonstration du parcours navigateur, de l'ACS et de la preuve 3DS.",
                List.of("Directory Server simule", "OTP sandbox", "Preuve anti-rejeu"),
                2000, CURRENCY, "3DS", "Securise"));
        return Collections.unmodifiableMap(products);
    }
}
