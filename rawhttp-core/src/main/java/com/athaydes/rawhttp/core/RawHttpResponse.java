package com.athaydes.rawhttp.core;

import com.athaydes.rawhttp.core.body.HttpMessageBody;

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

    private final StatusCodeLine statusCodeLine;

    public RawHttpResponse(@Nullable Response libResponse,
                           @Nullable RawHttpRequest request,
                           StatusCodeLine statusCodeLine,
                           RawHttpHeaders headers,
                           @Nullable BodyReader bodyReader) {
        super(headers, bodyReader);
        this.libResponse = libResponse;
        this.request = request;
        this.statusCodeLine = statusCodeLine;
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
    public StatusCodeLine getStartLine() {
        return statusCodeLine;
    }

    /**
     * @return the status code of this HTTP response.
     * @see #getStartLine()
     */
    public int getStatusCode() {
        return statusCodeLine.getStatusCode();
    }

    /**
     * Ensure that this response is read eagerly, downloading the full body if necessary.
     * <p>
     * The returned object can be safely passed around after the connection used to receive
     * this response has been closed.
     * <p>
     * The connection or stream used to download the response is not closed after a call to
     * this method. Use {@link #eagerly(boolean)} if a different behaviour is required.
     *
     * @return this response, after eagerly downloading all of its contents.
     * @throws IOException if an error occurs while reading this response
     */
    public EagerHttpResponse<Response> eagerly() throws IOException {
        return eagerly(false);
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
    public RawHttpResponse<Response> replaceBody(HttpMessageBody body) {
        return new RawHttpResponse<>(libResponse, request, statusCodeLine,
                body.headersFrom(getHeaders()), body.toBodyReader());
    }

}
