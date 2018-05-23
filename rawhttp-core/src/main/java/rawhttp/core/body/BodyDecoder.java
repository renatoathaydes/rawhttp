package rawhttp.core.body;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import rawhttp.core.body.encoding.DecodingOutputStream;
import rawhttp.core.body.encoding.HttpBodyEncodingRegistry;
import rawhttp.core.body.encoding.HttpMessageDecoder;
import rawhttp.core.errors.UnknownEncodingException;

import static java.util.stream.Collectors.toCollection;

/**
 * A HTTP message body decoder.
 * <p>
 * This class simply uses the {@link HttpMessageDecoder} instances provided by a {@link HttpBodyEncodingRegistry}
 * to perform the actual decoding.
 */
public class BodyDecoder {

    private final HttpBodyEncodingRegistry registry;
    private final List<String> encodings;

    /**
     * Create a no-op body decoder.
     */
    public BodyDecoder() {
        this(null, Collections.emptyList());
    }

    /**
     * Create a decoder that will use the given encodings to decode HTTP message bodies.
     * <p>
     * If any of the encodings does not have a corresponding entry in the registry, this decoder will still be created
     * successfully, but an attempt to decode the body of a message using this instance will fail with
     * {@link UnknownEncodingException}.
     *
     * @param registry  the registry of {@link HttpMessageDecoder}s.
     * @param encodings the encodings applied to the message body
     */
    public BodyDecoder(HttpBodyEncodingRegistry registry, List<String> encodings) {
        this.registry = registry;
        this.encodings = encodings;
    }

    public DecodingOutputStream decoding(OutputStream out) throws IOException {
        ArrayList<HttpMessageDecoder> decoders = getDecoders();

        DecodingOutputStream decoderStream = new DecodingOutputStream(out);

        if (decoders.isEmpty()) {
            return decoderStream;
        } else if (decoders.get(decoders.size() - 1).encodingName().equalsIgnoreCase("chunked")) {
            // when the chunked encoding is used to frame the message, we don't need to to decode its contents
            decoders.remove(decoders.size() - 1);
        }

        for (HttpMessageDecoder decoder : decoders) {
            decoderStream = decoder.decode(decoderStream);
        }

        return decoderStream;
    }

    /**
     * @return the encodings applied to the body of a HTTP message
     */
    public List<String> getEncodings() {
        return encodings;
    }

    private ArrayList<HttpMessageDecoder> getDecoders() {
        return encodings.stream()
                .map(encoding -> registry.get(encoding)
                        .orElseThrow(() -> new UnknownEncodingException(encoding)))
                .collect(toCollection(ArrayList::new));
    }

}
