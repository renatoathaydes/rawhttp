package com.athaydes.rawhttp.core.server;

/**
 * HTTP Server router.
 * <p>
 * A Router maps a request path to a {@link RequestHandler}. It must always return a handler, even if
 * the requested path is unknown (in which case it should return a handler that serves an error response).
 */
@FunctionalInterface
public interface Router {

    /**
     * Maps the given path to a {@link RequestHandler}.
     *
     * @param path request path
     * @return handler for the given path. Must not return null.
     */
    RequestHandler route(String path);

}
