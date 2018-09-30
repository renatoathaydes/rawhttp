package com.athaydes.rawhttp.duplex;

import rawhttp.core.client.RawHttpClient;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class RawHttpDuplexOptions {

    private final RawHttpClient<?> client;
    private final Duration pingPeriod;
    private final ScheduledExecutorService pingScheduler;

    public RawHttpDuplexOptions(RawHttpClient<?> client, Duration pingPeriod) {
        this(client, pingPeriod, Executors.newSingleThreadScheduledExecutor());
    }

    public RawHttpDuplexOptions(RawHttpClient<?> client, Duration pingPeriod,
                                ScheduledExecutorService pingScheduler) {
        this.client = client;
        this.pingPeriod = pingPeriod;
        this.pingScheduler = pingScheduler;
    }

    public RawHttpClient<?> getClient() {
        return client;
    }

    public Duration getPingPeriod() {
        return pingPeriod;
    }

    public ScheduledExecutorService getPingScheduler() {
        return pingScheduler;
    }
}
