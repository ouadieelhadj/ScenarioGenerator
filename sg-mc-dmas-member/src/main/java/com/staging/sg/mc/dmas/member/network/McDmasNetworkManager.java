package com.staging.sg.mc.dmas.member.network;

import com.staging.sg.common.iso.McDmasNetworkUtil;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Acquirer DMAS — gestion réseau (sign-on / sign-off / echo).
 * Même pattern que McNetworkManager (ASCII) mais via McDmasNetworkUtil (EBCDIC).
 * Pas d'auto-signon : tout est déclenché par endpoint REST.
 */
@Service
public class McDmasNetworkManager {

    private static final Logger log = LoggerFactory.getLogger(McDmasNetworkManager.class);

    private final McDmasNetworkUtil net;

    @Value("${dmas.issuer-host:localhost}") private String issuerHost;
    @Value("${dmas.issuer-port:8500}")      private int    issuerPort;
    @Value("${dmas.timeout-seconds:30}")    private int    timeoutSeconds;

    private final AtomicBoolean signedOn = new AtomicBoolean(false);

    public McDmasNetworkManager(McDmasNetworkUtil net) {
        this.net = net;
    }

    public boolean isSignedOn() { return signedOn.get(); }

    public Map<String,Object> sendSignOn()  throws Exception { return sendNetwork("001", "SIGN-ON"); }
    public Map<String,Object> sendSignOff() throws Exception { return sendNetwork("002", "SIGN-OFF"); }
    public Map<String,Object> sendEcho()    throws Exception { return sendNetwork("270", "ECHO"); }

    private Map<String,Object> sendNetwork(String de070, String label) throws Exception {
        String stan = net.generateStan();
        String dt   = new SimpleDateFormat("MMddHHmmss").format(new Date());

        ISOMsg request = new ISOMsg();
        request.setPackager(net.getPackager());
        request.setMTI("0800");
        request.set(7,  dt);
        request.set(11, stan);
        request.set(70, de070);

        String reqHex = ISOUtil.hexString(request.pack());
        log.info("[DMAS-ACQ] {} -> 0800 DE70={} STAN={} hex={}", label, de070, stan, reqHex);

        ISOMsg response = net.sendAndReceive(request, issuerHost, issuerPort, timeoutSeconds);
        String rc = net.safeGet(response, 39);
        boolean ok = "00".equals(rc);

        if ("001".equals(de070) && ok) signedOn.set(true);
        if ("002".equals(de070) && ok) signedOn.set(false);

        String respHex = ISOUtil.hexString(response.pack());
        log.info("[DMAS-ACQ] {} <- 0810 DE39={} ok={} hex={}", label, rc, ok, respHex);

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("type", label);
        r.put("de070", de070);
        r.put("stan", stan);
        r.put("mti_response", response.getMTI());
        r.put("de039", rc);
        r.put("success", ok);
        r.put("signed_on", signedOn.get());
        r.put("request_hex", reqHex);
        r.put("response_hex", respHex);
        return r;
    }
}
