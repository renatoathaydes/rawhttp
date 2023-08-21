package rawhttp.core.server;

import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.RequestLine;

import java.io.IOException;
import java.net.Socket;
import java.util.Optional;

/**
 * HTTP Server router.
 * <p>
 * A Router routes a HTTP request, producing a HTTP response that the server can send to the client.
 */
@FunctionalInterface
public interface Router {

    /**
     * Route an incoming HTTP request.
     * <p>
     * Implementations are free to handle GET and HEAD requests in the same manner, as when the server
     * responds to a HEAD request, it will never send a response body, even if the {@link Router} returns
     * a response with a body.
     *
     * @param request HTTP request
     * @return a HTTP response to send to the client.
     * If an empty Optional is returned, the server will use a default 404 response.
     * If an Exception happens, a default 500 response is returned.
     */
    Optional<RawHttpResponse<?>> route(RawHttpRequest request);

    /**
     * Get the HTTP response for a request that includes the {@code Expect} header with a {@code 100-continue} value.
     * <p>
     * If the returned response does not have a 100 status code, the server will treat the returned response as the
     * final response and will NOT call the {@link Router#route(RawHttpRequest)} method.
     * <p>
     * In other words, to let the HTTP client know that the server does not accept the request and will therefore
     * not read the request body, return a non-100 response. Otherwise, return a 100-response, in which case the
     * {@link Router#route(RawHttpRequest)} method will be invoked by the server in order to allow the ordinary
     * routing of the request to continue.
     *
     * @param requestLine message request-line
     * @param headers     message headers
     * @return interim response (with 100 status code) or final response (any other status code).
     * If an empty {@link Optional} is returned, the server returns a 100-response without any headers.
     */
    default Optional<RawHttpResponse<Void>> continueResponse(RequestLine requestLine, RawHttpHeaders headers) {
        return Optional.empty();
    }

    /**
     * Tunnel the client to the provided URI.
     * <p>
     * This method is called when a client requests to CONNECT to another location.
     * By default, the client is closed and an {@link UnsupportedOperationException} is thrown.
     * <p>
     * This method is called from a request Thread, so it's advisable that implementations that
     * support tunneling fork the handling to a different Thread immediately.
     * <p>
     * See <a href="https://www.rfc-editor.org/rfc/rfc9110#CONNECT">RFC-9110 Section 9.3.6</a>.
     *
     * @param client requesting tunneling.
     * @throws IOException if an IO problem occurs
     */
    default void tunnel(Socket client) throws IOException {
        client.close();
        throw new UnsupportedOperationException("CONNECT request is not supported");
    }

}
