package rawhttp.core.body.encoding;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Implementation of {@link HttpBodyEncodingRegistry} based on Java {@link ServiceLoader}.
 * <p>
 * This is the default registry used by RawHTTP. To provide extra encodings, you can just put the decoders
 * implementations on the classpath and register them via a file at
 * {@code META-INF/services/rawhttp.core.body.encoding.HttpBodyEncodingRegistry}.
 */
public final class ServiceLoaderHttpBodyEncodingRegistry implements HttpBodyEncodingRegistry {

    private final Map<String, HttpMessageDecoder> encoderByName;

    public ServiceLoaderHttpBodyEncodingRegistry() {
        Map<String, HttpMessageDecoder> encoderByName = new HashMap<>();
        ServiceLoader<HttpMessageDecoder> loader = ServiceLoader.load(HttpMessageDecoder.class);
        for (HttpMessageDecoder encoder : loader) {
            encoderByName.put(encoder.encodingName().toLowerCase(), encoder);
        }
        this.encoderByName = Collections.unmodifiableMap(encoderByName);
    }

    @Override
    public Optional<HttpMessageDecoder> get(String encoding) {
        return Optional.ofNullable(encoderByName.get(encoding.toLowerCase()));
    }

}
