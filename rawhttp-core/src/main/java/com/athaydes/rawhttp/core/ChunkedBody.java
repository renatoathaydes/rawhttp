package com.athaydes.rawhttp.core;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ChunkedBody {

    public static class Chunk {

        private final Map<String, Collection<String>> extensions;
        private final byte[] data;

        public Chunk(Map<String, Collection<String>> extensions, byte[] data) {
            this.extensions = extensions;
            this.data = data;
        }

        public Map<String, Collection<String>> getExtensions() {
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
    private final Map<String, Collection<String>> trailerHeaders;

    public ChunkedBody(List<Chunk> chunks, Map<String, Collection<String>> trailerHeaders) {
        this.chunks = chunks;
        this.trailerHeaders = trailerHeaders;
    }

    public List<Chunk> getChunks() {
        return chunks;
    }

    public Map<String, Collection<String>> getTrailerHeaders() {
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
