package com.athaydes.rawhttp.reqinedit;

import com.athaydes.rawhttp.reqinedit.js.JsEnvironment;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpOptions;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.body.BytesBody;
import rawhttp.core.client.RawHttpClient;
import rawhttp.core.client.TcpRawHttpClient;

import javax.script.ScriptException;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ReqInEditUnit implements Closeable, AutoCloseable {

    private static final RawHttp HTTP = new RawHttp(RawHttpOptions.newBuilder()
            .allowComments()
            .allowIllegalStartLineCharacters()
            .build());

    private final RawHttp http;
    private final FileReader fileReader;
    private final ResponseStorage responseStorage;
    private final RawHttpClient<?> httpClient;
    private final HttpTestsReporter testsReporter;
    private final HttpEnvironment environment;

    public ReqInEditUnit() {
        this(new JsEnvironment());
    }

    public ReqInEditUnit(HttpEnvironment environment) {
        this(environment, HTTP, new TcpRawHttpClient(null, HTTP),
                new DefaultFileReader(), new FileResponseStorage(),
                new DefaultTestReporter());
    }

    public ReqInEditUnit(HttpEnvironment environment,
                         RawHttp http,
                         RawHttpClient<?> httpClient,
                         FileReader fileReader,
                         ResponseStorage responseStorage,
                         HttpTestsReporter testsReporter) {
        this.http = http;
        this.httpClient = httpClient;
        this.fileReader = fileReader;
        this.responseStorage = responseStorage;
        this.testsReporter = testsReporter;
        this.environment = environment;
    }

    public void run(List<ReqInEditEntry> entries) {
        for (ReqInEditEntry entry : entries) {
            run(entry);
        }
    }

    private void run(ReqInEditEntry entry) {
        RawHttpResponse<?> response;
        try {
            response = httpClient.send(toRequest(entry)).eagerly();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        entry.getResponseRef().ifPresent(responseRef -> storeResponse(responseRef, response));
        entry.getScript().ifPresent(script -> runResponseScript(script, response, testsReporter));
    }

    private RawHttpRequest toRequest(ReqInEditEntry entry) {
        String requestTop = environment.renderTemplate(entry.getRequest());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);
        entry.getRequestBody().stream().map(b -> b.match(
                text -> environment.renderTemplate(text).getBytes(StandardCharsets.UTF_8),
                file -> inputFile(file, environment))
        ).forEach(b -> {
            try {
                buffer.write(b);
            } catch (IOException e) {
                // cannot occur - memory buffer
            }
        });
        return http.parseRequest(requestTop).withBody(new BytesBody(buffer.toByteArray()));
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

    private byte[] inputFile(String path, HttpEnvironment environment) {
        try {
            return fileReader.read(environment.resolvePath(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        if (httpClient instanceof Closeable) {
            ((Closeable) httpClient).close();
        }
    }

    private static final class DefaultFileReader implements FileReader {
        @Override
        public byte[] read(Path path) throws IOException {
            return Files.readAllBytes(path);
        }
    }

    private static final class FileResponseStorage implements ResponseStorage {
        @Override
        public void store(RawHttpResponse<?> response, String responseRef) throws IOException {
            try (FileOutputStream out = new FileOutputStream(responseRef)) {
                response.writeTo(out);
            }
        }
    }

    private static final class DefaultTestReporter implements HttpTestsReporter {
        @Override
        public void report(HttpTestResult result) {
            long time = result.getEndTime() - result.getStartTime();
            if (result.isSuccess()) {
                System.out.println("TEST OK (" + time + "ms): " + result.getName());
            } else {
                System.out.println("TEST FAILED (" + time + "ms): " + result.getName());
                if (!"".equals(result.getError())) {
                    System.err.println(result.getError());
                }
            }
        }
    }

}
