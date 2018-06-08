package rawhttp.core.body;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.OptionalLong;
import javax.annotation.Nullable;

/**
 * A {@link HttpMessageBody} containing the contents of a {@link File}.
 */
public class FileBody extends HttpMessageBody {

    private final File file;

    /**
     * Create a {@link HttpMessageBody} whose contents are provided by the given file.
     *
     * @param file the file whose contents form this message
     */
    public FileBody(File file) {
        this(file, null, null);
    }

    /**
     * Create a {@link HttpMessageBody} whose contents are provided by the given file.
     *
     * @param file        the file whose contents form this message
     * @param contentType Content-Type of the body
     */
    public FileBody(File file,
                    @Nullable String contentType) {
        this(file, contentType, null);
    }

    /**
     * Create a {@link HttpMessageBody} whose contents are provided by the given file.
     * <p>
     * The body is assumed to be in encoded form and can be decoded with the provided {@link BodyDecoder}.
     *
     * @param file        the file whose contents form this message
     * @param contentType Content-Type of the body
     * @param bodyDecoder decoder capable of decoding the body
     */
    public FileBody(File file,
                    @Nullable String contentType,
                    @Nullable BodyDecoder bodyDecoder) {
        super(contentType, bodyDecoder);
        this.file = file;
    }

    /**
     * @return the file associated with this instance.
     */
    public File getFile() {
        return file;
    }

    @Override
    public LazyBodyReader toBodyReader() {
        try {
            return new LazyBodyReader(
                    new FramedBody.ContentLength(getBodyDecoder(), file.length()),
                    new BufferedInputStream(Files.newInputStream(file.toPath())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected OptionalLong getContentLength() {
        return OptionalLong.of(file.length());
    }

}
