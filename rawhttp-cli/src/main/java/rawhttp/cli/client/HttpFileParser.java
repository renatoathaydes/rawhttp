package rawhttp.cli.client;

import rawhttp.core.RawHttp;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public final class HttpFileParser {
    private final RawHttp http;

    public HttpFileParser(RawHttp http) {
        this.http = http;
    }

    public List<HttpFileEntry> parse(File httpFile, @Nullable File envFile) throws IOException {
        return parse(new FileInputStream(httpFile), envFile == null ? null : new FileInputStream(envFile));
    }

    public List<HttpFileEntry> parse(InputStream httpFile, @Nullable InputStream envFile) throws IOException {
        HttpFileEntry entry = new HttpFileEntry(http.parseRequest(httpFile), null);
        return Collections.singletonList(entry);
    }
}
