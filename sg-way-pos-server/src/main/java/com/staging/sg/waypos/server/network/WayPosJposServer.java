package com.staging.sg.waypos.server.network;

import com.staging.sg.common.iso.WayPosLengthChannel;
import com.staging.sg.common.iso.WayPosPackager;
import com.staging.sg.common.iso.WayPosMessageValidator;
import com.staging.sg.waypos.server.config.WayPosProperties;
import com.staging.sg.waypos.server.service.PosRoutingService;
import com.staging.sg.waypos.server.service.WayPosSystemMessageService;
import com.staging.sg.waypos.server.service.WayPosSecurityService;
import com.staging.sg.waypos.server.service.WayPosSecurityService.PosSecurityException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISORequestListener;
import org.jpos.iso.ISOServer;
import org.jpos.iso.ISOSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WayPosJposServer {
    private static final Logger log = LoggerFactory.getLogger(WayPosJposServer.class);

    private final WayPosProperties properties;
    private final WayPosPackager packager;
    private final WayPosIsoMapper mapper;
    private final PosRoutingService routingService;
    private final WayPosSystemMessageService systemMessages;
    private final WayPosSecurityService security;
    private ISOServer server;
    private Thread serverThread;

    public WayPosJposServer(
            WayPosProperties properties, WayPosPackager packager,
            WayPosIsoMapper mapper, PosRoutingService routingService,
            WayPosSystemMessageService systemMessages, WayPosSecurityService security) {
        this.properties = properties;
        this.packager = packager;
        this.mapper = mapper;
        this.routingService = routingService;
        this.systemMessages = systemMessages;
        this.security = security;
    }

    @PostConstruct
    public void start() {
        WayPosLengthChannel channel = new WayPosLengthChannel(packager);
        server = new ISOServer(properties.isoPort(), channel, null);
        server.addISORequestListener(new Listener());
        serverThread = new Thread(server, "way-pos-jpos-server");
        serverThread.setDaemon(true);
        serverThread.start();
        log.info("[WAY-POS] jPOS server started on port {}, T1={}s",
                properties.isoPort(), properties.t1Seconds());
    }

    @PreDestroy
    public void stop() {
        if (server != null) server.shutdown();
        if (serverThread != null) serverThread.interrupt();
    }

    private final class Listener implements ISORequestListener {
        @Override
        public boolean process(ISOSource source, ISOMsg message) {
            try {
                WayPosSafeMessageTrace.received(message);
                log.info("[WAY-POS] received MTI={} STAN={} terminal={}",
                        message.getMTI(), text(message, 11), text(message, 41));
                WayPosMessageValidator.validateRequest(message);
                var profile = security.validate(message);
                if (systemMessages.supports(message)) {
                    ISOMsg response = systemMessages.process(message);
                    security.protectResponse(message, response, profile);
                    send(source, response);
                    return true;
                }
                var request = mapper.toRequest(message);
                var response = routingService.process(request);
                ISOMsg isoResponse = mapper.toResponse(message, response);
                security.protectResponse(message, isoResponse, profile);
                send(source, isoResponse);
                return true;
            } catch (PosSecurityException e) {
                log.warn("[WAY-POS] security rejection RC{}: {}", e.responseCode(), e.getMessage());
                try {
                    send(source, mapper.error(message, e.responseCode()));
                    return true;
                } catch (Exception sendFailure) {
                    return false;
                }
            } catch (Exception e) {
                log.error("[WAY-POS] processing failure", e);
                try {
                    send(source, mapper.systemError(message));
                    return true;
                } catch (Exception sendFailure) {
                    log.error("[WAY-POS] unable to send RC96", sendFailure);
                    return false;
                }
            }
        }

        private void send(ISOSource source, ISOMsg response) throws Exception {
            WayPosSafeMessageTrace.outgoing(response);
            source.send(response);
            log.info("[WAY-POS] sent MTI={} STAN={} RRN={} terminal={} RC={}",
                    response.getMTI(), text(response, 11), text(response, 37),
                    text(response, 41), text(response, 39));
        }

        private String text(ISOMsg message, int field) {
            return message.hasField(field) ? message.getString(field) : "-";
        }
    }
}
