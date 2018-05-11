package com.athaydes.rawhttp.core;

/**
 * A {@link RawHttpResponse}'s start-line.
 */
public class StatusLine implements StartLine {

    private final HttpVersion httpVersion;
    private final int statusCode;
    private final String reason;

    public StatusLine(HttpVersion httpVersion, int statusCode, String reason) {
        this.httpVersion = httpVersion;
        this.statusCode = statusCode;
        this.reason = reason;
    }

    @Override
    public HttpVersion getHttpVersion() {
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
        return String.join(" ", httpVersion.toString(), Integer.toString(statusCode), reason).trim();
    }
}
