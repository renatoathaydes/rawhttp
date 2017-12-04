package com.athaydes.rawhttp.core.file;

import com.athaydes.rawhttp.core.BodyReader;
import com.athaydes.rawhttp.core.LazyBodyReader;
import com.athaydes.rawhttp.core.RawHttpHeaders;
import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Optional;

public class FileBody {

    private final File file;

    @Nullable
    private final String contentType;

    public FileBody(File file) {
        this(file, null);
    }

    public FileBody(File file, @Nullable String contentType) {
        this.file = file;
        this.contentType = contentType;
    }

    public File getFile() {
        return file;
    }

    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    public RawHttpRequest setFileAsBodyOf(RawHttpRequest request) throws FileNotFoundException {
        return new RawHttpRequest(request.getStartLine(),
                headersFrom(request.getHeaders()),
                toBodyReader());
    }

    public <Response> RawHttpResponse<Response> setFileAsBodyOf(
            RawHttpResponse<Response> response) throws FileNotFoundException {
        return new RawHttpResponse<>(response.getLibResponse().orElse(null),
                response.getRequest().orElse(null),
                response.getStartLine(),
                headersFrom(response.getHeaders()),
                toBodyReader());
    }

    public LazyBodyReader toBodyReader() throws FileNotFoundException {
        return new LazyBodyReader(BodyReader.BodyType.CONTENT_LENGTH,
                new BufferedInputStream(new FileInputStream(file)),
                file.length());
    }

    private RawHttpHeaders headersFrom(RawHttpHeaders headers) {
        RawHttpHeaders.Builder builder = RawHttpHeaders.Builder.newBuilder(headers);
        if (contentType != null) {
            builder.overwrite("Content-Type", contentType);
        }
        return builder.overwrite("Content-Length", Long.toString(file.length())).build();

    }

}
