package com.athaydes.rawhttp.core.server;

import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;

@FunctionalInterface
public interface RequestHandler {

    /**
     * Accept an incoming HTTP request.
     *
     * @param request HTTP request
     * @return a HTTP response
     */
    RawHttpResponse<Void> accept(RawHttpRequest request);

}
