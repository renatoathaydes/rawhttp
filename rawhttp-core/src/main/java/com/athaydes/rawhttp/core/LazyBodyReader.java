package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.io.InputStream;

public class LazyBodyReader extends BodyReader {

    private final InputStream inputStream;
    private final Integer streamLength;

    public LazyBodyReader(BodyType bodyType,
                          InputStream inputStream,
                          Integer streamLength) {
        super(bodyType);
        this.inputStream = inputStream;
        this.streamLength = streamLength;
    }

    @Override
    public EagerBodyReader eager() throws IOException {
        return new EagerBodyReader(getBodyType(), inputStream, streamLength);
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
