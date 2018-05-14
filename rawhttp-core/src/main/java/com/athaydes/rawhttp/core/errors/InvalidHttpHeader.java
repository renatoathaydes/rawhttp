package com.athaydes.rawhttp.core.errors;

public class InvalidHttpHeader extends RuntimeException {

    public InvalidHttpHeader(String message) {
        super(message);
    }

    @Override
    public String toString() {
        return "InvalidHttpHeader{" +
                "message='" + getMessage() + "'}";
    }

}
