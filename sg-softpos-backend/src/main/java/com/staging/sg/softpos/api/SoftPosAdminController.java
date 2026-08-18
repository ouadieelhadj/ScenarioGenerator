package com.staging.sg.softpos.api;

import com.staging.sg.softpos.contracts.SoftPosContracts.*;
import com.staging.sg.softpos.domain.*;
import com.staging.sg.softpos.repository.SoftPosRepositories.*;
import com.staging.sg.softpos.service.SoftPosHashing;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/softpos/v1")
public class SoftPosAdminController {
    private final RequestIdentity identities; private final ActivationRepository activations;
    private final DeviceRepository devices; private final RouteRepository routes; private final TransactionRepository transactions;
    public SoftPosAdminController(RequestIdentity identities, ActivationRepository activations, DeviceRepository devices,
            RouteRepository routes, TransactionRepository transactions) {
        this.identities = identities; this.activations = activations; this.devices = devices; this.routes = routes; this.transactions = transactions;
    }
    @PostMapping("/activations") @Transactional
    public Map<String, Object> issue(Authentication auth, @RequestHeader(value = "X-Member-Id", required = false) String member,
            @RequestBody ActivationIssue request) {
        String memberId = identities.resolve(auth, member, null).memberId(); String code = UUID.randomUUID().toString();
        Instant expires = Instant.now().plus(Duration.ofMinutes(30));
        activations.save(SoftPosActivation.issue(SoftPosHashing.sha256(code), memberId, request.merchantId(), request.outletId(), request.terminalId(), expires));
        return Map.of("activationCode", code, "expiresAt", expires);
    }
    @GetMapping("/devices") public List<Map<String, Object>> devices(Authentication auth, @RequestHeader(value = "X-Member-Id", required = false) String member) {
        String memberId = identities.resolve(auth, member, null).memberId(); return devices.findAllByMemberIdOrderByDeviceId(memberId).stream().map(d -> Map.<String,Object>of(
                "deviceId", d.getDeviceId(), "merchantId", d.getMerchantId(), "outletId", d.getOutletId(), "terminalId", d.getTerminalId(), "status", d.getStatus(), "applicationVersion", d.getApplicationVersion())).toList();
    }
    @PatchMapping("/devices/{deviceId}/status") @Transactional
    public Map<String, Object> status(Authentication auth, @RequestHeader(value = "X-Member-Id", required = false) String member,
            @PathVariable String deviceId, @RequestBody StatusUpdate request) {
        String memberId = identities.resolve(auth, member, null).memberId(); SoftPosDevice d = devices.findByDeviceIdAndMemberId(deviceId, memberId).orElseThrow();
        d.changeStatus(request.status()); devices.save(d); return Map.of("deviceId", d.getDeviceId(), "status", d.getStatus());
    }
    @GetMapping("/poserver-routes") public List<RouteView> routes(Authentication auth, @RequestHeader(value = "X-Member-Id", required = false) String member) {
        String memberId = identities.resolve(auth, member, null).memberId(); return routes.findAllByMemberIdOrderByEnvironment(memberId).stream().map(SoftPosAdminController::view).toList();
    }
    @PutMapping("/poserver-routes/{environment}") @Transactional
    public RouteView route(Authentication auth, @RequestHeader(value = "X-Member-Id", required = false) String member,
            @PathVariable String environment, @RequestBody RouteUpdate update) {
        String memberId = identities.resolve(auth, member, null).memberId(); SoftPosPosServerRoute route = routes.findByMemberIdAndEnvironment(memberId, environment).orElseGet(() -> SoftPosPosServerRoute.configured(memberId, environment, update.primaryMode(), update.endpoint(), update.connectTimeoutMillis(), update.responseTimeoutMillis(), update.active()));
        if (routes.findByMemberIdAndEnvironment(memberId, environment).isPresent()) route.update(update.primaryMode(), update.endpoint(), update.connectTimeoutMillis(), update.responseTimeoutMillis(), update.active());
        return view(routes.save(route));
    }
    @GetMapping("/transactions") public List<PaymentResponse> transactions(Authentication auth, @RequestHeader(value = "X-Member-Id", required = false) String member) {
        String memberId = identities.resolve(auth, member, null).memberId(); return transactions.findTop100ByMemberIdOrderByUpdatedAtDesc(memberId).stream().map(t -> new PaymentResponse(t.getClientTransactionId(), t.getStatus(), t.getResponseCode(), t.getAuthorizationCode(), null, false, t.getUpdatedAt())).toList();
    }
    private static RouteView view(SoftPosPosServerRoute r) { return new RouteView(r.getMemberId(), r.getEnvironment(), r.getPrimaryMode(), r.getEndpoint(), r.getConnectTimeoutMillis(), r.getResponseTimeoutMillis(), r.isActive()); }
    public record ActivationIssue(String merchantId, String outletId, String terminalId) {}
    public record StatusUpdate(DeviceStatus status) {}
    public record RouteUpdate(PosServerMode primaryMode, String endpoint, int connectTimeoutMillis, int responseTimeoutMillis, boolean active) {}
}
