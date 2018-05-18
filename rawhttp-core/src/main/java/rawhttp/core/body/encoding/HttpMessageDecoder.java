package rawhttp.core.body.encoding;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A HTTP message body decoder.
 */
public interface HttpMessageDecoder {

    /**
     * @return the name of the encoding supported by this decoder.
     */
    String encodingName();

    /**
     * Create an {@link OutputStream} that decodes the bytes being written into it according to the encoding
     * supported by this decoder, before writing the decoded bytes into the given stream.
     *
     * @param out receiver of decoded messages
     * @return a stream into which encoded messages can be written to be decoded
     * @throws IOException if an error occurs while creating the decoder stream
     */
    OutputStream decode(OutputStream out) throws IOException;

}
