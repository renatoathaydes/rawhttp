package com.athaydes.rawhttp.core;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.athaydes.rawhttp.core.RawHttpHeaders.Builder.emptyRawHttpHeaders;
import static java.util.Objects.requireNonNull;

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

    /**
     * @return the length of this body if known without consuming it first.
     */
    protected abstract OptionalLong getLengthIfKnown();

    /**
     * Read the HTTP message body, simultaneously writing it to the given output.
     *
     * @param out to write the HTTP body to
     * @throws IOException if an error occurs while writing the message
     */
    public void writeTo(OutputStream out) throws IOException {
        writeTo(out, 4096);
    }

    /**
     * Read the HTTP message body, simultaneously writing it to the given output.
     *
     * @param out        to write the HTTP body to
     * @param bufferSize size of the buffer to use for writing
     * @throws IOException if an error occurs while writing the message
     */
    public void writeTo(OutputStream out, int bufferSize) throws IOException {
        InputStream inputStream = asStream();
        switch (bodyType) {
            case CONTENT_LENGTH:
                OptionalLong bodyLength = getLengthIfKnown();
                if (!bodyLength.isPresent() || bodyLength.getAsLong() < 0) {
                    throw new IllegalArgumentException("Invalid length (null OR < 0)");
                }
                readAndWriteBytesUpToLength(inputStream, bodyLength.getAsLong(), out, bufferSize);
                break;
            case CHUNKED:
                readChunkedBody(inputStream, true);
                break;
            case CLOSE_TERMINATED:
                readBytesWhileAvailable(inputStream);
                break;
            default:
                throw new IllegalStateException("Unknown body type: " + bodyType);
        }
    }

    protected static class ConsumedBody {
        @Nullable
        private final byte[] bodyBytes;

        @Nullable
        private final ChunkedBodyContents chunkedBody;

        ConsumedBody(@Nonnull byte[] bodyBytes) {
            requireNonNull(bodyBytes);
            this.bodyBytes = bodyBytes;
            this.chunkedBody = null;
        }

        ConsumedBody(@Nonnull ChunkedBodyContents chunkedBody) {
            requireNonNull(chunkedBody);
            this.chunkedBody = chunkedBody;
            this.bodyBytes = null;
        }

        <T> T use(Function<byte[], T> takeBytes, Function<ChunkedBodyContents, T> takeChunkedBody) {
            if (bodyBytes == null) {
                return takeChunkedBody.apply(chunkedBody);
            } else {
                return takeBytes.apply(bodyBytes);
            }
        }

    }

    protected static ConsumedBody consumeBody(BodyType bodyType,
                                              @Nonnull InputStream inputStream,
                                              @Nullable Long bodyLength,
                                              boolean allowNewLineWithoutReturn) throws IOException {
        switch (bodyType) {
            case CONTENT_LENGTH:
                if (bodyLength == null || bodyLength < 0) {
                    throw new IllegalArgumentException("Invalid length (null OR < 0)");
                }
                return new ConsumedBody(readBytesUpToLength(inputStream, Math.toIntExact(bodyLength)));
            case CHUNKED:
                return new ConsumedBody(readChunkedBody(inputStream, allowNewLineWithoutReturn));
            case CLOSE_TERMINATED:
                return new ConsumedBody(readBytesWhileAvailable(inputStream));
            default:
                throw new IllegalStateException("Unknown body type: " + bodyType);
        }
    }

    private static byte[] readBytesUpToLength(InputStream inputStream,
                                              int bodyLength) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(bodyLength);
        readAndWriteBytesUpToLength(inputStream, bodyLength, outputStream, 4096);
        return outputStream.toByteArray();
    }

    private static void readAndWriteBytesUpToLength(InputStream inputStream,
                                                    long bodyLength,
                                                    OutputStream outputStream,
                                                    int bufferSize) throws IOException {
        int offset = 0;
        byte[] bytes = new byte[(int) Math.min(bodyLength, bufferSize)];
        while (offset < bodyLength) {
            int bytesToRead = (int) Math.min(bytes.length, bodyLength - offset);
            int actuallyRead = inputStream.read(bytes, 0, bytesToRead);
            if (actuallyRead < 0) {
                throw new IOException("InputStream provided " + offset + ", but " + bodyLength + " were expected");
            } else {
                outputStream.write(bytes, 0, actuallyRead);
            }
            offset += actuallyRead;
        }
    }

    private static ChunkedBodyContents readChunkedBody(InputStream inputStream,
                                                       boolean allowNewLineWithoutReturn) throws IOException {
        List<ChunkedBodyContents.Chunk> chunks = new ArrayList<>();
        int chunkSize = 1;
        while (chunkSize > 0) {
            AtomicBoolean hasExtensions = new AtomicBoolean(false);
            chunkSize = readChunkSize(inputStream, allowNewLineWithoutReturn, hasExtensions);
            if (chunkSize < 0) {
                throw new IllegalStateException("unexpected EOF, could not read chunked body");
            }
            ChunkedBodyContents.Chunk chunk = readChunk(inputStream, chunkSize, allowNewLineWithoutReturn, hasExtensions);
            chunks.add(chunk);
        }

        BiFunction<String, Integer, RuntimeException> errorCreator =
                (msg, lineNumber) -> new IllegalStateException(msg + " (parsing chunked body headers)");

        List<String> trailer = RawHttp.parseMetadataLines(inputStream, errorCreator, allowNewLineWithoutReturn);
        RawHttpHeaders trailerHeaders = RawHttp.parseHeaders(trailer, errorCreator).build();

        return new ChunkedBodyContents(chunks, trailerHeaders);
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

    private static ChunkedBodyContents.Chunk readChunk(InputStream inputStream,
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

        return new ChunkedBodyContents.Chunk(extensions, data);
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

}
