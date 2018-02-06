package com.athaydes.rawhttp.core;

/**
 * A {@link RawHttpResponse}'s start-line.
 */
public class StatusCodeLine implements StartLine {

    private final String httpVersion;
    private final int statusCode;
    private final String reason;

    public StatusCodeLine(String httpVersion, int statusCode, String reason) {
        this.httpVersion = httpVersion;
        this.statusCode = statusCode;
        this.reason = reason;
    }

    @Override
    public String getHttpVersion() {
        return httpVersion;
    }

    /**
     * @return the status code in this status-code line.
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * @return the reason phrase in this status-code line.
     */
    public String getReason() {
        return reason;
    }

    /**
     * @return the start-line for this status-code line.
     */
    @Override
    public String toString() {
        return String.join(" ", httpVersion, Integer.toString(statusCode), reason);
    }
}
