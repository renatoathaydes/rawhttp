package rawhttp.cli.client;

import rawhttp.core.RawHttpRequest;

import javax.annotation.Nullable;

public final class HttpFileEntry {
    final RawHttpRequest request;
    @Nullable
    final String script;

    public HttpFileEntry(RawHttpRequest request,
                         @Nullable String script) {
        this.request = request;
        this.script = script;
    }

}
