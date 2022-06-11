package rawhttp.core;

import rawhttp.core.body.BodyReader;
import rawhttp.core.body.HttpMessageBody;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Optional;

/**
 * A HTTP Response.
 * <p>
 * This Response may be adapted from a library's own representation of HTTP Response, in which
 * case the {@link Response} type parameter is the type of the library's Response
 * (which can be obtained by calling {@link #getLibResponse()}).
 *
 * @param <Response> library response type, if any
 */
public class RawHttpResponse<Response> extends HttpMessage {

    @Nullable
    private final Response libResponse;
    @Nullable
    private final RawHttpRequest request;

    private final StatusLine statusLine;

    public RawHttpResponse(@Nullable Response libResponse,
                           @Nullable RawHttpRequest request,
                           StatusLine statusLine,
                           RawHttpHeaders headers,
                           @Nullable BodyReader bodyReader) {
        super(headers, bodyReader);
        this.libResponse = libResponse;
        this.request = request;
        this.statusLine = statusLine;
    }

    /**
     * @return the library-specific HTTP libResponse.
     */
    public Optional<Response> getLibResponse() {
        return Optional.ofNullable(libResponse);
    }

    /**
     * @return the HTTP request that originated this HTTP response, if any.
     */
    public Optional<RawHttpRequest> getRequest() {
        return Optional.ofNullable(request);
    }

    @Override
    public StatusLine getStartLine() {
        return statusLine;
    }

    /**
     * @return the status code of this HTTP response.
     * @see #getStartLine()
     */
    public int getStatusCode() {
        return statusLine.getStatusCode();
    }

    /**
     * Ensure that this response is read eagerly, downloading the full body if necessary.
     * <p>
     * The returned object can be safely passed around after the connection used to receive
     * this response has been closed.
     * <p>
     * The connection or stream used to download the response is NOT closed after a call to
     * this method. Use {@link #eagerly(boolean)} if a different behaviour is required.
     *
     * @return this response, after eagerly downloading all of its contents.
     * @throws IOException if an error occurs while reading this response
     */
    public EagerHttpResponse<Response> eagerly() throws IOException {
        return eagerly(true);
    }

    /**
     * Ensure that this response is read eagerly, downloading the full body if necessary.
     * <p>
     * The returned object can be safely passed around after the connection used to receive
     * this response has been closed.
     *
     * @param keepAlive whether to keep the connection alive. If false, the {@link BodyReader}
     *                  associated with this response is closed after by the time this method returns.
     * @return this response, after eagerly downloading all of its contents.
     * @throws IOException if an error occurs while reading this response
     */
    public EagerHttpResponse<Response> eagerly(boolean keepAlive) throws IOException {
        try {
            return EagerHttpResponse.from(this);
        } finally {
            if (!keepAlive) {
                getBody().ifPresent(b -> {
                    try {
                        b.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    @Override
    public RawHttpResponse<Response> withBody(@Nullable HttpMessageBody body) {
        return withBody(body, true);
    }

    @Override
    public RawHttpResponse<Response> withBody(@Nullable HttpMessageBody body, boolean adjustHeaders) {
        return withBody(body, adjustHeaders, (headers, bodyReader) ->
                new RawHttpResponse<>(libResponse, request, statusLine, headers, bodyReader));
    }

    @Override
    public RawHttpResponse<Response> withHeaders(RawHttpHeaders headers) {
        return withHeaders(headers, true);
    }

    @Override
    public RawHttpResponse<Response> withHeaders(RawHttpHeaders headers, boolean append) {
        RawHttpHeaders newHeaders;
        if (append) {
            newHeaders = getHeaders().and(headers);
        } else {
            // to avoid losing the values in the provided headers, we must first remove conflicting
            // headers from our own headers.
            RawHttpHeaders nonConflictingHeaders = RawHttpHeaders.newBuilder(getHeaders())
                    .removeAll(headers.getUniqueHeaderNames()).build();
            newHeaders = headers.and(nonConflictingHeaders);
        }
        return new RawHttpResponse<>(libResponse, request, statusLine,
                newHeaders, getBody().orElse(null));
    }

    /**
     * Create a copy of this HTTP response, replacing its statusLine with the provided one.
     *
     * @param statusLine to replace
     * @return copy of this HTTP message with the provided statusLine
     */
    public RawHttpResponse<Response> withStatusLine(StatusLine statusLine) {
        return new RawHttpResponse<>(libResponse, request, statusLine,
                getHeaders(), getBody().orElse(null));
    }

    /**
     * Check whether the HTTP connection used to receive the given response needs to be closed.
     * <p>
     * Normally, connections should be kept alive so that a client can make several HTTP requests
     * using the same connection, but in certain cases, that cannot be done. For example, the server
     * may send a {@code Connection: closed} header to indicate that it does not expect the connection
     * to be re-used by the client. In HTTP/1.0, this was always the case.
     *
     * @param httpResponse received from some connection
     * @return whether the connection should be closed
     */
    public static boolean shouldCloseConnectionAfter(RawHttpResponse<?> httpResponse) {
        return httpResponse.getStartLine().getHttpVersion().isOlderThan(HttpVersion.HTTP_1_1)
                || !responseHasFramingInformation(httpResponse.getHeaders())
                || httpResponse.getHeaders()
                .getFirst("Connection")
                .orElse("")
                .equalsIgnoreCase("close");
    }

    private static boolean responseHasFramingInformation(RawHttpHeaders headers) {
        return !headers.getFirst("Content-Length").orElse("").isEmpty() ||
                !headers.getFirst("Transfer-Encoding").orElse("").isEmpty();
    }

}
