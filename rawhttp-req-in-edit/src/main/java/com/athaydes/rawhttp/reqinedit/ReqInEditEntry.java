package com.athaydes.rawhttp.reqinedit;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * A single HTTP request in a HTTP file.
 * <p>
 * It may also contain a response handler script and a response reference (where the HTTP response resulting from
 * running the HTTP request will be written).
 */
public final class ReqInEditEntry {

    private final String request;
    private final List<StringOrFile> requestBody;
    @Nullable
    private final StringOrFile script;
    @Nullable
    private final String responseRef;

    public ReqInEditEntry(String request,
                          List<StringOrFile> requestBody,
                          @Nullable StringOrFile script,
                          @Nullable String responseRef) {
        this.request = request;
        this.requestBody = requestBody;
        this.script = script;
        this.responseRef = responseRef;
    }

    public String getRequest() {
        return request;
    }

    public List<StringOrFile> getRequestBody() {
        return requestBody;
    }

    public Optional<StringOrFile> getScript() {
        return Optional.ofNullable(script);
    }

    public Optional<String> getResponseRef() {
        return Optional.ofNullable(responseRef);
    }

}

