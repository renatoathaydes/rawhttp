package com.athaydes.rawhttp.core;

public class InvalidHttpRequest extends RuntimeException {

    private final int lineNumber;

    public InvalidHttpRequest(String message, int lineNumber) {
        super(message);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}
