package com.athaydes.rawhttp.core;

import com.athaydes.rawhttp.core.ChunkedBody.Chunk;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public class EagerBodyReader extends BodyReader {

    private final byte[] bytes;

    @Nullable
    private final InputStream rawInputStream;

    @Nullable
    private final ChunkedBody chunkedBody;

    public EagerBodyReader(BodyType bodyType,
                           @Nonnull InputStream inputStream,
                           @Nullable Long bodyLength) throws IOException {
        super(bodyType);
        this.rawInputStream = inputStream;

        switch (bodyType) {
            case CONTENT_LENGTH:
                if (bodyLength == null || bodyLength < 0) {
                    throw new IllegalArgumentException("Invalid length (null OR < 0)");
                }
                this.bytes = readBytesUpToLength(inputStream, Math.toIntExact(bodyLength));
                this.chunkedBody = null;
                break;
            case CHUNKED:
                this.chunkedBody = readChunkedBody(inputStream);
                this.bytes = chunkedBody.getData();
                break;
            case CLOSE_TERMINATED:
                this.bytes = readBytesWhileAvailable(inputStream);
                this.chunkedBody = null;
                break;
            default:
                throw new IllegalStateException("Unknown body type: " + bodyType);
        }
    }

    public EagerBodyReader(byte[] bytes) {
        super(BodyType.CONTENT_LENGTH);
        this.bytes = bytes;
        this.rawInputStream = null;
        this.chunkedBody = null;
    }

    @Override
    public void close() throws IOException {
        if (rawInputStream != null) {
            rawInputStream.close();
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

    private static ChunkedBody readChunkedBody(InputStream inputStream) throws IOException {
        List<Chunk> chunks = new ArrayList<>();
        int chunkSize = 1;
        while (chunkSize > 0) {
            chunkSize = inputStream.read();
            if (chunkSize < 0) {
                throw new IllegalStateException("unexpected EOF, could not read chunked body");
            }
            Chunk chunk = readChunk(inputStream, chunkSize);
            chunks.add(chunk);
        }

        BiFunction<String, Integer, RuntimeException> errorCreator =
                (msg, lineNumber) -> new IllegalStateException(msg);

        List<String> trailer = RawHttp.parseMetadataLines(inputStream, errorCreator);
        Map<String, Collection<String>> trailerHeaders = RawHttp.parseHeaders(trailer, errorCreator);

        return new ChunkedBody(chunks, trailerHeaders);
    }

    private static Chunk readChunk(InputStream inputStream, int chunkSize) throws IOException {
        StringBuilder currentName = new StringBuilder();
        StringBuilder currentValue = new StringBuilder();
        boolean parsingValue = false;
        Map<String, Collection<String>> extensions = new HashMap<>(2);
        int b;
        while ((b = inputStream.read()) >= 0) {
            if (b == '\r') {
                // expect new-line
                int next = inputStream.read();
                if (next < 0 || next == '\n') {
                    break;
                } else {
                    inputStream.close();
                    throw new IllegalStateException("Illegal character after return in chunked body");
                }
            } else if (b == '\n') {
                // unexpected, but let's accept new-line without returns
                break;
            } else if (b == '=') {
                if (!parsingValue) {
                    parsingValue = true;
                } else {
                    currentValue.append((char) b);
                }
            } else if (b == ';') {
                extensions.computeIfAbsent(currentName.toString().trim(),
                        (ignore) -> new ArrayList<>(3)).add(currentValue.toString().trim());
                currentName = new StringBuilder();
                currentValue = new StringBuilder();
                parsingValue = false;
            } else {
                if (parsingValue) {
                    currentValue.append((char) b);
                } else {
                    currentName.append((char) b);
                }
            }
        }

        if (currentName.length() > 0 || currentValue.length() > 0) {
            extensions.computeIfAbsent(currentName.toString().trim(),
                    (ignore) -> new ArrayList<>(3)).add(currentValue.toString().trim());
        }

        byte[] data = new byte[chunkSize];

        if (chunkSize > 0) {
            int bytesRead;
            int totalBytesRead = 0;
            while ((bytesRead = inputStream.read(data, totalBytesRead, chunkSize - totalBytesRead)) >= 0) {
                totalBytesRead += bytesRead;
                if (totalBytesRead == chunkSize) {
                    break;
                }
            }

            if (totalBytesRead < chunkSize) {
                throw new IllegalStateException("Unexpected EOF while reading chunk data");
            }
        }

        return new Chunk(extensions, data);
    }

    @Override
    public EagerBodyReader eager() {
        return this;
    }

    public byte[] asBytes() {
        return bytes;
    }

    public Optional<ChunkedBody> asChunkedBody() {
        return Optional.ofNullable(chunkedBody);
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
