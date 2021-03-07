package rawhttp.core.body.encoding;


import java.io.OutputStream;

/**
 * Decoder for the "identity" encoding.
 */
public final class IdentityDecoder implements HttpMessageDecoder {
    @Override
    public String encodingName() {
        return "identity";
    }

    @Override
    public OutputStream decode(OutputStream out) {
        return out;
    }
}
