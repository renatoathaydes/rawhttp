package rawhttp.core.body;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A {@link InputStream} implementation that wraps another InputStream, encoding its contents with the
 * "chunked" encoding.
 */
public class InputStreamChunkEncoder extends InputStream {

    // TODO allow users to insert chunk extensions and a trailer

    private final InputStream stream;
    private final int chunkSize;

    private byte[] buffer = new byte[0];
    private int index = 0;
    private boolean terminated = false;

    /**
     * Create a {@link InputStreamChunkEncoder} to encode the contents of the given stream.
     *
     * @param stream    to encode
     * @param chunkSize maximum chunk-size
     */
    public InputStreamChunkEncoder(InputStream stream, int chunkSize) {
        this.stream = stream;
        this.chunkSize = chunkSize;
    }

    private byte[] nextChunk() throws IOException {
        byte[] chunkData = new byte[chunkSize];
        int bytesRead = stream.read(chunkData);
        if (bytesRead <= 0) {
            terminated = true;
            bytesRead = 0;
        }

        byte[] chunkSizeBytes = (Integer.toString(bytesRead, 16) + "\r\n").getBytes(StandardCharsets.US_ASCII);

        byte[] chunk = new byte[chunkSizeBytes.length + bytesRead + 2];
        System.arraycopy(chunkSizeBytes, 0, chunk, 0, chunkSizeBytes.length);
        if (bytesRead > 0) {
            System.arraycopy(chunkData, 0, chunk, chunkSizeBytes.length, bytesRead);
        }
        chunk[chunk.length - 2] = '\r';
        chunk[chunk.length - 1] = '\n';
        return chunk;
    }

    @Override
    public int read() throws IOException {
        if (index >= buffer.length) {
            if (terminated) {
                return -1;
            }
            buffer = nextChunk();
            if (buffer.length == 0) {
                return -1;
            }
            index = 0;
        }
        return buffer[index++];
    }

    @Override
    public boolean markSupported() {
        return false;
    }

}
