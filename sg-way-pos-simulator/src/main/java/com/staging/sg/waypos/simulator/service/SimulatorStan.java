package com.staging.sg.waypos.simulator.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SimulatorStan {
    private final AtomicInteger sequence = new AtomicInteger(
            Math.floorMod((int) System.currentTimeMillis(), 999_999));

    public String next() {
        return "%06d".formatted(sequence.updateAndGet(value ->
                value >= 999_999 ? 1 : value + 1));
    }
}
