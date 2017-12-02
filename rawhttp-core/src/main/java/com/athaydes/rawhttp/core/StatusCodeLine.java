package com.athaydes.rawhttp.core;

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

    public int getStatusCode() {
        return statusCode;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return String.join(" ", httpVersion, Integer.toString(statusCode), reason);
    }
}
