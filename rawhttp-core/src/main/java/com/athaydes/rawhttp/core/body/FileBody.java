package com.athaydes.rawhttp.core.body;

import com.athaydes.rawhttp.core.BodyReader;
import com.athaydes.rawhttp.core.LazyBodyReader;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * A {@link HttpMessageBody} containing the contents of a {@link File}.
 */
public class FileBody extends HttpMessageBody {

    private final File file;
    private final boolean allowNewLineWithoutReturn;

    public FileBody(File file) {
        this(file, null, false);
    }

    public FileBody(File file,
                    @Nullable String contentType,
                    boolean allowNewLineWithoutReturn) {
        super(contentType);
        this.file = file;
        this.allowNewLineWithoutReturn = allowNewLineWithoutReturn;
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
            return new LazyBodyReader(BodyReader.BodyType.CONTENT_LENGTH,
                    new BufferedInputStream(new FileInputStream(file)),
                    file.length(),
                    allowNewLineWithoutReturn);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected long getContentLength() {
        return file.length();
    }

}
