package com.staging.sg.acquirer;

import com.staging.sg.iso.NetworkUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class McNetworkManager {

    private static final Logger log = LoggerFactory.getLogger(McNetworkManager.class);

    private final NetworkUtil          net;
    private final McKeyExchangeManager keyExchangeManager;

    @Value("${mc.acquirer.mode:loopback}")          private String  mode;
    @Value("${mc.acquirer.mas.host:127.0.0.1}")     private String  masHost;
    @Value("${mc.acquirer.mas.port:8200}")           private int     masPort;
    @Value("${mc.acquirer.mas.timeout-seconds:30}")  private int     timeoutSeconds;
    @Value("${mc.network.echo-interval-seconds:60}") private int     echoIntervalSeconds;
    @Value("${mc.network.auto-signon:true}")         private boolean autoSignon;

    private final AtomicBoolean keysExchanged = new AtomicBoolean(false);
    private final AtomicBoolean signedOn      = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private long signOnTime      = 0;
    private long lastEchoTime    = 0;
    private String zmkKcv, zpkKcv, zakKcv;

    public McNetworkManager(NetworkUtil net, McKeyExchangeManager keyExchangeManager) {
        this.net                = net;
        this.keyExchangeManager = keyExchangeManager;
    }

    @PostConstruct
    public void init() {
        if (!autoSignon) {
            log.info("[ACQUIRING] Auto sign-on disabled");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "mc-network-manager"));

        scheduler.schedule(() -> {
            try {
                // Step 1 : Key Exchange
                log.info("[ACQUIRING] Step 1 — Key Exchange...");
                McKeyExchangeResult keyResult = keyExchangeManager.exchangeAllKeys();
                if (keyResult.isSuccess()) {
                    keysExchanged.set(true);
                    zmkKcv = keyResult.getZmkKcv();
                    zpkKcv = keyResult.getZpkKcv();
                    zakKcv = keyResult.getZakKcv();
                    log.info("[ACQUIRING] Key Exchange OK — ZMK={} ZPK={} ZAK={}",
                            zmkKcv, zpkKcv, zakKcv);
                } else {
                    log.warn("[ACQUIRING] Key Exchange failed — {}", keyResult.getMessage());
                    return;
                }

                // Step 2 : Sign-on
                log.info("[ACQUIRING] Step 2 — Sign-on...");
                McNetworkResult signonResult = sendSignOn();
                if (signonResult.isSuccess()) {
                    log.info("[ACQUIRING] Sign-on OK — session established");
                    startHeartbeat();
                } else {
                    log.warn("[ACQUIRING] Sign-on failed — {}", signonResult.getMessage());
                }

            } catch (Exception e) {
                log.warn("[ACQUIRING] Startup failed : {}", e.getMessage());
            }
        }, 2, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) scheduler.shutdown();
        signedOn.set(false);
        keysExchanged.set(false);
        log.info("[ACQUIRING] Network manager stopped");
    }

    // ── Public API ───────────────────────────────────────────

    public McKeyExchangeResult performKeyExchange() throws Exception {
        McKeyExchangeResult result = keyExchangeManager.exchangeAllKeys();
        if (result.isSuccess()) {
            keysExchanged.set(true);
            zmkKcv = result.getZmkKcv();
            zpkKcv = result.getZpkKcv();
            zakKcv = result.getZakKcv();
        }
        return result;
    }

    public McNetworkResult sendSignOn() throws Exception {
        String stan = net.generateStan();
        ISOMsg request = buildNetworkMsg("0800", "301", stan);

        logIsoMsg("SENT", "0800 Sign-on", request);

        ISOMsg response = net.sendAndReceive(request, resolveHost(), resolvePort(), timeoutSeconds);

        logIsoMsg("RECEIVED", "0810 Sign-on Response", response);

        String rc = net.safeGet(response, 39);
        boolean success = "00".equals(rc);
        if (success) { signedOn.set(true); signOnTime = System.currentTimeMillis(); }

        log.info("[ACQUIRING] Sign-on — DE39={} success={}", rc, success);

        return McNetworkResult.builder()
                .type("SIGN-ON").stan(stan).responseCode(rc).success(success)
                .message(success ? "Session established" : "Rejected DE39=" + rc)
                .requestHex(ISOUtil.hexString(request.pack()))
                .responseHex(ISOUtil.hexString(response.pack()))
                .build();
    }

    public McNetworkResult sendEcho() throws Exception {
        String stan = net.generateStan();
        ISOMsg request = buildNetworkMsg("0800", "302", stan);

        logIsoMsg("SENT", "0800 Echo Test", request);

        ISOMsg response = net.sendAndReceive(request, resolveHost(), resolvePort(), timeoutSeconds);

        logIsoMsg("RECEIVED", "0810 Echo Response", response);

        String rc = net.safeGet(response, 39);
        boolean success = "00".equals(rc);
        lastEchoTime = System.currentTimeMillis();

        log.info("[ACQUIRING] Echo — DE39={} success={}", rc, success);

        return McNetworkResult.builder()
                .type("ECHO").stan(stan).responseCode(rc).success(success)
                .message(success ? "Echo OK" : "Echo failed DE39=" + rc)
                .requestHex(ISOUtil.hexString(request.pack()))
                .responseHex(ISOUtil.hexString(response.pack()))
                .build();
    }

    public McNetworkStatus getStatus() {
        return McNetworkStatus.builder()
                .keysExchanged(keysExchanged.get())
                .signedOn(signedOn.get())
                .signOnTime(signOnTime > 0
                        ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(signOnTime))
                        : "Never")
                .lastEchoTime(lastEchoTime > 0
                        ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(lastEchoTime))
                        : "Never")
                .zmkKcv(zmkKcv).zpkKcv(zpkKcv).zakKcv(zakKcv)
                .echoIntervalSeconds(echoIntervalSeconds)
                .mode(mode)
                .host(resolveHost())
                .port(resolvePort())
                .build();
    }

    public boolean isSignedOn()      { return signedOn.get(); }
    public boolean isKeysExchanged() { return keysExchanged.get(); }

    // ── Heartbeat ────────────────────────────────────────────

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                McNetworkResult result = sendEcho();
                if (!result.isSuccess()) {
                    log.warn("[ACQUIRING] Echo failed — retrying sign-on");
                    signedOn.set(false);
                    sendSignOn();
                }
            } catch (Exception e) {
                log.warn("[ACQUIRING] Heartbeat error : {}", e.getMessage());
                signedOn.set(false);
            }
        }, echoIntervalSeconds, echoIntervalSeconds, TimeUnit.SECONDS);
        log.info("[ACQUIRING] Heartbeat started — interval {}s", echoIntervalSeconds);
    }

    // ── Helpers ──────────────────────────────────────────────

    private ISOMsg buildNetworkMsg(String mti, String fc, String stan) throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(net.getPackager());
        msg.setMTI(mti);
        msg.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
        msg.set(11, stan);
        msg.set(70, fc);
        return msg;
    }

    private void logIsoMsg(String direction, String type, ISOMsg msg) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\n┌─────────────────────────────────────────────────\n");
            sb.append(String.format("│ [ACQUIRING] %s — %s\n", direction, type));
            sb.append("├─────────────────────────────────────────────────\n");
            try { sb.append(String.format("│ MTI                   : %s\n", msg.getMTI())); } catch (Exception ignored) {}
            if (msg.hasField(7))  sb.append(String.format("│ DE007 Date/Time       : %s\n", msg.getString(7)));
            if (msg.hasField(11)) sb.append(String.format("│ DE011 STAN            : %s\n", msg.getString(11)));
            if (msg.hasField(39)) sb.append(String.format("│ DE039 Response Code   : %s\n", msg.getString(39)));
            if (msg.hasField(53)) sb.append(String.format("│ DE053 Security Info   : %s...\n", msg.getString(53).substring(0, Math.min(12, msg.getString(53).length()))));
            if (msg.hasField(70)) sb.append(String.format("│ DE070 Network Code    : %s\n", msg.getString(70)));
            sb.append("├─────────────────────────────────────────────────\n");
            sb.append(String.format("│ HEX : %s\n", ISOUtil.hexString(msg.pack())));
            sb.append("└─────────────────────────────────────────────────");
            log.info(sb.toString());
        } catch (Exception e) {
            log.warn("[ACQUIRING] Error logging ISO message : {}", e.getMessage());
        }
    }

    private String resolveHost() { return "mas".equalsIgnoreCase(mode) ? masHost : "127.0.0.1"; }
    private int    resolvePort() { return "mas".equalsIgnoreCase(mode) ? masPort  : 8200; }
}
