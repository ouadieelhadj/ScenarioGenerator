package com.staging.sg.acquirer.tps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TPS Metrics — real-time metrics for an execution.
 */
public class TpsMetrics {

    private final AtomicInteger txTotal    = new AtomicInteger(0);
    private final AtomicInteger txApproved = new AtomicInteger(0);
    private final AtomicInteger txDeclined = new AtomicInteger(0);
    private final AtomicLong    totalMs    = new AtomicLong(0);
    private final AtomicLong    minMs      = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong    maxMs      = new AtomicLong(0);

    private final List<Long>    responseTimes = Collections.synchronizedList(new ArrayList<>());
    private final List<Double>  tpsHistory    = Collections.synchronizedList(new ArrayList<>());

    private volatile int    currentTps   = 0;
    private volatile int    currentStep  = 0;
    private volatile String status       = "RUNNING";
    private final long      startTime    = System.currentTimeMillis();

    public void record(boolean approved, long durationMs) {
        txTotal.incrementAndGet();
        if (approved) txApproved.incrementAndGet();
        else          txDeclined.incrementAndGet();

        totalMs.addAndGet(durationMs);
        responseTimes.add(durationMs);

        if (durationMs < minMs.get()) minMs.set(durationMs);
        if (durationMs > maxMs.get()) maxMs.set(durationMs);
    }

    public void recordTps(double tps) { tpsHistory.add(tps); }

    // ── Computed metrics ─────────────────────────────────────

    public double getAvgResponseMs() {
        int total = txTotal.get();
        return total > 0 ? (double) totalMs.get() / total : 0;
    }

    public long getMinResponseMs() {
        long v = minMs.get();
        return v == Long.MAX_VALUE ? 0 : v;
    }

    public long getMaxResponseMs() { return maxMs.get(); }

    public double getP95ResponseMs() { return getPercentile(95); }
    public double getP99ResponseMs() { return getPercentile(99); }

    public double getAvgTps() {
        if (tpsHistory.isEmpty()) return 0;
        return tpsHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0);
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
    public int    getTxTotal()       { return txTotal.get(); }
    public int    getTxApproved()    { return txApproved.get(); }
    public int    getTxDeclined()    { return txDeclined.get(); }
    public int    getCurrentTps()    { return currentTps; }
    public int    getCurrentStep()   { return currentStep; }
    public String getStatus()        { return status; }
    public long   getStartTime()     { return startTime; }
    public List<Long>   getResponseTimes() { return responseTimes; }
    public List<Double> getTpsHistory()    { return tpsHistory; }

    // ── Setters ──────────────────────────────────────────────
    public void setCurrentTps(int v)   { this.currentTps = v; }
    public void setCurrentStep(int v)  { this.currentStep = v; }
    public void setStatus(String v)    { this.status = v; }
}
