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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static com.athaydes.rawhttp.core.RawHttpHeaders.Builder.emptyRawHttpHeaders;

/**
 * An eager implementation of {@link BodyReader}.
 * <p>
 * Because this implementation eagerly consumes the HTTP message, it is not considered "live"
 * (i.e. it can be stored after the HTTP connection is closed).
 */
public class EagerBodyReader extends BodyReader {

    private final byte[] bytes;

    @Nullable
    private final InputStream rawInputStream;

    @Nullable
    private final ChunkedBody chunkedBody;

    public EagerBodyReader(BodyType bodyType,
                           @Nonnull InputStream inputStream,
                           @Nullable Long bodyLength,
                           boolean allowNewLineWithoutReturn) throws IOException {
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
                this.chunkedBody = readChunkedBody(inputStream, allowNewLineWithoutReturn);
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

    /**
     * Create an instance of this class from the given bytes.
     * <p>
     * The bytes are assumed to be the decoded HTTP message's body.
     *
     * @param bytes plain HTTP message's body
     */
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

    private static ChunkedBody readChunkedBody(InputStream inputStream,
                                               boolean allowNewLineWithoutReturn) throws IOException {
        List<Chunk> chunks = new ArrayList<>();
        int chunkSize = 1;
        while (chunkSize > 0) {
            AtomicBoolean hasExtensions = new AtomicBoolean(false);
            chunkSize = readChunkSize(inputStream, allowNewLineWithoutReturn, hasExtensions);
            if (chunkSize < 0) {
                throw new IllegalStateException("unexpected EOF, could not read chunked body");
            }
            Chunk chunk = readChunk(inputStream, chunkSize, allowNewLineWithoutReturn, hasExtensions);
            chunks.add(chunk);
        }

        BiFunction<String, Integer, RuntimeException> errorCreator =
                (msg, lineNumber) -> new IllegalStateException(msg + " (parsing chunked body headers)");

        List<String> trailer = RawHttp.parseMetadataLines(inputStream, errorCreator, allowNewLineWithoutReturn);
        RawHttpHeaders trailerHeaders = RawHttp.parseHeaders(trailer, errorCreator).build();

        return new ChunkedBody(chunks, trailerHeaders);
    }

    private static int readChunkSize(InputStream inputStream,
                                     boolean allowNewLineWithoutReturn,
                                     AtomicBoolean hasExtensions) throws IOException {
        char[] chars = new char[4];
        int b;
        int i = 0;
        while ((b = inputStream.read()) >= 0 && i < 4) {
            if (b == '\r') {
                int next = inputStream.read();
                if (next == '\n') {
                    break;
                } else {
                    throw new IllegalStateException("Illegal character after return (parsing chunk-size)");
                }
            }
            if (b == '\n') {
                if (!allowNewLineWithoutReturn) {
                    throw new IllegalStateException("Illegal character after chunk-size " +
                            "(new-line character without preceding return)");
                }
                // unexpected, but allow it
                break;
            }
            if (b == ';') {
                hasExtensions.set(true);
                break;
            }
            chars[i++] = (char) b;
        }
        if (i == 4) {
            // ensure end of chunk-size or new-line
            if (b == '\r') {
                int next = inputStream.read();
                if (next != '\n') {
                    throw new IllegalStateException("Illegal character after return (parsing chunk-size)");
                }
            } else if (b == '\n') {
                if (!allowNewLineWithoutReturn) {
                    throw new IllegalStateException("Illegal character after chunk-size " +
                            "(new-line character without preceding return)");
                }
            } else if (b == ';') {
                hasExtensions.set(true);
            } else {
                throw new IllegalStateException("Invalid chunk-size (too big, more than 4 hex-digits)");
            }
        }

        if (i == 0) {
            throw new IllegalStateException("Missing chunk-size");
        }

        try {
            return Integer.parseInt(new String(chars, 0, i), 16);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid chunk-size (" + e.getMessage() + ")");
        }
    }

    private static Chunk readChunk(InputStream inputStream,
                                   int chunkSize,
                                   boolean allowNewLineWithoutReturn,
                                   AtomicBoolean hasExtensions) throws IOException {
        RawHttpHeaders extensions = hasExtensions.get() ?
                parseExtensions(inputStream, allowNewLineWithoutReturn) :
                emptyRawHttpHeaders();

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

            // consume CRLF
            int b = inputStream.read();
            if (b == '\r') {
                int next = inputStream.read();
                if (next != '\n') {
                    throw new IllegalStateException("Illegal character after return (parsing chunk-size)");
                }
            } else if (b == '\n') {
                if (!allowNewLineWithoutReturn) {
                    throw new IllegalStateException("Illegal character after chunk-data " +
                            "(new-line character without preceding return)");
                }
            } else {
                throw new IllegalStateException("Illegal character after chunk-data (missing CRLF)");
            }
        }

        return new Chunk(extensions, data);
    }

    private static RawHttpHeaders parseExtensions(InputStream inputStream,
                                                  boolean allowNewLineWithoutReturn) throws IOException {
        StringBuilder currentName = new StringBuilder();
        StringBuilder currentValue = new StringBuilder();
        boolean parsingValue = false;
        RawHttpHeaders.Builder extensions = RawHttpHeaders.Builder.newBuilder();
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
                if (!allowNewLineWithoutReturn) {
                    throw new IllegalStateException("Illegal new-line character without preceding return");
                }
                // unexpected, but let's accept new-line without returns
                break;
            } else if (b == '=') {
                if (!parsingValue) {
                    parsingValue = true;
                } else {
                    currentValue.append((char) b);
                }
            } else if (b == ';') {
                extensions.with(currentName.toString().trim(), currentValue.toString().trim());
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
            extensions.with(currentName.toString().trim(), currentValue.toString().trim());
        }

        return extensions.build();
    }

    @Override
    public EagerBodyReader eager() {
        return this;
    }

    /**
     * @return the HTTP message's body as bytes.
     * Notice that this method does not decode the body, so if the body is chunked, for example,
     * the bytes will represent the chunked body, not the decoded body.
     * Use {@link #asChunkedBody()} then {@link ChunkedBody#getData()} to decode the body in such cases.
     */
    public byte[] asBytes() {
        return bytes;
    }

    /**
     * @return the body of the HTTP message as a {@link ChunkedBody} if the body indeed used
     * the chunked transfer coding. If the body was not chunked, this method returns an empty value.
     */
    public Optional<ChunkedBody> asChunkedBody() {
        return Optional.ofNullable(chunkedBody);
    }

    @Override
    public InputStream asStream() {
        return new ByteArrayInputStream(bytes);
    }

    /**
     * Convert the HTTP message's body into a String.
     *
     * @param charset text message's charset
     * @return String representing the HTTP message's body.
     */
    public String asString(Charset charset) {
        return new String(bytes, charset);
    }

    /**
     * @return the body of the HTTP message in String format, using the {@link StandardCharsets#UTF_8} encoding.
     * @see #asString(Charset)
     */
    @Override
    public String toString() {
        return asString(StandardCharsets.UTF_8);
    }

}
