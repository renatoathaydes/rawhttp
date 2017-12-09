package com.athaydes.rawhttp.core.body;

import com.athaydes.rawhttp.core.LazyBodyReader;
import com.athaydes.rawhttp.core.RawHttpHeaders;

import java.util.Optional;

public abstract class HttpMessageBody {

    protected abstract Optional<String> getContentType();

    protected abstract long getContentLength();

    public abstract LazyBodyReader toBodyReader();

    public RawHttpHeaders headersFrom(RawHttpHeaders headers) {
        RawHttpHeaders.Builder builder = RawHttpHeaders.Builder.newBuilder(headers);
        getContentType().ifPresent(contentType -> builder.overwrite("Content-Type", contentType));
        return builder.overwrite("Content-Length", Long.toString(getContentLength())).build();
    }

}
