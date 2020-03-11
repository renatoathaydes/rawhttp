package com.athaydes.rawhttp.reqinedit;

import rawhttp.core.RawHttpRequest;

import javax.annotation.Nullable;
import java.util.Optional;

public final class ReqInEditEntry {
    private final RawHttpRequest request;

    @Nullable
    private final String script;
    @Nullable
    private final String responseRef;

    public ReqInEditEntry(RawHttpRequest request,
                          @Nullable String script,
                          @Nullable String responseRef) {
        this.request = request;
        this.script = script;
        this.responseRef = responseRef;
    }

    public RawHttpRequest getRequest() {
        return request;
    }

    public Optional<String> getScript() {
        return Optional.ofNullable(script);
    }

    public Optional<String> getResponseRef() {
        return Optional.ofNullable(responseRef);
    }
}
