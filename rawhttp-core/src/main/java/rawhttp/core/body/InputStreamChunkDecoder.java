package rawhttp.core.body;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import rawhttp.core.RawHttpHeaders;

/**
 * A {@link InputStream} implementation that wraps another InputStream, decoding its contents with the
 * "chunked" encoding.
 */
public class InputStreamChunkDecoder extends InputStream {

    private final ChunkedBodyParser parser;
    private final InputStream inputStream;

    private boolean done = false;
    private ByteArrayInputStream currentSource;
    private RawHttpHeaders trailer;

    public InputStreamChunkDecoder(ChunkedBodyParser parser, InputStream inputStream) {
        this.parser = parser;
        this.inputStream = inputStream;
    }

    /**
     * @return this InputStream as an iterator over the chunks of the message body.
     * <p>
     * The chunks are consumed lazily.
     */
    public Iterator<ChunkedBodyContents.Chunk> asIterator() {
        return parser.readLazily(inputStream);
    }

    /**
     * Read the next chunk available from the source input stream.
     * <p>
     * The last chunk is always the empty chunk. After the empty chunk is returned, the message is completed
     * and calling this method again after that results in an {@link IllegalStateException}.
     * <p>
     * When the last chunk is returned, the trailer-part, if any, is also consumed and can be obtained by
     * calling the {@link InputStreamChunkDecoder#getTrailer()} method.
     *
     * @return the next chunk
     * @throws IOException           if an error occurs while reading the original stream
     * @throws IllegalStateException if this method is called after the empty chunk is returned
     */
    public ChunkedBodyContents.Chunk readChunk() throws IOException {
        if (done) {
            throw new IllegalStateException("HTTP message body is already consumed");
        }
        ChunkedBodyContents.Chunk chunk = parser.readNextChunk(inputStream);
        if (chunk.size() == 0) {
            trailer = parser.readTrailer(inputStream);
            done = true;
            currentSource = null;
        } else {
            currentSource = new ByteArrayInputStream(chunk.getData());
        }
        return chunk;
    }

    @Override
    public int read() throws IOException {
        if (done) {
            return -1;
        }
        if (currentSource == null) {
            readChunk();
            if (currentSource == null) {
                return -1;
            }
        }
        int b = currentSource.read();
        if (b == -1) {
            // read the next chunk
            currentSource = null;
            return read();
        }
        return b & 0xFF;
    }

    /**
     * @return the trailer-part of the chunked body.
     * This method should only be called after the full body has been read. Before that, it will
     * just return an empty set of headers.
     */
    public RawHttpHeaders getTrailer() {
        return trailer == null ? RawHttpHeaders.empty() : trailer;
    }
}
