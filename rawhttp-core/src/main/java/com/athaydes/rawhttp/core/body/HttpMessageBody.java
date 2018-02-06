package com.athaydes.rawhttp.core.body;

import com.athaydes.rawhttp.core.LazyBodyReader;
import com.athaydes.rawhttp.core.RawHttpHeaders;

import java.util.Optional;

/**
 * A HTTP message's body.
 *
 * @see com.athaydes.rawhttp.core.HttpMessage
 */
public abstract class HttpMessageBody {

    /**
     * @return the Content-Type header associated with this message, if available.
     */
    protected abstract Optional<String> getContentType();

    protected abstract long getContentLength();

    /**
     * @return this body as a {@link LazyBodyReader}.
     */
    public abstract LazyBodyReader toBodyReader();

    /**
     * @param headers headers object to adapt to include this HTTP message body.
     * @return adjusted headers for this HTTP message body.
     * The Content-Type and Content-Length headers may be modified to fit a HTTP message
     * containing this body.
     */
    public RawHttpHeaders headersFrom(RawHttpHeaders headers) {
        RawHttpHeaders.Builder builder = RawHttpHeaders.Builder.newBuilder(headers);
        getContentType().ifPresent(contentType -> builder.overwrite("Content-Type", contentType));
        return builder.overwrite("Content-Length", Long.toString(getContentLength())).build();
    }

}
