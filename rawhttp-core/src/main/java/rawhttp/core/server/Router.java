package rawhttp.core.server;

import rawhttp.core.HttpVersion;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.RequestLine;
import rawhttp.core.StatusLine;

import java.util.Optional;

/**
 * HTTP Server router.
 * <p>
 * A Router routes a HTTP request, producing a HTTP response that the server can send to the client.
 */
@FunctionalInterface
public interface Router {

    RawHttpResponse<Void> RESPONSE_100 = new RawHttpResponse<>(null, null,
            new StatusLine(HttpVersion.HTTP_1_1, 100, "Continue"), RawHttpHeaders.empty(), null);

    /**
     * Route an incoming HTTP request.
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
     * <p>
     * The default implementation return a 100-response without any headers.
     *
     * @param requestLine message request-line
     * @param headers     message headers
     * @return interim response (with 100 status code) or final response (any other status code).
     */
    default RawHttpResponse<Void> continueResponse(RequestLine requestLine, RawHttpHeaders headers) {
        return RESPONSE_100;
    }

}
