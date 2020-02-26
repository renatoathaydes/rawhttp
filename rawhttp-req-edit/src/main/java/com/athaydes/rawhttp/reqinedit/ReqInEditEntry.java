package com.athaydes.rawhttp.reqinedit;

import rawhttp.core.RawHttpRequest;

import javax.annotation.Nullable;
import java.util.Optional;

public final class ReqInEditEntry {
    private final RawHttpRequest request;

    @Nullable
    private final String script;

    public ReqInEditEntry(RawHttpRequest request,
                          @Nullable String script) {
        this.request = request;
        this.script = script;
    }

    public RawHttpRequest getRequest() {
        return request;
    }

    public Optional<String> getScript() {
        return Optional.ofNullable(script);
    }
}
