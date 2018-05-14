package rawhttp.core.server;

import java.util.Optional;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

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
     * If an empty Optional is returned, the server will use a default 404 response.
     * If an Exception happens, a default 500 response is returned.
     */
    Optional<RawHttpResponse<?>> route(RawHttpRequest request);

}
