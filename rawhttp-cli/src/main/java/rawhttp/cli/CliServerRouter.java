package rawhttp.cli;

import rawhttp.cli.FileLocator.FileResult;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.body.FileBody;
import rawhttp.core.server.Router;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

final class CliServerRouter implements Router {

    static final Map<String, String> mimeByFileExtension;
    public static final String DIR_BACK_PATTERN = Pattern.quote("../");

    static {
        Map<String, String> _mimeMapping = new HashMap<>(13);

        _mimeMapping.put("html", "text/html");
        _mimeMapping.put("txt", "text/plain");
        _mimeMapping.put("json", "application/json");
        _mimeMapping.put("js", "application/javascript");
        _mimeMapping.put("xml", "text/xml");
        _mimeMapping.put("jpg", "image/jpeg");
        _mimeMapping.put("jpeg", "image/jpeg");
        _mimeMapping.put("gif", "image/gif");
        _mimeMapping.put("png", "image/png");
        _mimeMapping.put("tif", "image/tiff");
        _mimeMapping.put("tiff", "image/tiff");
        _mimeMapping.put("ico", "image/x-icon");
        _mimeMapping.put("pdf", "application/pdf");
        _mimeMapping.put("css", "text/css");
        _mimeMapping.put("svg", "image/svg+xml");

        mimeByFileExtension = Collections.unmodifiableMap(_mimeMapping);
    }

    private final FileLocator fileLocator;
    private final PathReader pathReader;

    CliServerRouter(File rootDir, String rootPath) {
        this(rootDir, rootPath, mimeByFileExtension);
    }

    CliServerRouter(File rootDir, String rootPath, Properties mediaTypes) {
        this(rootDir, rootPath, convertToMap(mediaTypes));
    }

    private CliServerRouter(File rootDir, String rootPath, Map<String, String> mimeMapping) {
        this.fileLocator = new FileLocator(rootDir, mimeMapping);
        this.pathReader = rootPath.isEmpty() ? new StandardPathReader() : new ContextPathReader(rootPath);
    }

    private static Map<String, String> convertToMap(Properties mediaTypes) {
        Map<String, String> mimeMapping = new HashMap<>(mimeByFileExtension);
        mediaTypes.forEach((ext, mime) -> mimeMapping.put(ext.toString(), mime.toString()));
        return mimeMapping;
    }

    @Override
    public Optional<RawHttpResponse<?>> route(RawHttpRequest request) {
        final Optional<RawHttpResponse<?>> response;
        if (request.getMethod().equals("GET") || request.getMethod().equals("HEAD")) {
            String path = pathReader.readPath(request);
            if (path == null) {
                return Optional.empty();
            }

            // provide the index.html file at the root path
            if (path.isEmpty() || path.equals("/")) {
                path = "index.html";
            }

            Optional<FileResult> resource = fileLocator.find(path, request.getHeaders().get("Accept"));

            response = resource.map(fileResult ->
                    HttpResponses.getOkResponse(request.getStartLine().getHttpVersion())
                            .withHeaders(fileResult.contentTypeHeader)
                            .withBody(new FileBody(fileResult.file)));
        } else {
            response = Optional.of(HttpResponses.getMethodNotAllowedResponse(request.getStartLine().getHttpVersion()));
        }
        return response;
    }

    private interface PathReader {
        /**
         * @param request the received request
         * @return the path to use to look up for a resource, or null if the path was definitely
         * not mapped to a resource.
         */
        default String readPath(RawHttpRequest request) {
            return request.getStartLine().getUri().normalize()
                    .getPath().replaceAll(DIR_BACK_PATTERN, "");
        }
    }

    private static final class StandardPathReader implements PathReader {
    }

    private static final class ContextPathReader implements PathReader {
        private final String rootPath;

        ContextPathReader(String rootPath) {
            this.rootPath = rootPath.startsWith("/") ? rootPath : "/" + rootPath;
        }

        @Override
        public String readPath(RawHttpRequest request) {
            String path = PathReader.super.readPath(request);
            if (!path.startsWith(rootPath)) {
                return null;
            } else {
                return path.substring(rootPath.length());
            }
        }
    }

}

