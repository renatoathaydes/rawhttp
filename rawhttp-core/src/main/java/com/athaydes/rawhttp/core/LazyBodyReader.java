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
        try {
            return new EagerBodyReader(getBodyType(), inputStream, streamLength);
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

    @Override
    public String toString() {
        return "<lazy body reader>";
    }
}
