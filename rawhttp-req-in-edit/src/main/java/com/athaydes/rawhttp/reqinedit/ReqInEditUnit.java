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
    private final ResponseStorage responseStorage;
    private final TcpRawHttpClient defaultHttpClient = new TcpRawHttpClient();

    public ReqInEditUnit(List<ReqInEditEntry> entries,
                         HttpEnvironment environment) {
        this(entries, environment, new FileResponseStorage());
    }

    public ReqInEditUnit(List<ReqInEditEntry> entries,
                         HttpEnvironment environment,
                         ResponseStorage responseStorage) {
        this.entries = entries;
        this.environment = environment;
        this.responseStorage = responseStorage;
    }

    public List<ReqInEditEntry> getEntries() {
        return entries;
    }

    @Override
    public void run() {
        HttpTestsReporter testsReporter = result -> {
            if (result.isSuccess()) {
                System.out.println("Test passed: " + result.getName());
            } else {
                System.out.println("Test FAILED: " + result.getName() + ": " + result.getError());
            }
        };
        runWith(defaultHttpClient, testsReporter);
    }

    public void runWith(RawHttpClient<?> httpClient, HttpTestsReporter testsReporter) {
        for (ReqInEditEntry entry : entries) {
            run(entry, httpClient, testsReporter);
        }
    }

    private void run(ReqInEditEntry entry,
                     RawHttpClient<?> httpClient,
                     HttpTestsReporter testsReporter) {
        RawHttpResponse<?> response;
        try {
            response = httpClient.send(entry.getRequest()).eagerly();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        entry.getResponseRef().ifPresent(responseRef -> storeResponse(responseRef, response));
        entry.getScript().ifPresent(script -> runResponseScript(script, response, testsReporter));
    }

    private void storeResponse(String responseRef,
                               RawHttpResponse<?> response) {
        try {
            responseStorage.store(response, responseRef);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void runResponseScript(String script,
                                   RawHttpResponse<?> response,
                                   HttpTestsReporter testsReporter) {
        try {
            environment.runResponseHandler(script, response, testsReporter);
        } catch (IOException | ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        defaultHttpClient.close();
    }
}
