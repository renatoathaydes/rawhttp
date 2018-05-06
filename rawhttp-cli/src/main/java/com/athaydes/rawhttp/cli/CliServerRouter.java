package com.athaydes.rawhttp.cli;

import com.athaydes.rawhttp.core.RawHttpHeaders;
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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Map.entry;

final class CliServerRouter implements Router {

    private static final Map<String, String> mimeMapping;

    static {
        mimeMapping = Map.ofEntries(
                entry("html", "text/html"),
                entry("txt", "text/plain"),
                entry("json", "application/json"),
                entry("js", "application/javascript"),
                entry("xml", "application/xml"),
                entry("jpg", "image/jpeg"),
                entry("jpeg", "image/jpeg"),
                entry("gif", "image/gif"),
                entry("png", "image/png"),
                entry("tif", "image/tiff"),
                entry("tiff", "image/tiff"),
                entry("ico", "image/x-icon"),
                entry("pdf", "application/pdf"),
                entry("css", "text/css")
        );
    }

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
            String path = request.getStartLine().getUri().normalize().getPath().replaceAll("../", "");

            // provide the index.html file at the root path
            if (path.isEmpty() || path.equals("/")) {
                path = "index.html";
            }

            File resource = new File(rootDir, path);
            if (resource.isFile()) {
                response = HttpResponses.getOkResponse(request.getStartLine().getHttpVersion())
                        .withHeaders(contentTypeHeaderFor(resource.getName()))
                        .replaceBody(new FileBody(resource));
            } else {
                response = null; // 404 - NOT FOUND
            }
        } else {
            response = HttpResponses.getMethodNotAllowedResponse(request.getStartLine().getHttpVersion());
        }
        requestLogger.logRequest(request, response);
        return response;
    }

    private static RawHttpHeaders contentTypeHeaderFor(String resourceName) {
        return RawHttpHeaders.Builder.newBuilder()
                .with("Content-Type", mimeTypeOf(resourceName))
                .build();
    }

    static String mimeTypeOf(String resourceName) {
        int idx = resourceName.lastIndexOf('.');
        if (idx < 0 || idx == resourceName.length() - 1) {
            return "application/octet-stream";
        }
        String extension = resourceName.substring(idx + 1);
        return mimeMapping.getOrDefault(extension, "application/octet-stream");
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
            .ofPattern("d/MMM/yyyy:HH:mm:ss z")
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

