package rawhttp.core;

import rawhttp.core.body.BodyReader;
import rawhttp.core.body.ChunkedBodyContents;
import rawhttp.core.body.EagerBodyReader;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import static rawhttp.core.RawHttpHeaders.Builder.emptyRawHttpHeaders;

/**
 * An eager specialization of {@link RawHttpResponse}.
 * <p>
 * Normally, an instance of this class is obtained by calling {@link RawHttpResponse#eagerly()}.
 * Doing that guarantees that the response is fully downloaded or read from its source, so that
 * it can be passed around even after the connection or stream it originates from has been closed.
 *
 * @param <Response> library response type
 */
public class EagerHttpResponse<Response> extends RawHttpResponse<Response> {

    public EagerHttpResponse(@Nullable Response libResponse,
                             @Nullable RawHttpRequest request,
                             StatusLine startLine,
                             RawHttpHeaders headers,
                             @Nullable EagerBodyReader bodyReader) {
        super(libResponse,
                request,
                startLine,
                headers,
                bodyReader
        );
    }

    /**
     * Create a new {@link EagerHttpResponse} from the provided response.
     *
     * @param response   raw HTTP response
     * @param <Response> library response type
     * @return an eager HTTP response from the given HTTP response.
     * @throws IOException if an error occurs while reading the response.
     */
    public static <Response> EagerHttpResponse<Response> from(RawHttpResponse<Response> response)
            throws IOException {
        if (response instanceof EagerHttpResponse) {
            return (EagerHttpResponse<Response>) response;
        }

        @Nullable EagerBodyReader bodyReader = response.getBody().isPresent() ?
                response.getBody().get().eager() :
                null;

        // headers might come from the trailing part of the HTTP message, in which case we merge them together
        RawHttpHeaders headers;
        if (bodyReader != null) {
            RawHttpHeaders trailingHeaders = bodyReader.asChunkedBodyContents()
                    .map(ChunkedBodyContents::getTrailerHeaders)
                    .orElse(emptyRawHttpHeaders());
            headers = RawHttpHeaders.newBuilder(response.getHeaders())
                    .merge(trailingHeaders)
                    .build();
        } else {
            headers = response.getHeaders();
        }

        return new EagerHttpResponse<>(response.getLibResponse().orElse(null),
                response.getRequest().orElse(null),
                response.getStartLine(),
                headers, bodyReader);
    }

    /**
     * @return this eager response.
     */
    @Override
    public EagerHttpResponse<Response> eagerly() {
        return this;
    }

    @Override
    public Optional<EagerBodyReader> getBody() {
        Optional<? extends BodyReader> body = super.getBody();
        return body.map(b -> (EagerBodyReader) b);
    }

    @Override
    public EagerHttpResponse<Response> withHeaders(RawHttpHeaders headers) {
        return withHeaders(headers, true);
    }

    @Override
    public EagerHttpResponse<Response> withHeaders(RawHttpHeaders headers, boolean append) {
        return new EagerHttpResponse<>(getLibResponse().orElse(null),
                getRequest().orElse(null),
                getStartLine(),
                append ? getHeaders().and(headers) : headers.and(getHeaders()),
                getBody().orElse(null));
    }

    public EagerHttpResponse<Response> withStatusLine(StatusLine statusLine) {
        return new EagerHttpResponse<>(getLibResponse().orElse(null),
                getRequest().orElse(null),
                statusLine,
                getHeaders(),
                getBody().orElse(null));
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLibResponse(), getStartLine(), getHeaders(), getBody());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;

        EagerHttpResponse<?> that = (EagerHttpResponse<?>) other;

        return Objects.equals(getLibResponse(), that.getLibResponse())
                && getStartLine().equals(that.getStartLine())
                && getHeaders().equals(that.getHeaders())
                && getBody().equals(that.getBody());
    }

}
