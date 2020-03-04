package com.athaydes.rawhttp.reqinedit;

import rawhttp.core.RawHttpResponse;
import rawhttp.core.client.RawHttpClient;
import rawhttp.core.client.TcpRawHttpClient;

import javax.script.ScriptException;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public class ReqInEditUnit implements Runnable, Closeable, AutoCloseable {
    private final List<ReqInEditEntry> entries;
    private final HttpEnvironment environment;

    private final RawHttpClient<?> httpClient;

    public ReqInEditUnit(List<ReqInEditEntry> entries,
                         HttpEnvironment environment) {
        this(entries, environment, new TcpRawHttpClient());
    }

    public ReqInEditUnit(List<ReqInEditEntry> entries,
                         HttpEnvironment environment,
                         RawHttpClient<?> httpClient) {
        this.entries = entries;
        this.environment = environment;
        this.httpClient = httpClient;
    }

    public List<ReqInEditEntry> getEntries() {
        return entries;
    }

    @Override
    public void run() {
        for (ReqInEditEntry entry : entries) {
            run(entry);
        }
    }

    public void run(ReqInEditEntry entry) {
        RawHttpResponse<?> response;
        try {
            response = httpClient.send(entry.getRequest());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        entry.getResponseRef().ifPresent(responseRef -> storeResponse(responseRef, response));
        entry.getScript().ifPresent(script -> runResponseScript(script, response));
    }

    private void storeResponse(String responseRef,
                               RawHttpResponse<?> response) {
        // TODO
    }

    private void runResponseScript(String script, RawHttpResponse<?> response) {
        List<String> errors;
        try {
            errors = environment.runResponseHandler(script, response);
        } catch (IOException | ScriptException e) {
            throw new RuntimeException(e);
        }

        if (!errors.isEmpty()) {
            throw new RuntimeException(String.join(", ", errors));
        }
    }

    @Override
    public void close() throws IOException {
        if (httpClient instanceof Closeable) {
            ((Closeable) httpClient).close();
        }
    }
}
