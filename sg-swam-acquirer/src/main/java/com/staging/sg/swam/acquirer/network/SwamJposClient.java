package com.staging.sg.swam.acquirer.network;

import com.staging.sg.common.entity.NetworkRef;
import com.staging.sg.common.iso.SwamPackager;
import com.staging.sg.common.iso.SwamLengthChannel;
import com.staging.sg.common.repository.NetworkRepository;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Client jPOS cote Membre/banque (SWAM acquirer).
 * Etablit et maintient la connexion PERMANENTE vers le switch (host/port lus
 * depuis networks). Correlation requete/reponse par STAN (DE11).
 * Conforme HPS sec.4 : c'est le Membre qui se connecte.
 */
@Component
public class SwamJposClient {

    private static final Logger log = LoggerFactory.getLogger(SwamJposClient.class);
    private static final String DEFAULT_HOST = "localhost";
    private static final int    DEFAULT_PORT = 8510;

    private final NetworkRepository networkRepository;
    private final SwamPackager packager = new SwamPackager();
    private SwamLengthChannel channel;

    private final ConcurrentHashMap<String, ISOMsg> responses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();
    private Thread receiver;
    private volatile boolean running = false;

    public SwamJposClient(NetworkRepository networkRepository) {
        this.networkRepository = networkRepository;
    }

    private String host() {
        try {
            Optional<NetworkRef> s = networkRepository.findByCode("SWAM");
            if (s.isPresent() && s.get().getIssuerHost() != null) return s.get().getIssuerHost();
        } catch (Exception e) { log.warn("[SWAM-CLI] host base KO: {}", e.getMessage()); }
        return DEFAULT_HOST;
    }
    private int port() {
        try {
            Optional<NetworkRef> s = networkRepository.findByCode("SWAM");
            if (s.isPresent() && s.get().getIssuerIsoPort() != null) return s.get().getIssuerIsoPort();
        } catch (Exception e) { log.warn("[SWAM-CLI] port base KO: {}", e.getMessage()); }
        return DEFAULT_PORT;
    }

    /** Etablit la connexion permanente si pas deja connectee. */
    public synchronized void connect() throws Exception {
        if (channel != null && channel.isConnected()) return;
        String h = host(); int p = port();
        channel = new SwamLengthChannel(packager);
        channel.setHost(h);
        channel.setPort(p);
        channel.connect();
        running = true;
        startReceiver();
        log.info("[SWAM-CLI] Connecte au switch {}:{}", h, p);
    }

    private void startReceiver() {
        receiver = new Thread(() -> {
            while (running && channel != null && channel.isConnected()) {
                try {
                    ISOMsg resp = channel.receive();
                    String stan = resp.hasField(11) ? resp.getString(11) : null;
                    if (stan != null) {
                        responses.put(stan, resp);
                        CountDownLatch l = latches.get(stan);
                        if (l != null) l.countDown();
                    }
                } catch (Exception e) {
                    if (running) log.warn("[SWAM-CLI] receiver: {}", e.getMessage());
                    break;
                }
            }
        }, "swam-client-receiver");
        receiver.setDaemon(true);
        receiver.start();
    }

    /** Envoie un message et attend la reponse correlee par STAN (timeout en secondes). */
    public ISOMsg sendAndWait(ISOMsg req, int timeoutSeconds) throws Exception {
        connect();
        String stan = req.getString(11);
        CountDownLatch latch = new CountDownLatch(1);
        latches.put(stan, latch);
        channel.send(req);
        boolean ok = latch.await(timeoutSeconds, TimeUnit.SECONDS);
        latches.remove(stan);
        ISOMsg resp = responses.remove(stan);
        if (!ok || resp == null) throw new RuntimeException("Timeout SWAM (STAN=" + stan + ")");
        return resp;
    }

    public boolean isConnected() { return channel != null && channel.isConnected(); }
    public SwamPackager getPackager() { return packager; }

    @PreDestroy
    public void disconnect() {
        running = false;
        try { if (channel != null) channel.disconnect(); } catch (Exception ignore) {}
        log.info("[SWAM-CLI] Deconnecte");
    }
}
