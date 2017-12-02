package com.athaydes.rawhttp.core;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class EagerBodyReader extends BodyReader {

    private final byte[] bytes;

    @Nullable
    private final InputStream rawInputStream;

    public EagerBodyReader(BodyType bodyType,
                           @Nonnull InputStream inputStream,
                           @Nullable Long bodyLength) throws IOException {
        super(bodyType);
        this.rawInputStream = inputStream;
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
                              @Nullable Long bodyLength) throws IOException {
        switch (bodyType) {
            case CONTENT_LENGTH:
                if (bodyLength == null || bodyLength < 0) {
                    throw new IllegalArgumentException("Invalid length (null OR < 0)");
                }
                return readBytesUpToLength(inputStream, Math.toIntExact(bodyLength));
            case CHUNKED:
                return readChunkedBody(inputStream);
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
        byte[] buffer = new byte[4096];
        while (true) {
            int actuallyRead = inputStream.read(buffer);
            if (actuallyRead < 0) {
                break;
            }
            out.write(buffer, 0, actuallyRead);
        }
        return out.toByteArray();
    }

    private static byte[] readChunkedBody(InputStream inputStream) throws IOException {
        throw new UnsupportedOperationException("Chunked body not supported yet");
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

    public String asString(Charset charset) {
        return new String(bytes, charset);
    }

    @Override
    public String toString() {
        return asString(StandardCharsets.UTF_8);
    }

}
