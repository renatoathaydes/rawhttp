package com.athaydes.rawhttp.core;

import java.util.List;

/**
 * Container of a HTTP message body which has the "chunked" transfer-coding.
 * <p>
 * See <a href="https://tools.ietf.org/html/rfc7230#section-4.1">Section 4.1</a>
 * of RFC-7230 for details.
 */
public class ChunkedBody {

    public static class Chunk {

        private final RawHttpHeaders extensions;
        private final byte[] data;

        public Chunk(RawHttpHeaders extensions, byte[] data) {
            this.extensions = extensions;
            this.data = data;
        }

        public RawHttpHeaders getExtensions() {
            return extensions;
        }

        public byte[] getData() {
            return data;
        }

        public int size() {
            return data.length;
        }
    }

    private final List<Chunk> chunks;
    private final RawHttpHeaders trailerHeaders;

    public ChunkedBody(List<Chunk> chunks, RawHttpHeaders trailerHeaders) {
        this.chunks = chunks;
        this.trailerHeaders = trailerHeaders;
    }

    /**
     * @return the chunks that make up the chunked body.
     */
    public List<Chunk> getChunks() {
        return chunks;
    }

    /**
     * @return the trailing headers included in the chunked body.
     */
    public RawHttpHeaders getTrailerHeaders() {
        return trailerHeaders;
    }

    /**
     * @return the message body (after decoding).
     */
    public byte[] getData() {
        int totalSize = chunks.stream().mapToInt(Chunk::size).sum();
        byte[] result = new byte[totalSize];
        int offset = 0;
        for (Chunk chunk : chunks) {
            System.arraycopy(chunk.data, 0, result, offset, chunk.size());
            offset += chunk.size();
        }
        return result;
    }
}
