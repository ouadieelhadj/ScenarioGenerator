package com.staging.sg.dmas.acquirer.api;

import com.staging.sg.common.iso.McPackagerEbcdic;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoints de test du packager EBCDIC (D-sign-on).
 * Protégé ADMIN via la règle URL /api/admin/** du SecurityConfig.
 * Vérifie round-trip pack -> unpack sans réseau.
 */
@RestController
@RequestMapping("/api/admin/dmas/packager")
public class PackagerTestController {

    private static final Logger log = LoggerFactory.getLogger(PackagerTestController.class);

    private final McPackagerEbcdic packager = new McPackagerEbcdic();

    @GetMapping("/signon-roundtrip")
    public ResponseEntity<?> signonRoundtrip(@RequestParam(defaultValue = "001") String de070) {
        try {
            ISOMsg msg = new ISOMsg();
            msg.setPackager(packager);
            msg.setMTI("0800");
            msg.set(7,  "0619103045");
            msg.set(11, "000001");
            msg.set(70, de070);

            byte[] packed = msg.pack();
            String hex = ISOUtil.hexString(packed);

            ISOMsg parsed = new ISOMsg();
            parsed.setPackager(packager);
            parsed.unpack(packed);

            Map<String,Object> result = new LinkedHashMap<>();
            result.put("packed_hex", hex);
            result.put("packed_length", packed.length);
            result.put("mti_sent", "0800");
            result.put("mti_parsed", parsed.getMTI());
            result.put("de7_sent",  "0619103045");
            result.put("de7_parsed",  parsed.getString(7));
            result.put("de11_sent", "000001");
            result.put("de11_parsed", parsed.getString(11));
            result.put("de70_sent", de070);
            result.put("de70_parsed", parsed.getString(70));

            boolean ok = "0800".equals(parsed.getMTI())
                    && "0619103045".equals(parsed.getString(7))
                    && "000001".equals(parsed.getString(11))
                    && de070.equals(parsed.getString(70));
            result.put("roundtrip_ok", ok);

            log.info("[DMAS-PKG] Sign-on roundtrip — ok={} hex={}", ok, hex);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[DMAS-PKG] Roundtrip failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    @GetMapping("/de48-roundtrip")
    public ResponseEntity<?> de48Roundtrip(@RequestParam(defaultValue = "PE16ABCDEF0123456789ABCDEF01234567D5D44F") String de048) {
        try {
            ISOMsg msg = new ISOMsg();
            msg.setPackager(packager);
            msg.setMTI("0800");
            msg.set(7,  "0619103045");
            msg.set(11, "000001");
            msg.set(48, de048);
            msg.set(70, "101");

            byte[] packed = msg.pack();
            String hex = ISOUtil.hexString(packed);

            ISOMsg parsed = new ISOMsg();
            parsed.setPackager(packager);
            parsed.unpack(packed);

            Map<String,Object> result = new LinkedHashMap<>();
            result.put("packed_hex", hex);
            result.put("packed_length", packed.length);
            result.put("de48_sent", de048);
            result.put("de48_parsed", parsed.getString(48));
            result.put("de70_parsed", parsed.getString(70));
            boolean ok = de048.equals(parsed.getString(48)) && "101".equals(parsed.getString(70));
            result.put("roundtrip_ok", ok);
            log.info("[DMAS-PKG] DE48 roundtrip — ok={} len={}", ok, de048.length());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[DMAS-PKG] DE48 roundtrip failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
