package com.staging.sg.waypos.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "way-pos.local-test-bootstrap-enabled", havingValue = "true")
public class WayPosLocalInterfaceKeyBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(
            WayPosLocalInterfaceKeyBootstrap.class);

    private final WayPosPinTranslationService service;
    private final String clearPek;
    private final String interfaceCode;

    public WayPosLocalInterfaceKeyBootstrap(
            WayPosPinTranslationService service,
            @Value("${way-pos.test-interface-pek-clear:}") String clearPek,
            @Value("${way-pos.test-interface-code:DMAS_MEMBER}") String interfaceCode) {
        this.service = service;
        this.clearPek = clearPek;
        this.interfaceCode = interfaceCode;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (clearPek == null || clearPek.isBlank()) {
            return;
        }
        String kcv = service.provisionClearInterfaceKeyTestOnly(interfaceCode, clearPek);
        log.info("[WAY-POS][TEST-ONLY] destination PEK active: interface={} kcv={}",
                interfaceCode, kcv);
    }
}
