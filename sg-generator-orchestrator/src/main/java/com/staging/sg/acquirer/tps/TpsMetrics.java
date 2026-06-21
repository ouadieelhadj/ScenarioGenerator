package com.staging.sg.acquirer.tps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TpsMetrics {

    private final AtomicInteger txTotal    = new AtomicInteger(0);
    private final AtomicInteger txApproved = new AtomicInteger(0);
    private final AtomicInteger txDeclined = new AtomicInteger(0);
    private final AtomicLong    totalMs    = new AtomicLong(0);
    private final AtomicLong    minMs      = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong    maxMs      = new AtomicLong(0);

    private final List<TxRecord>   txRecords     = Collections.synchronizedList(new ArrayList<>());
    private final List<Long>       responseTimes = Collections.synchronizedList(new ArrayList<>());
    private final List<Double>     tpsHistory    = Collections.synchronizedList(new ArrayList<>());
    private final List<StepRecord> stepRecords   = Collections.synchronizedList(new ArrayList<>());

    private volatile int    currentTps  = 0;
    private volatile int    currentStep = 0;
    private volatile String status      = "RUNNING";
    private final long      startTime   = System.currentTimeMillis();

    // ── TxRecord ─────────────────────────────────────────────
    public static class TxRecord {
        public final String  panMasked;
        public final String  de039;
        public final String  de038AuthCode;
        public final boolean approved;
        public final long    durationMs;
        public final String  requestHex;
        public final String  responseHex;
        public final long    timestamp;

        public TxRecord(String panMasked, String de039, String de038AuthCode,
                        boolean approved, long durationMs,
                        String requestHex, String responseHex) {
            this.panMasked     = panMasked;
            this.de039         = de039;
            this.de038AuthCode = de038AuthCode;
            this.approved      = approved;
            this.durationMs    = durationMs;
            this.requestHex    = requestHex;
            this.responseHex   = responseHex;
            this.timestamp     = System.currentTimeMillis();
        }
    }

    // ── StepRecord ───────────────────────────────────────────
    public static class StepRecord {
        public final int stepOrder;
        public final int tpsTarget;
        public final int startSeconds;
        public final int endSeconds;
        public int    txSent     = 0;
        public int    txApproved = 0;
        public double avgTps     = 0;
        public double avgMs      = 0;

        public StepRecord(int stepOrder, int tpsTarget,
                          int startSeconds, int endSeconds) {
            this.stepOrder    = stepOrder;
            this.tpsTarget    = tpsTarget;
            this.startSeconds = startSeconds;
            this.endSeconds   = endSeconds;
        }
    }

    // ── Record transaction ────────────────────────────────────
    public void record(String panMasked, String de039, String de038AuthCode,
                       boolean approved, long durationMs,
                       String requestHex, String responseHex) {
        txTotal.incrementAndGet();
        if (approved) txApproved.incrementAndGet();
        else          txDeclined.incrementAndGet();

        totalMs.addAndGet(durationMs);
        responseTimes.add(durationMs);

        if (durationMs < minMs.get()) minMs.set(durationMs);
        if (durationMs > maxMs.get()) maxMs.set(durationMs);

        txRecords.add(new TxRecord(panMasked, de039, de038AuthCode,
                approved, durationMs, requestHex, responseHex));
    }

    public void record(boolean approved, long durationMs) {
        record(null, approved ? "00" : "05", null,
               approved, durationMs, null, null);
    }

    public void recordTps(double tps)          { tpsHistory.add(tps); }
    public void addStepRecord(StepRecord step) { stepRecords.add(step); }

    // ── Métriques calculées ──────────────────────────────────
    public double getAvgResponseMs() {
        int total = txTotal.get();
        return total > 0 ? (double) totalMs.get() / total : 0;
    }

    public long getMinResponseMs() {
        long v = minMs.get();
        return v == Long.MAX_VALUE ? 0 : v;
    }

    public long   getMaxResponseMs() { return maxMs.get(); }
    public double getP95ResponseMs() { return getPercentile(95); }
    public double getP99ResponseMs() { return getPercentile(99); }

    public double getAvgTps() {
        if (tpsHistory.isEmpty()) return 0;
        return tpsHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    public double getApprovalRate() {
        int total = txTotal.get();
        return total > 0 ? (double) txApproved.get() / total * 100 : 0;
    }

    public double getElapsedSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000.0;
    }

    private double getPercentile(int pct) {
        if (responseTimes.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(responseTimes);
        Collections.sort(sorted);
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, idx));
    }

    // ── Getters ──────────────────────────────────────────────
    public int              getTxTotal()       { return txTotal.get(); }
    public int              getTxApproved()    { return txApproved.get(); }
    public int              getTxDeclined()    { return txDeclined.get(); }
    public int              getCurrentTps()    { return currentTps; }
    public int              getCurrentStep()   { return currentStep; }
    public String           getStatus()        { return status; }
    public long             getStartTime()     { return startTime; }
    public List<Long>       getResponseTimes() { return responseTimes; }
    public List<Double>     getTpsHistory()    { return tpsHistory; }
    public List<TxRecord>   getTxRecords()     { return txRecords; }
    public List<StepRecord> getStepRecords()   { return stepRecords; }

    // ── Setters ──────────────────────────────────────────────
    public void setCurrentTps(int v)  { this.currentTps = v; }
    public void setCurrentStep(int v) { this.currentStep = v; }
    public void setStatus(String v)   { this.status = v; }
}
