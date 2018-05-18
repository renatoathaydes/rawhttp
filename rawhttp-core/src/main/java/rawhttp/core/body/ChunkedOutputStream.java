package rawhttp.core.body;

import java.io.IOException;
import java.io.OutputStream;

public final class ChunkedOutputStream extends OutputStream {

    private final OutputStream out;

    public ChunkedOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(int b) throws IOException {
        // FIXME implement this so the chunked encoding can be used as non-last encoding
        throw new UnsupportedOperationException();
    }

    /**
     * Get the target output for the decoded body.
     * <p>
     * This method allows a writer to bypass this stream decoding logic entirely by directly consuming the chunked
     * body, then writing only the actual data to the stream wrapped by this instance.
     *
     * @return the target stream for the decoded data
     */
    public OutputStream getDecodedChunksTarget() {
        return out;
    }

}
