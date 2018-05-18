package rawhttp.core.body.encoding;

import java.util.Optional;

/**
 * A registry of encodings and related {@link HttpMessageDecoder}s.
 */
public interface HttpBodyEncodingRegistry {

    /**
     * Get the decoder mapped to the given encoding, if available.
     *
     * @param encoding case-insensitive encoding name
     * @return the decoder associated with the encoding, or empty if none is available
     */
    Optional<HttpMessageDecoder> get(String encoding);
}
