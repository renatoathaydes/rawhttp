package rawhttp.core.body.encoding;

import java.io.OutputStream;

/**
 * Decoder for the "gzip" encoding.
 */
public class GzipDecoder implements HttpMessageDecoder {

    private int bufferSize = 4096;

    /**
     * Set the size of the buffer to use when transferring data from the inflater InputStream to the
     * target OutputStream.
     *
     * @param bufferSize size of transfer buffer
     */
    public void setBufferSize(int bufferSize) {
        if (bufferSize < 1) {
            throw new IllegalArgumentException("Buffer size must be positive, invalid size: " + bufferSize);
        }
        this.bufferSize = bufferSize;
    }

    @Override
    public String encodingName() {
        return "gzip";
    }

    @Override
    public OutputStream decode(OutputStream out) {
        return new GZipUncompressorOutputStream(out, bufferSize);
    }

}
