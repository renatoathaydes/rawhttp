package rawhttp.core.body;

import rawhttp.core.HttpMetadataParser;
import rawhttp.core.IOConsumer;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.errors.InvalidHttpHeader;
import rawhttp.core.internal.Bool;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

import static rawhttp.core.RawHttpHeaders.Builder.emptyRawHttpHeaders;

/**
 * Parser of HTTP message body encoded with the "chunked" encoding.
 * <p>
 * This parser is used by {@link InputStreamChunkDecoder} to provide an implementation of
 * {@link InputStream} that decodes the contents of a chunked message.
 */
public final class ChunkedBodyParser {

    private final HttpMetadataParser metadataParser;
    private final boolean allowNewLineWithoutReturn;

    public ChunkedBodyParser(HttpMetadataParser metadataParser) {
        this.metadataParser = metadataParser;
        this.allowNewLineWithoutReturn = metadataParser.getOptions().allowNewLineWithoutReturn();
    }

    /**
     * Consume the given inputStream lazily.
     *
     * @param inputStream stream providing a chunked body
     * @return lazy iterator over the body chunks
     */
    public Iterator<ChunkedBodyContents.Chunk> readLazily(InputStream inputStream) {
        return new Iterator<ChunkedBodyContents.Chunk>() {
            boolean hasMoreChunks = true;

            @Override
            public boolean hasNext() {
                return hasMoreChunks;
            }

            @Override
            public ChunkedBodyContents.Chunk next() {
                if (!hasMoreChunks) {
                    throw new NoSuchElementException();
                }
                try {
                    ChunkedBodyContents.Chunk chunk = readNextChunk(inputStream);
                    hasMoreChunks = chunk.size() > 0;
                    if (!hasMoreChunks) {
                        // throw away the trailer-part
                        readTrailer(inputStream);
                    }
                    return chunk;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Read a single chunk from the given stream.
     *
     * @param inputStream to read chunk from
     * @return a single chunk
     * @throws IOException if an error occurs while reading the stream
     */
    public ChunkedBodyContents.Chunk readNextChunk(InputStream inputStream) throws IOException {
        Bool hasExtensions = new Bool();
        int chunkSize = readChunkSize(inputStream, hasExtensions);
        if (chunkSize < 0) {
            throw new IllegalStateException("unexpected EOF, could not read chunked body");
        }
        return readChunk(inputStream, chunkSize, hasExtensions.get());
    }

    /**
     * Parse the full contents of the chunked message.
     *
     * @param inputStream     to read message from
     * @param chunkConsumer   consumer of individual chunks
     * @param trailerConsumer consumer of the trailer part
     * @throws IOException if an error occurs while reading the stream or calling the callbacks
     */
    public void parseChunkedBody(InputStream inputStream,
                                 IOConsumer<ChunkedBodyContents.Chunk> chunkConsumer,
                                 IOConsumer<RawHttpHeaders> trailerConsumer) throws IOException {
        int chunkSize = 1;
        while (chunkSize > 0) {
            Bool hasExtensions = new Bool();
            chunkSize = readChunkSize(inputStream, hasExtensions);
            if (chunkSize < 0) {
                throw new IllegalStateException("unexpected EOF, could not read chunked body");
            }
            ChunkedBodyContents.Chunk chunk = readChunk(inputStream, chunkSize, hasExtensions.get());
            chunkConsumer.accept(chunk);
        }

        RawHttpHeaders trailer = readTrailer(inputStream);
        trailerConsumer.accept(trailer);
    }

    /**
     * Read the trailer-part.
     * <p>
     * This method must only be called after the last chunk has been read via
     * {@link ChunkedBodyParser#readNextChunk(InputStream)}.
     *
     * @param inputStream to read trailer from
     * @return the trailer part of the message
     * @throws IOException if an error occurs while reading the stream
     */
    public RawHttpHeaders readTrailer(InputStream inputStream) throws IOException {
        BiFunction<String, Integer, RuntimeException> errorCreator =
                (msg, lineNumber) -> new IllegalStateException(msg + " (trailer header)");
        try {
            return metadataParser.parseHeaders(inputStream, errorCreator);
        } catch (InvalidHttpHeader e) {
            throw new InvalidHttpHeader(e.getMessage() + " (trailer header)");
        }
    }

    private ChunkedBodyContents.Chunk readChunk(InputStream inputStream,
                                                int chunkSize,
                                                boolean hasExtensions) throws IOException {
        RawHttpHeaders extensions = hasExtensions ?
                parseExtensions(inputStream) :
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

    private int readChunkSize(InputStream inputStream,
                              Bool hasExtensions) throws IOException {
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

    private RawHttpHeaders parseExtensions(InputStream inputStream) throws IOException {
        StringBuilder currentName = new StringBuilder();
        StringBuilder currentValue = new StringBuilder();
        boolean parsingValue = false;
        RawHttpHeaders.Builder extensions = RawHttpHeaders.newBuilder();
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
