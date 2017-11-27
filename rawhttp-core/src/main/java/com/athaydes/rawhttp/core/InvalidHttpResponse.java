package com.athaydes.rawhttp.core;

public class InvalidHttpResponse extends RuntimeException {

    private final int lineNumber;

    public InvalidHttpResponse(String message, int lineNumber) {
        super(message);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public String toString() {
        return "InvalidHttpResponse{" +
                "message='" + getMessage() + "', " +
                "lineNumber=" + lineNumber +
                '}';
    }
}
