package com.athaydes.rawhttp.core;

import java.util.List;

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

    public List<Chunk> getChunks() {
        return chunks;
    }

    public RawHttpHeaders getTrailerHeaders() {
        return trailerHeaders;
    }

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
