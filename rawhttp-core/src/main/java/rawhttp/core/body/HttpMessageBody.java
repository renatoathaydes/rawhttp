package rawhttp.core.body;

import java.util.Optional;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import rawhttp.core.HttpMessage;
import rawhttp.core.LazyBodyReader;
import rawhttp.core.RawHttpHeaders;

/**
 * A HTTP message's body.
 *
 * @see HttpMessage
 */
public abstract class HttpMessageBody {

    @Nullable
    private final String contentType;

    protected HttpMessageBody(@Nullable String contentType) {
        this.contentType = contentType;
    }

    /**
     * @return the Content-Type header associated with this message, if available.
     */
    protected Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    /**
     * @return the content-length of this message, if known.
     */
    protected abstract OptionalLong getContentLength();

    /**
     * @return this body as a {@link LazyBodyReader}.
     */
    public abstract LazyBodyReader toBodyReader();

    /**
     * @param headers headers object to adapt to include this HTTP message body.
     * @return adjusted headers for this HTTP message body.
     * The Content-Type and Content-Length headers may be modified to fit a HTTP message
     * containing this body.
     */
    public RawHttpHeaders headersFrom(RawHttpHeaders headers) {
        RawHttpHeaders.Builder builder = RawHttpHeaders.newBuilder(headers);
        getContentType().ifPresent(contentType -> builder.overwrite("Content-Type", contentType));
        getContentLength().ifPresent(length -> builder.overwrite("Content-Length", Long.toString(length)));
        return builder.build();
    }

}
