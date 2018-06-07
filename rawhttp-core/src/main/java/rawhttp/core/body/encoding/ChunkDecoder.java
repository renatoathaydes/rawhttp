package rawhttp.core.body.encoding;

import java.io.OutputStream;

/**
 * Decoder for the "chunked" encoding.
 */
public class ChunkDecoder implements HttpMessageDecoder {

    @Override
    public String encodingName() {
        return "chunked";
    }

    @Override
    public DecodingOutputStream decode(OutputStream outputStream) {
        return new ChunkedOutputStream(outputStream);
    }

}
