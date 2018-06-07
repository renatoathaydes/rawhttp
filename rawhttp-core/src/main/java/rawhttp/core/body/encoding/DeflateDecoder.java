package rawhttp.core.body.encoding;

import java.io.OutputStream;
import java.util.zip.InflaterOutputStream;

/**
 * Decoder for the "deflate" encoding.
 */
public class DeflateDecoder implements HttpMessageDecoder {
    @Override
    public String encodingName() {
        return "deflate";
    }

    @Override
    public DecodingOutputStream decode(OutputStream outputStream) {
        return new DecodingOutputStream(new InflaterOutputStream(outputStream));
    }

}
