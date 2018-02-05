package com.athaydes.rawhttp.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * HTTP message body reader.
 */
public abstract class BodyReader implements Closeable {

    /**
     * Type of HTTP message body.
     */
    public enum BodyType {
        CONTENT_LENGTH,
        CHUNKED,
        CLOSE_TERMINATED
    }

    private final BodyType bodyType;

    public BodyReader(BodyType bodyType) {
        this.bodyType = bodyType;
    }

    /**
     * @return the type of the body of a HTTP message.
     */
    public BodyType getBodyType() {
        return bodyType;
    }

    /**
     * @return resolve the body of the HTTP message eagerly.
     * In effect, this method causes the body of the HTTP message to be consumed.
     * @throws IOException if an error occurs while reading the body
     */
    public abstract EagerBodyReader eager() throws IOException;

    /**
     * @return the stream which may produce the HTTP message body.
     * Notice that the stream may be closed if this {@link BodyReader} is closed.
     */
    public abstract InputStream asStream();

}
