package com.athaydes.rawhttp.core;

import com.athaydes.rawhttp.core.body.HttpMessageBody;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;

public class RawHttpRequest extends HttpMessage {

    private final MethodLine methodLine;

    public RawHttpRequest(MethodLine methodLine,
                          RawHttpHeaders headers,
                          @Nullable BodyReader bodyReader) {
        super(headers, bodyReader);
        this.methodLine = methodLine;
    }

    public String getMethod() {
        return methodLine.getMethod();
    }

    public URI getUri() {
        return methodLine.getUri();
    }

    @Override
    public MethodLine getStartLine() {
        return methodLine;
    }

    public EagerHttpRequest eagerly() throws IOException {
        return new EagerHttpRequest(this);
    }

    @Override
    public RawHttpRequest replaceBody(HttpMessageBody body) {
        return new RawHttpRequest(methodLine, body.headersFrom(getHeaders()), body.toBodyReader());
    }

}
