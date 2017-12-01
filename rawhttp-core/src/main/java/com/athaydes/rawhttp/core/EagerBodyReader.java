package com.athaydes.rawhttp.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class EagerBodyReader extends BodyReader {

    private final byte[] bytes;
    private final InputStream rawInputStream;

    public EagerBodyReader(BodyType bodyType,
                           InputStream inputStream,
                           Integer bodyLength) throws IOException {
        super(bodyType);
        this.rawInputStream = inputStream;
        if (bodyType == BodyType.CONTENT_LENGTH) {
            if (bodyLength == null || bodyLength < 0) {
                throw new IllegalArgumentException("Invalid length (null OR < 0)");
            }
        }
        this.bytes = read(bodyType, inputStream, bodyLength);
    }

    public EagerBodyReader(byte[] bytes) {
        super(BodyType.CONTENT_LENGTH);
        this.bytes = bytes;
        this.rawInputStream = null;
    }

    @Override
    public void close() throws IOException {
        if (rawInputStream != null) {
            rawInputStream.close();
        }
    }

    public static byte[] read(BodyType bodyType,
                              InputStream inputStream,
                              Integer bodyLength) throws IOException {
        switch (bodyType) {
            case CONTENT_LENGTH:
                return readBytesUpToLength(inputStream, bodyLength);
            case CHUNKED:
                throw new UnsupportedOperationException("Chunked response body not supported yet");
            case CLOSE_TERMINATED:
                return readBytesWhileAvailable(inputStream);
            default:
                throw new IllegalStateException("Unknown body type: " + bodyType);
        }
    }

    private static byte[] readBytesUpToLength(InputStream inputStream,
                                              int bodyLength) throws IOException {
        byte[] bytes = new byte[bodyLength];
        int offset = 0;
        while (offset < bodyLength) {
            int actuallyRead = inputStream.read(bytes, offset, bodyLength - offset);
            if (actuallyRead < 0) {
                byte[] shortResult = new byte[offset];
                System.arraycopy(bytes, 0, shortResult, 0, offset);
                bytes = shortResult;
                break;
            }
            offset += actuallyRead;
        }
        return bytes;
    }

    private static byte[] readBytesWhileAvailable(InputStream inputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024 * 16];
        while (true) {
            int actuallyRead = inputStream.read(buffer);
            if (actuallyRead < 0) {
                break;
            }
            out.write(buffer, 0, actuallyRead);
        }
        return out.toByteArray();
    }

    @Override
    public EagerBodyReader eager() {
        return this;
    }

    public byte[] asBytes() {
        return bytes;
    }

    @Override
    public InputStream asStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public String toString() {
        return new String(bytes, StandardCharsets.UTF_8);
    }

}
