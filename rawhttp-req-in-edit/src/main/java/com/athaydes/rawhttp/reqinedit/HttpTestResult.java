package com.athaydes.rawhttp.reqinedit;

import org.jetbrains.annotations.Nullable;

/**
 * Result of running a single test for a HTTP response.
 *
 * @see ReqInEditEntry
 */
public final class HttpTestResult {
    private final String name;
    private final long startTime;
    private final long endTime;

    @Nullable
    private final String error;

    public HttpTestResult(String name, long startTime, long endTime, @Nullable String error) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.error = error;
    }

    /**
     * @return the name of the test
     */
    public String getName() {
        return name;
    }

    /**
     * @return time the test started
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * @return time the test ended
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * @return error if the test failed, null otherwise.
     */
    @Nullable
    public String getError() {
        return error;
    }

    /**
     * @return whether the test has succeeded
     */
    public boolean isSuccess() {
        return error == null;
    }
}
