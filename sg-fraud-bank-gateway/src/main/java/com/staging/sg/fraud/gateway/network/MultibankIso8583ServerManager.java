package com.staging.sg.fraud.gateway.network;

import com.staging.sg.fraud.gateway.domain.GatewayConnectionProfile;
import com.staging.sg.fraud.gateway.service.*;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.*;
import org.jpos.iso.channel.NACChannel;
import org.jpos.iso.packager.ISO87APackager;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@ConditionalOnProperty(name = "fraud-gateway.routing.dynamic-listeners-enabled", havingValue = "true")
public class MultibankIso8583ServerManager {
    private static final Logger log = LoggerFactory.getLogger(MultibankIso8583ServerManager.class);
    private final GatewayRouteRegistry routes;
    private final PermanentIsoMessageProcessor processor;
    private final Map<String, ISOServer> servers = new LinkedHashMap<>();
    private final List<Thread> threads = new ArrayList<>();

    public MultibankIso8583ServerManager(GatewayRouteRegistry routes, PermanentIsoMessageProcessor processor) {
        this.routes = routes; this.processor = processor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void startConfiguredLinks() throws Exception {
        for (GatewayConnectionProfile profile : routes.activeProfiles(GatewayRouteRegistry.ISO8583)) {
            if (!"SERVER".equals(profile.connectionMode()) || servers.containsKey(profile.connectionCode())) continue;
            NACChannel channel = new NACChannel(new ISO87APackager(), null);
            ISOServer server = new ISOServer(profile.listenPort(), channel, null);
            server.addISORequestListener((source, request) -> process(profile, source, request));
            Thread thread = new Thread(server, "fraud-iso-" + profile.connectionCode());
            thread.setDaemon(true); thread.start();
            servers.put(profile.connectionCode(), server); threads.add(thread);
            log.info("[FRAUD-ISO] member link started code={} member={} sector={} port={}",
                    profile.connectionCode(), profile.memberId(), profile.sectorId(), profile.listenPort());
        }
    }

    private boolean process(GatewayConnectionProfile profile, ISOSource source, ISOMsg request) {
        try {
            source.send(processor.process(request, profile.memberId(), profile.zmkReference(),
                    profile.credentialReference()));
            return true;
        } catch (Exception failure) {
            log.warn("[FRAUD-ISO] member link processing failed code={} type={}",
                    profile.connectionCode(), failure.getClass().getSimpleName());
            return false;
        }
    }

    @PreDestroy
    public synchronized void stop() {
        servers.values().forEach(ISOServer::shutdown);
        threads.forEach(Thread::interrupt);
        servers.clear(); threads.clear();
    }
}
