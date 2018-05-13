package com.athaydes.rawhttp.cli;

import com.athaydes.rawhttp.core.RawHttpHeaders;
import com.athaydes.rawhttp.core.RawHttpRequest;
import com.athaydes.rawhttp.core.RawHttpResponse;
import com.athaydes.rawhttp.core.body.FileBody;
import com.athaydes.rawhttp.core.server.Router;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class CliServerRouter implements Router {

    private static final Map<String, String> mimeMapping;

    static {
        Map<String, String> _mimeMapping = new HashMap<>(13);

        _mimeMapping.put("html", "text/html");
        _mimeMapping.put("txt", "text/plain");
        _mimeMapping.put("json", "application/json");
        _mimeMapping.put("js", "application/javascript");
        _mimeMapping.put("xml", "application/xml");
        _mimeMapping.put("jpg", "image/jpeg");
        _mimeMapping.put("jpeg", "image/jpeg");
        _mimeMapping.put("gif", "image/gif");
        _mimeMapping.put("png", "image/png");
        _mimeMapping.put("tif", "image/tiff");
        _mimeMapping.put("tiff", "image/tiff");
        _mimeMapping.put("ico", "image/x-icon");
        _mimeMapping.put("pdf", "application/pdf");
        _mimeMapping.put("css", "text/css");

        mimeMapping = Collections.unmodifiableMap(_mimeMapping);
    }

    private final File rootDir;

    CliServerRouter(File rootDir) {
        this.rootDir = rootDir;
    }

    @Override
    public Optional<RawHttpResponse<?>> route(RawHttpRequest request) {
        final RawHttpResponse<Void> response;
        if (request.getMethod().equals("GET")) {
            String path = request.getStartLine().getUri().normalize().getPath().replaceAll("../", "");

            // provide the index.html file at the root path
            if (path.isEmpty() || path.equals("/")) {
                path = "index.html";
            }

            // TODO try to find out a file extension based on the Accept header
            // if no extension is provided in the path
            File resource = new File(rootDir, path);
            if (resource.isFile()) {
                response = HttpResponses.getOkResponse(request.getStartLine().getHttpVersion())
                        .withHeaders(contentTypeHeaderFor(resource.getName()))
                        .withBody(new FileBody(resource));
            } else {
                response = null; // 404 - NOT FOUND
            }
        } else {
            response = HttpResponses.getMethodNotAllowedResponse(request.getStartLine().getHttpVersion());
        }
        return Optional.ofNullable(response);
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

