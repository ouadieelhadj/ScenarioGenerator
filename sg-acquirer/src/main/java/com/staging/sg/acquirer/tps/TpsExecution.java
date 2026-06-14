package com.staging.sg.acquirer.tps;

import java.util.concurrent.Future;

/**
 * Represents a running TPS execution.
 */
public class TpsExecution {

    private final Long        executionId;
    private final Long        testId;
    private final TpsMetrics  metrics;
    private final Future<?>   future;

    public TpsExecution(Long executionId, Long testId,
                        TpsMetrics metrics, Future<?> future) {
        this.executionId = executionId;
        this.testId      = testId;
        this.metrics     = metrics;
        this.future      = future;
    }

    public void stop() {
        if (future != null && !future.isDone())
            future.cancel(true);
        metrics.setStatus("STOPPED");
    }

    public boolean isRunning() {
        return future != null && !future.isDone();
    }

    public Long       getExecutionId() { return executionId; }
    public Long       getTestId()      { return testId; }
    public TpsMetrics getMetrics()     { return metrics; }
    public Future<?>  getFuture()      { return future; }
}
