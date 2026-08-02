package com.staging.sg.deployment.catalog;

import com.staging.sg.deployment.model.ModuleSide;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ModuleCatalog {
    private final Map<String, ModuleDescriptor> modules;

    private ModuleCatalog(List<ModuleDescriptor> descriptors) {
        Map<String, ModuleDescriptor> indexed = new LinkedHashMap<>();
        descriptors.forEach(module -> indexed.put(module.code(), module));
        this.modules = Map.copyOf(indexed);
    }

    public static ModuleCatalog scenarioGenerator() {
        return new ModuleCatalog(List.of(
                member("SERVER_POS", "ServerPOS", "sg-way-pos-server",
                        "com.staging.sg.waypos.server.WayPosServerApplication", 8530,
                        "WAY_POS_DB_PASSWORD", "WAY_POS_PAN_PEPPER", "WAY_POS_OUTBOX_KEY_HEX"),
                member("ACQUIRING", "Acquisition POS / e-commerce", "sg-acquiring",
                        "com.staging.sg.acquiring.AcquiringApplication", 8550, "DB_PASSWORD"),
                member("CARD_ISSUING", "Card Issuing", "sg-card-issuing",
                        "com.staging.sg.card.issuing.CardIssuingApplication", 8540,
                        "CARD_ISSUING_DB_PASSWORD"),
                member("THREE_DS_MEMBER", "3DS Member", "sg-3ds-member",
                        "com.staging.sg.threeds.member.ThreeDsMemberApplication", 8560),
                member("SWAM_MEMBER", "SWAM Member", "sg-swam-lis-member",
                        "com.staging.sg.swam.lis.member.SwamLisMemberApplication", 8521),
                member("DMAS_MEMBER", "DMAS Member", "sg-mc-dmas-member",
                        "com.staging.sg.mc.dmas.member.SgMcDmasMemberApplication", null),
                member("DMCS_MEMBER_ACQUIRER", "DMCS Member Acquirer", "sg-dmcs-acquirer",
                        "com.staging.sg.dmcs.acquirer.SgDmcsAcquirerApplication", 8082),
                member("DMCS_MEMBER_ISSUER", "DMCS Member Issuer", "sg-dmcs-issuer",
                        "com.staging.sg.dmcs.issuer.SgDmcsIssuerApplication", 8083),
                simulator("POS_SIMULATOR", "POS Simulator", "sg-way-pos-simulator",
                        "com.staging.sg.waypos.simulator.WayPosSimulatorApplication", 8532),
                simulator("MERCHANT_SITE_SIMULATOR", "Merchant Site Simulator", "sg-merchant-site-simulator",
                        "com.staging.sg.ecommerce.simulator.EcommerceSimulatorApplication", 8551),
                simulator("THREE_DS_NETWORK_SIMULATOR", "3DS Network Simulator", "sg-3ds-network-simulator",
                        "com.staging.sg.threeds.network.ThreeDsNetworkSimulatorApplication", 8561),
                simulator("CARD_NETWORK_SIMULATOR", "Visa / Mastercard Network Simulator",
                        "sg-visa-mastercard-gateway-simulator",
                        "com.staging.sg.cardnetwork.gateway.VisaMastercardGatewaySimulatorApplication", 8563),
                simulator("DMAS_MASTERCARD_SIMULATOR", "DMAS Mastercard Simulator", "sg-mc-dmas-mastercard",
                        "com.staging.sg.mc.dmas.mastercard.SgMcDmasMastercardApplication", null),
                simulator("SWAM_SWITCH_SIMULATOR", "SWAM Switch Simulator", "sg-swam-lis-switch",
                        "com.staging.sg.swam.lis.switching.SwamLisSwitchApplication", 8522)
        ));
    }

    public static ModuleCatalog of(List<ModuleDescriptor> descriptors) {
        return new ModuleCatalog(descriptors);
    }

    public List<ModuleDescriptor> all() {
        return modules.values().stream().toList();
    }

    public Optional<ModuleDescriptor> find(String code) {
        return Optional.ofNullable(modules.get(code));
    }

    private static ModuleDescriptor member(String code, String label, String artifact,
                                           String mainClass, Integer port, String... variables) {
        return new ModuleDescriptor(code, label, ModuleSide.MEMBER, artifact, mainClass,
                port, List.of(variables));
    }

    private static ModuleDescriptor simulator(String code, String label, String artifact,
                                              String mainClass, Integer port, String... variables) {
        return new ModuleDescriptor(code, label, ModuleSide.SIMULATOR, artifact, mainClass,
                port, List.of(variables));
    }
}
