package com.staging.sg.dmas.acquirer.network;

import com.staging.sg.dmas.acquirer.api.LoadTestRequest;
import com.staging.sg.common.iso.DmasNetworkUtil;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Moteur de test de charge de la connexion permanente jPOS.
 * Asynchrone : run() lance en arriere-plan, le status est interrogeable.
 * La lecture des reponses reste sur le thread jPOS (correlation par STAN dans DmasJposServer).
 */
@Service
public class LoadTestService {

    private static final Logger log = LoggerFactory.getLogger(LoadTestService.class);

    private final McDmasAuthorization auth;
    private final DmasJposServer jposServer;
    private final DmasNetworkUtil net;
    private final java.util.concurrent.Semaphore slots;

    // STAN unique en charge (cycle 6 chiffres, base aleatoire pour limiter collisions inter-flux)
    private final Map<String, LoadTestRun> runs = new ConcurrentHashMap<>();

    public LoadTestService(McDmasAuthorization auth, DmasJposServer jposServer, DmasNetworkUtil net,
                           @Value("${dmas.loadtest.max-concurrent-tests:1}") int maxConcurrent) {
        this.auth = auth;
        this.jposServer = jposServer;
        this.net = net;
        this.slots = new java.util.concurrent.Semaphore(Math.max(1, maxConcurrent));
    }

    /** Detail d'une transaction (pour reporting cote orchestrator). */
    public static class TxDetail {
        public String  pan;
        public String  de39;
        public boolean approved;
        public long    durationMs;
        public boolean error;
        public String  requestHex;
        public String  responseHex;
    }

    /** Etat d'un run de load test. */
    public static class LoadTestRun {
        public String status = "RUNNING";      // RUNNING | COMPLETED | ERROR
        public final AtomicInteger sent     = new AtomicInteger();
        public final AtomicInteger approved = new AtomicInteger();
        public final AtomicInteger declined = new AtomicInteger();
        public final AtomicInteger errors   = new AtomicInteger();
        public final Map<String,Integer> de39Counts = new ConcurrentHashMap<>();
        public final List<TxDetail> details = Collections.synchronizedList(new ArrayList<>());
        public long startedAt;
        public long endedAt;
        public Integer plannedCount;
    }

    private String nextStan() {
        return net.generateStan();  // compteur atomique partage (unicite globale)
    }

    /** Lance un load test asynchrone. Retourne le loadTestId. */
    public String start(LoadTestRequest req) {
        if (!slots.tryAcquire()) {
            return null;  // limite de tests simultanes atteinte
        }
        String id = "lt-" + System.currentTimeMillis();
        LoadTestRun run = new LoadTestRun();
        run.startedAt = System.currentTimeMillis();
        runs.put(id, run);

        Thread supervisor = new Thread(() -> execute(id, req, run), "loadtest-" + id);
        supervisor.setDaemon(true);
        supervisor.start();
        return id;
    }

    public LoadTestRun status(String id) {
        return runs.get(id);
    }

    /** Thread superviseur : pilote l'emission, attend la completion, calcule la fin. */
    private void execute(String id, LoadTestRequest req, LoadTestRun run) {
        int concurrency = req.concurrency != null ? req.concurrency : 50;
        int timeout     = req.timeoutSeconds != null ? req.timeoutSeconds : 10;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            if (req.count != null && req.count > 0) {
                // ---- Mode A : nombre fixe ----
                run.plannedCount = req.count;
                long intervalMs = (req.targetTps != null && req.targetTps > 0) ? 1000L / req.targetTps : 0L;
                for (int i = 0; i < req.count; i++) {
                    submitOne(pool, req, run, timeout);
                    if (intervalMs > 0) Thread.sleep(intervalMs);
                }
            } else if (req.durationSeconds != null && req.durationSeconds > 0) {
                // ---- Mode B : duree ----
                long intervalMs = (req.targetTps != null && req.targetTps > 0) ? 1000L / req.targetTps : 0L;
                long end = System.currentTimeMillis() + req.durationSeconds * 1000L;
                while (System.currentTimeMillis() < end) {
                    submitOne(pool, req, run, timeout);
                    if (intervalMs > 0) Thread.sleep(intervalMs);
                }
            } else {
                log.warn("[LOADTEST] ni count ni durationSeconds fournis");
            }
            // Supervision : plus d'emission, on attend la fin des tx en vol
            pool.shutdown();
            long globalTimeout = (long) timeout + (req.durationSeconds != null ? req.durationSeconds : 0) + 30;
            pool.awaitTermination(globalTimeout, TimeUnit.SECONDS);
            run.status = "COMPLETED";
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            run.status = "ERROR";
        } catch (Exception e) {
            run.status = "ERROR";
            log.error("[LOADTEST] {} erreur : {}", id, e.getMessage());
        } finally {
            pool.shutdownNow();
            run.endedAt = System.currentTimeMillis();
            slots.release();
            log.info("[LOADTEST] {} {} : sent={} approved={} declined={} errors={} dureeMs={}",
                    id, run.status, run.sent.get(), run.approved.get(),
                    run.declined.get(), run.errors.get(), run.endedAt - run.startedAt);
        }
    }

    private void submitOne(ExecutorService pool, LoadTestRequest req, LoadTestRun run, int timeout) {
        final String stan = nextStan();
        // v1.1.0 : tirage d'une carte au hasard dans le pool si fourni, sinon PAN fixe
        final String txPan;
        final String txPin;
        if (req.cards != null && !req.cards.isEmpty()) {
            LoadTestRequest.CardEntry c = req.cards.get(
                    java.util.concurrent.ThreadLocalRandom.current().nextInt(req.cards.size()));
            txPan = c.pan;
            txPin = c.pin;
        } else {
            txPan = req.pan;
            txPin = null;
        }
        pool.submit(() -> {
            TxDetail d = new TxDetail();
            d.pan = txPan;
            long t0 = System.currentTimeMillis();
            try {
                ISOMsg msg = req.withPin
                        ? auth.buildAuth0100WithPin(txPan, txPin, req.amount, req.entryMode, stan)
                        : auth.buildAuth0100(txPan, req.amount, req.entryMode, stan);
                d.requestHex = org.jpos.iso.ISOUtil.hexString(msg.pack());
                ISOMsg resp = jposServer.pushAndWait(msg, timeout);
                d.durationMs = System.currentTimeMillis() - t0;
                String rc = resp.hasField(39) ? resp.getString(39) : "??";
                d.de39 = rc;
                d.approved = "00".equals(rc);
                d.responseHex = org.jpos.iso.ISOUtil.hexString(resp.pack());
                if (d.approved) run.approved.incrementAndGet(); else run.declined.incrementAndGet();
                run.de39Counts.merge(rc, 1, Integer::sum);
            } catch (Exception e) {
                d.durationMs = System.currentTimeMillis() - t0;
                d.error = true;
                d.de39 = "ERR";
                run.errors.incrementAndGet();
                run.de39Counts.merge("ERR", 1, Integer::sum);
            } finally {
                run.sent.incrementAndGet();
                run.details.add(d);
            }
        });
    }
}
