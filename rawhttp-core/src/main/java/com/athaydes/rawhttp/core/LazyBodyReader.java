package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.io.InputStream;

public class LazyBodyReader implements BodyReader {

    private final InputStream inputStream;
    private final Long streamLength;

    public LazyBodyReader(InputStream inputStream,
                          Long streamLength) {
        this.inputStream = inputStream;
        this.streamLength = streamLength;
    }

    @Override
    public EagerBodyReader eager() throws IOException {
        return new EagerBodyReader(inputStream, intLength());
    }

    private Integer intLength() {
        if (streamLength == null) {
            return null;
        } else {
            return Math.toIntExact(streamLength);
        }
    }

    @Override
    public byte[] asBytes() throws IOException {
        return eager().asBytes();
    }

    @Override
    public InputStream asStream() {
        return inputStream;
    }

    @Override
    public String toString() {
        return "<lazy body reader>";
    }
}
