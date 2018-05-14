package rawhttp.core.body;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

/**
 * A simple {@link HttpMessageBody} whose contents are given by a String.
 */
public class StringBody extends BytesBody {

    private final Charset charset;

    public StringBody(String body) {
        this(body, null);
    }

    public StringBody(String body,
                      @Nullable String contentType) {
        this(body, contentType, StandardCharsets.UTF_8);
    }

    public StringBody(String body,
                      @Nullable String contentType,
                      Charset charset) {
        super(body.getBytes(charset), contentType);
        this.charset = charset;
    }

    /**
     * @return the charset of this HTTP message's body.
     */
    public Charset getCharset() {
        return charset;
    }

}
