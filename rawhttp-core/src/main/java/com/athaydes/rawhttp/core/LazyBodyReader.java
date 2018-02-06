package com.athaydes.rawhttp.core;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Lazy implementation of {@link BodyReader}.
 * <p>
 * Instances of this class are "live", i.e. they should only be used while the HTTP connection is live.
 *
 * @see #eager()
 */
public class LazyBodyReader extends BodyReader {

    private final InputStream inputStream;

    @Nullable
    private final Long streamLength;

    private final boolean allowNewLineWithoutReturn;

    public LazyBodyReader(BodyType bodyType,
                          InputStream inputStream,
                          @Nullable Long streamLength,
                          boolean allowNewLineWithoutReturn) {
        super(bodyType);
        this.inputStream = inputStream;
        this.streamLength = streamLength;
        this.allowNewLineWithoutReturn = allowNewLineWithoutReturn;
    }

    @Override
    public EagerBodyReader eager() throws IOException {
        try {
            return new EagerBodyReader(getBodyType(), inputStream, streamLength, allowNewLineWithoutReturn);
        } catch (IOException e) {
            // error while trying to read message body, we cannot keep the connection alive
            try {
                inputStream.close();
            } catch (IOException e2) {
                // ignore
            }

            throw e;
        }
    }

    @Override
    public InputStream asStream() {
        return inputStream;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }

    /**
     * @return the {@literal "<lazy body reader>"} String.
     * To obtain the String representation of a HTTP message's body, first call
     * {@link #eager()}, then {@link EagerBodyReader#toString()}.
     */
    @Override
    public String toString() {
        return "<lazy body reader>";
    }
}
