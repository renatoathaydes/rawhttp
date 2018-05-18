package rawhttp.core.body.encoding;

import java.io.OutputStream;
import rawhttp.core.body.ChunkedOutputStream;

/**
 * Decoder for the "chunked" encoding.
 */
public class ChunkDecoder implements HttpMessageDecoder {

    @Override
    public String encodingName() {
        return "chunked";
    }

    @Override
    public OutputStream decode(OutputStream outputStream) {
        return new ChunkedOutputStream(outputStream);
    }

}
