package com.athaydes.rawhttp.duplex;

import rawhttp.core.client.RawHttpClient;

import java.time.Duration;

public class RawHttpDuplexOptions {

    private final RawHttpClient<?> client;
    private final Duration pingPeriod;

    public RawHttpDuplexOptions(RawHttpClient<?> client, Duration pingPeriod) {
        this.client = client;
        this.pingPeriod = pingPeriod;
    }

    public RawHttpClient<?> getClient() {
        return client;
    }

    public Duration getPingPeriod() {
        return pingPeriod;
    }
}
