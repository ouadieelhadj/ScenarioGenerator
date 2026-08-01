package com.staging.sg.acquiring.service;

import com.staging.sg.common.ecommerce.EcommerceNetworkRoute;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/** Uses the same authoritative BIN routing table as ServerPOS. */
@Service
public class EcommerceRouteResolver {
    private static final String SQL = """
            SELECT bin_from, bin_to, interface_code
              FROM pos_bin_routes
             WHERE active = TRUE
             ORDER BY priority DESC
            """;

    private final JdbcTemplate jdbc;

    public EcommerceRouteResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EcommerceNetworkRoute resolve(
            String pan, EcommerceNetworkRoute requestedRoute) {
        List<RouteRange> ranges = jdbc.query(SQL, (rs, row) -> new RouteRange(
                rs.getString("bin_from"), rs.getString("bin_to"),
                rs.getString("interface_code")));
        EcommerceNetworkRoute resolved = ranges.stream()
                .filter(range -> range.contains(pan))
                .findFirst()
                .map(range -> fromInterfaceCode(range.interfaceCode()))
                .orElseThrow(() -> new IllegalStateException(
                        "No active BIN route for ecommerce payment identifier"));
        if (requestedRoute != EcommerceNetworkRoute.AUTO
                && requestedRoute != resolved) {
            throw new IllegalStateException(
                    "Requested ecommerce route does not match the authoritative BIN route");
        }
        return resolved;
    }

    private static EcommerceNetworkRoute fromInterfaceCode(String code) {
        return switch (code) {
            case "00000", "LOCAL_ISSUING" -> EcommerceNetworkRoute.LOCAL_ISSUING;
            case "DMAS_MEMBER", "DMAS_MASTERCARD" ->
                    EcommerceNetworkRoute.DMAS_MASTERCARD;
            case "SWAM_MEMBER", "SWAM" -> EcommerceNetworkRoute.SWAM;
            case "VISA" -> EcommerceNetworkRoute.VISA;
            default -> throw new IllegalStateException(
                    "Unsupported ecommerce BIN interface: " + code);
        };
    }

    private record RouteRange(String from, String to, String interfaceCode) {
        boolean contains(String pan) {
            int length = from.length();
            if (pan == null || pan.length() < length || to.length() != length) {
                return false;
            }
            String candidate = pan.substring(0, length);
            return candidate.compareTo(from) >= 0 && candidate.compareTo(to) <= 0;
        }
    }
}
