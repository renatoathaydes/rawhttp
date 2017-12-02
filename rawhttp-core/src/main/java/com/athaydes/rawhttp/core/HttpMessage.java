package com.athaydes.rawhttp.core;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.joining;

public abstract class HttpMessage {

    private final Map<String, Collection<String>> headers;

    @Nullable
    private final BodyReader bodyReader;

    public HttpMessage(Map<String, Collection<String>> headers,
                       @Nullable BodyReader bodyReader) {
        this.headers = headers;
        this.bodyReader = bodyReader;
    }

    public abstract StartLine getStartLine();

    public Map<String, Collection<String>> getHeaders() {
        return headers;
    }

    public Optional<? extends BodyReader> getBody() {
        return Optional.ofNullable(bodyReader);
    }

    public String messageWithoutBody() {
        return String.join("\r\n", getStartLine().toString(),
                getHeaders().entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream().map(v -> entry.getKey() + ": " + v))
                        .collect(joining("\r\n"))) + "\r\n\r\n";
    }

    @Override
    public String toString() {
        String body = getBody().map(Object::toString).orElse("<no body>");
        return messageWithoutBody() + body;
    }

    public void writeTo(OutputStream out) throws IOException {
        writeTo(out, 4096);
    }

    public void writeTo(OutputStream out, int bufferSize) throws IOException {
        out.write(messageWithoutBody().getBytes(StandardCharsets.US_ASCII));
        Optional<? extends BodyReader> body = getBody();
        if (body.isPresent()) {
            InputStream in = body.get().asStream();
            byte[] buffer = new byte[bufferSize];
            while (true) {
                int actuallyRead = in.read(buffer);
                if (actuallyRead < 0) {
                    break;
                }
                out.write(buffer, 0, actuallyRead);
            }
        }
    }

}
