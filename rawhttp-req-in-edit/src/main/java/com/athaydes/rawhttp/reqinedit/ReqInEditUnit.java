package com.athaydes.rawhttp.reqinedit;

import com.athaydes.rawhttp.reqinedit.js.JsEnvironment;
import rawhttp.cookies.ClientOptionsWithCookies;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpOptions;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.body.BytesBody;
import rawhttp.core.client.RawHttpClient;
import rawhttp.core.client.TcpRawHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A unit representing a single HTTP file.
 * <p>
 * A HTTP file may contain one or more HTTP requests, as well as response handlers and references to other
 * files (to either populate request bodies or store responses). These references are resolved at runtime
 * (i.e. when the {@link ReqInEditUnit#run(List)} or {@link ReqInEditUnit#run(ReqInEditEntry)} methods are called.
 * <p>
 * This class can run {@link ReqInEditEntry} instances, which are normally obtained via the {@link ReqInEditParser}
 * class by parsing a HTTP file.
 * <p>
 * A {@link ReqInEditUnit} may be run one or more times. The caller is expected to close it once it's done
 * running requests.
 * <p>
 * The {@link TcpRawHttpClient} and {@link HttpTestsReporter} used by this unit are closed when this unit
 * is closed.
 */
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
        this(environment, HTTP, new TcpRawHttpClient(new ClientOptionsWithCookies(), HTTP),
                new DefaultFileReader(), new FileResponseStorage(),
                new DefaultTestReporter());
    }

    public ReqInEditUnit(HttpEnvironment environment,
                         RawHttp http,
                         RawHttpClient<?> httpClient) {
        this(environment, http, httpClient,
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

    /**
     * Run all of the given entries.
     * <p>
     * The entries are run in order until either they all execute without errors, or an error occurs,
     * or a response handler test fails.
     * <p>
     * The results of running an entry can be used by its associated response handler to modify subsequent
     * requests.
     *
     * @param entries to run
     * @return true if all tests passed or there was no tests, false if any test failed.
     */
    public boolean run(List<ReqInEditEntry> entries) {
        for (ReqInEditEntry entry : entries) {
            boolean allTestsPass = run(entry);
            if (!allTestsPass) {
                return false;
            }
        }
        return true;
    }

    /**
     * Run a single entry of a HTTP file.
     *
     * @param entry to run
     * @return true if all tests passed or there was no tests, false if any test failed.
     */
    public boolean run(ReqInEditEntry entry) {
        RawHttpResponse<?> response;
        try {
            response = httpClient.send(toRequest(entry)).eagerly();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        entry.getResponseRef().ifPresent(responseRef -> storeResponse(responseRef, response));
        return entry.getScript()
                .map(script -> runResponseScript(script, response))
                .orElse(true);
    }

    private RawHttpRequest toRequest(ReqInEditEntry entry) throws IOException {
        String requestTop = environment.renderTemplate(entry.getRequest());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);
        entry.getRequestBody().stream().map(b -> b.match(
                text -> environment.renderTemplate(text).getBytes(StandardCharsets.UTF_8),
                this::inputFile)
        ).forEach(b -> {
            try {
                buffer.write(b);
            } catch (IOException e) {
                // cannot occur - memory buffer
            }
        });

        RawHttpRequest request = http.parseRequest(requestTop);
        if (buffer.size() > 0) {
            request = request.withBody(new BytesBody(buffer.toByteArray())).eagerly();
        }
        return request;
    }

    private void storeResponse(String responseRef,
                               RawHttpResponse<?> response) {
        try {
            responseStorage.store(response, responseRef);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean runResponseScript(StringOrFile script,
                                      RawHttpResponse<?> response) {
        try {
            String scriptText = script.match(text -> text, file -> new String(inputFile(file)));
            return environment.runResponseHandler(scriptText, response, testsReporter);
        } catch (IOException | ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] inputFile(String path) {
        try {
            return fileReader.read(environment.resolvePath(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            testsReporter.close();
        } finally {
            if (httpClient instanceof Closeable) {
                ((Closeable) httpClient).close();
            }
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
