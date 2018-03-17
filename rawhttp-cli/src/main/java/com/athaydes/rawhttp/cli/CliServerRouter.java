package com.athaydes.rawhttp.cli;

import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;
import com.athaydes.rawhttp.core.body.FileBody;
import com.athaydes.rawhttp.core.server.Router;

import java.io.File;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CliServerRouter implements Router {

    private static final RawHttp http = new RawHttp();

    private final File rootDir;
    private final RequestLogger requestLogger;

    CliServerRouter(File rootDir, boolean logRequests) {
        this.rootDir = rootDir;
        this.requestLogger = logRequests ?
                new AsyncSysoutRequestLogger() :
                new NoopRequestLogger();
    }

    @Override
    public RawHttpResponse<?> route(RawHttpRequest request) {
        final RawHttpResponse<Void> response;
        if (request.getMethod().equals("GET")) {
            String path = request.getStartLine().getUri().getPath();
            File resource = new File(rootDir, path);
            if (resource.isFile()) {
                response = http.parseResponse(request.getStartLine().getHttpVersion() +
                        " 200 OK\n" +
                        "Content-Type: application/octet-stream\n" +
                        "Server: RawHTTP")
                        .replaceBody(new FileBody(resource));
            } else {
                response = http.parseResponse(request.getStartLine().getHttpVersion() +
                        " 404 Not Found\n" +
                        "Content-Length: 24\n" +
                        "Content-Type: plain/text\n" +
                        "Server: RawHTTP\n\n" +
                        "Resource does not exist.");
            }
        } else {
            response = http.parseResponse(request.getStartLine().getHttpVersion() +
                    " 405 Method Not Allowed\n" +
                    "Content-Length: 19\n" +
                    "Content-Type: plain/text\n" +
                    "Server: RawHTTP\n\n" +
                    "Method not allowed.");
        }
        requestLogger.logRequest(request, response);
        return response;
    }

}

interface RequestLogger {
    void logRequest(RawHttpRequest request,
                    RawHttpResponse<?> response);
}

final class NoopRequestLogger implements RequestLogger {
    @Override
    public void logRequest(RawHttpRequest request, RawHttpResponse<?> response) {
        // noop
    }
}

final class AsyncSysoutRequestLogger implements RequestLogger {

    private static final DateTimeFormatter dateFormat = DateTimeFormatter
            .ofPattern("d/MMM/Y:HH:mm:ss z")
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault());

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setName("async-sysout-request-logger");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void logRequest(RawHttpRequest request, RawHttpResponse<?> response) {
        executor.submit(() -> {
            if (request.getSenderAddress().isPresent()) {
                InetAddress senderAddress = request.getSenderAddress().get();
                System.out.print(senderAddress.getHostAddress() + " ");
            }
            Long bytes = response.getBody()
                    .map(b -> b.getLengthIfKnown().orElse(-1L))
                    .orElse(-1L);
            System.out.println("[" + LocalDateTime.now().format(dateFormat) + "] \"" +
                    request.getStartLine() + "\" " + response.getStatusCode() +
                    " " + bytes);
        });
    }

}

