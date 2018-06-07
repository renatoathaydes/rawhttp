package rawhttp.core.body;

import java.nio.charset.Charset;
import javax.annotation.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A simple {@link HttpMessageBody} whose contents are given by a String.
 */
public class StringBody extends BytesBody {

    private final Charset charset;

    /**
     * Create a {@link HttpMessageBody} whose contents are the given String.
     *
     * @param body the contents of this message
     */
    public StringBody(String body) {
        this(body, null, null, UTF_8);
    }

    /**
     * Create a {@link HttpMessageBody} whose contents are the given String.
     *
     * @param body        the contents of this message
     * @param contentType the Content-Type of this message
     */
    public StringBody(String body,
                      @Nullable String contentType) {
        this(body, contentType, null, UTF_8);
    }

    /**
     * Create a {@link HttpMessageBody} whose contents are the given String.
     * <p>
     * The body is assumed to be in encoded form and can be decoded with the provided {@link BodyDecoder}.
     *
     * @param body        the contents of this message
     * @param contentType the Content-Type of this message
     * @param bodyDecoder decoder capable of decoding the body
     * @param charset     the {@link Charset} of the body
     */
    public StringBody(String body,
                      @Nullable String contentType,
                      @Nullable BodyDecoder bodyDecoder,
                      Charset charset) {
        super(body.getBytes(charset), contentType, bodyDecoder);
        this.charset = charset;
    }

    /**
     * @return the charset of this HTTP message's body.
     */
    public Charset getCharset() {
        return charset;
    }

}
