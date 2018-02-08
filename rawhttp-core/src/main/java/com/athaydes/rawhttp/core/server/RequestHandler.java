package com.athaydes.rawhttp.core.server;

import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;

/**
 * A HTTP Request handler.
 */
@FunctionalInterface
public interface RequestHandler {

    /**
     * Accept an incoming HTTP request.
     *
     * @param request HTTP request
     * @return a HTTP response to send to the client. Must not be null.
     */
    RawHttpResponse<Void> accept(RawHttpRequest request);

}
