package rawhttp.core.body.encoding;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Decoder for the "gzip" encoding.
 */
public class GzipDecoder implements HttpMessageDecoder {

    @Override
    public String encodingName() {
        return "gzip";
    }

    @Override
    public OutputStream decode(OutputStream out) throws IOException {
        // FIXME this class will actually compress, not decompress
        return new GZIPOutputStream(out);
    }

}
