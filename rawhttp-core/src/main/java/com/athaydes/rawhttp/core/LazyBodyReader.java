package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.io.InputStream;

public class LazyBodyReader implements BodyReader {

    private final InputStream inputStream;
    private final Integer streamLength;

    public LazyBodyReader(InputStream inputStream,
                          Integer streamLength) {
        this.inputStream = inputStream;
        this.streamLength = streamLength;
    }

    @Override
    public EagerBodyReader eager() throws IOException {
        return new EagerBodyReader(inputStream, streamLength);
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
