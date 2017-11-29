package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.io.InputStream;

public abstract class BodyReader {

    public enum BodyType {
        CONTENT_LENGTH,
        CHUNKED,
        CLOSE_TERMINATED
    }

    private final BodyType bodyType;

    public BodyReader(BodyType bodyType) {
        this.bodyType = bodyType;
    }

    public BodyType getBodyType() {
        return bodyType;
    }

    public abstract EagerBodyReader eager() throws IOException;

    public abstract InputStream asStream();

}
