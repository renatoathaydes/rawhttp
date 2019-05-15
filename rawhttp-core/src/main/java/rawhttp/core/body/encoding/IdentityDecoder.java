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
    public DecodingOutputStream decode(OutputStream out) {
        if (out instanceof DecodingOutputStream) {
            return (DecodingOutputStream) out;
        }
        return new DecodingOutputStream(out);
    }
}
