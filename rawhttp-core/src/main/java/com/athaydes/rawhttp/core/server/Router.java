package com.athaydes.rawhttp.core.server;

import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;
import javax.annotation.Nullable;

/**
 * HTTP Server router.
 * <p>
 * A Router routes a HTTP request, producing a HTTP response that the server can send to the client.
 */
@FunctionalInterface
public interface Router {

    /**
     * Route an incoming HTTP request.
     *
     * @param request HTTP request
     * @return a HTTP response to send to the client.
     * If null, a default 404 response is returned. If an Exception happens, a default 500 response is returned.
     */
    @Nullable
    RawHttpResponse<?> route(RawHttpRequest request);

}
