package com.athaydes.rawhttp.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class EagerBodyReader implements BodyReader {

    private final byte[] bytes;

    public EagerBodyReader(InputStream inputStream, Integer bodyLength) throws IOException {
        if (bodyLength != null && bodyLength < 0) {
            throw new IllegalArgumentException("Invalid length (< 0)");
        }
        this.bytes = read(inputStream, bodyLength);
        inputStream.close();
    }

    public EagerBodyReader(byte[] bytes) {
        this.bytes = bytes;
    }

    private static byte[] read(InputStream inputStream, Integer bodyLength) throws IOException {
        if (bodyLength != null) {
            return readBytesUpToLength(inputStream, bodyLength);
        } else {
            return readBytesWhileAvailable(inputStream);
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
    public BodyReader eager() {
        return this;
    }

    @Override
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
