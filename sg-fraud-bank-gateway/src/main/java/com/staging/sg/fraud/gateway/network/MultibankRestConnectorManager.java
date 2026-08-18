package com.staging.sg.fraud.gateway.network;

import com.staging.sg.fraud.gateway.domain.GatewayConnectionProfile;
import com.staging.sg.fraud.gateway.service.GatewayRouteRegistry;
import jakarta.annotation.PreDestroy;
import org.apache.catalina.connector.Connector;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.embedded.tomcat.*;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@ConditionalOnProperty(name = "fraud-gateway.routing.dynamic-listeners-enabled", havingValue = "true")
public class MultibankRestConnectorManager {
    private static final Logger log = LoggerFactory.getLogger(MultibankRestConnectorManager.class);
    private final GatewayRouteRegistry routes;
    private final ServletWebServerApplicationContext context;
    private final List<Connector> connectors = new ArrayList<>();
    public MultibankRestConnectorManager(GatewayRouteRegistry routes, ServletWebServerApplicationContext context) {
        this.routes = routes; this.context = context;
    }
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void startConfiguredPorts() throws Exception {
        if (!(context.getWebServer() instanceof TomcatWebServer webServer))
            throw new IllegalStateException("Dedicated REST ports require embedded Tomcat");
        for (GatewayConnectionProfile profile : routes.activeProfiles(GatewayRouteRegistry.REST)) {
            if (!"SERVER".equals(profile.connectionMode())) continue;
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(profile.listenPort()); connector.setProperty("connectionTimeout", "5000");
            webServer.getTomcat().getService().addConnector(connector); connector.start(); connectors.add(connector);
            log.info("[FRAUD-REST] member port started code={} member={} port={}",
                    profile.connectionCode(), profile.memberId(), profile.listenPort());
        }
    }
    @PreDestroy
    public synchronized void stop() {
        if (!(context.getWebServer() instanceof TomcatWebServer webServer)) return;
        for (Connector connector : connectors) {
            try { connector.stop(); connector.destroy(); }
            catch (Exception failure) { log.warn("[FRAUD-REST] shutdown failed type={}", failure.getClass().getSimpleName()); }
            webServer.getTomcat().getService().removeConnector(connector);
        }
        connectors.clear();
    }
}
