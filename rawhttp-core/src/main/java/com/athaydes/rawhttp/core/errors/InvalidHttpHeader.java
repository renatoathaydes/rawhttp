package com.athaydes.rawhttp.core.errors;

public class InvalidHttpHeader extends RuntimeException {

    private final int lineNumber;

    public InvalidHttpHeader(String message, int lineNumber) {
        super(message);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public String toString() {
        return "InvalidHttpHeader{" +
                "message='" + getMessage() + "', " +
                "lineNumber=" + lineNumber +
                '}';
    }

}
