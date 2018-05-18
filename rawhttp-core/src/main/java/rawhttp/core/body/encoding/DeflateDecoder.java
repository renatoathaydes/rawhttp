package rawhttp.core.body.encoding;

import java.io.OutputStream;
import java.util.zip.InflaterOutputStream;

/**
 * Decoder for the "deflat" encoding.
 */
public class DeflateDecoder implements HttpMessageDecoder {
    @Override
    public String encodingName() {
        return "deflate";
    }

    @Override
    public OutputStream decode(OutputStream outputStream) {
        return new InflaterOutputStream(outputStream);
    }

}
