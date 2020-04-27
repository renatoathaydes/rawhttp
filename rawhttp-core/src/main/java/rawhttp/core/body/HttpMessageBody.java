package rawhttp.core.body;

import rawhttp.core.HttpMessage;
import rawhttp.core.RawHttpHeaders;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A HTTP message's body.
 *
 * @see HttpMessage
 */
public abstract class HttpMessageBody {

    @Nullable
    private final String contentType;
    @Nullable
    private final BodyDecoder bodyDecoder;

    protected HttpMessageBody(@Nullable String contentType,
                              @Nullable BodyDecoder bodyDecoder) {
        this.contentType = contentType;
        this.bodyDecoder = bodyDecoder;
    }

    /**
     * @return the Content-Type header associated with this message, if available.
     */
    public Optional<String> getContentType() {
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
     * @return the {@link BodyDecoder} capable of decoding this message's body.
     * <p>
     * If the message body is not encoded, a no-op decoder is returned.
     */
    public BodyDecoder getBodyDecoder() {
        return bodyDecoder == null ? new BodyDecoder() : bodyDecoder;
    }

    /**
     * @param headers headers object to adapt to include this HTTP message body.
     * @return adjusted headers for this HTTP message body.
     * The Content-Type and Content-Length headers may be modified to fit a HTTP message
     * containing this body. If the body is encoded, the Transfer-Encoding header will be set.
     */
    public RawHttpHeaders headersFrom(RawHttpHeaders headers) {
        RawHttpHeaders.Builder builder = RawHttpHeaders.newBuilderSkippingValidation(headers);
        getContentType().ifPresent(contentType -> builder.overwrite("Content-Type", contentType));
        getContentLength().ifPresent(length -> builder.overwrite("Content-Length", Long.toString(length)));
        Optional.ofNullable(bodyDecoder).ifPresent(decoder ->
        {
            if (!decoder.getEncodings().isEmpty()) {
                builder.overwrite("Transfer-Encoding", String.join(",", decoder.getEncodings()));
            }
        });
        return builder.build();
    }

    /**
     * Return a copy of the given headers, excluding the typical HTTP headers related to its body.
     * <p>
     * Currently, the removed headers are:
     * <ul>
     *     <li>Content-Type</li>
     *     <li>Content-Length</li>
     *     <li>Transfer-Encoding</li>
     * </ul>
     *
     * @param headers source headers
     * @return headers without body-specific headers
     * @see HttpMessageBody#headersFrom(RawHttpHeaders)
     */
    public static RawHttpHeaders removeBodySpecificHeaders(RawHttpHeaders headers) {
        return RawHttpHeaders.newBuilderSkippingValidation(headers)
                .remove("Content-Type")
                .remove("Content-Length")
                .remove("Transfer-Encoding")
                .build();
    }

}
