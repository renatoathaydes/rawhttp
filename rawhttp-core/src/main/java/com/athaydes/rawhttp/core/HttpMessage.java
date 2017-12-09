package com.athaydes.rawhttp.core;

import com.athaydes.rawhttp.core.body.HttpMessageBody;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public abstract class HttpMessage {

    private final RawHttpHeaders headers;

    @Nullable
    private final BodyReader bodyReader;

    public HttpMessage(RawHttpHeaders headers,
                       @Nullable BodyReader bodyReader) {
        this.headers = headers;
        this.bodyReader = bodyReader;
    }

    public abstract StartLine getStartLine();

    public abstract HttpMessage replaceBody(HttpMessageBody body);

    public RawHttpHeaders getHeaders() {
        return headers;
    }

    public Optional<? extends BodyReader> getBody() {
        return Optional.ofNullable(bodyReader);
    }

    public String messageWithoutBody() {
        return String.join("\r\n", getStartLine().toString(), getHeaders().toString());
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
