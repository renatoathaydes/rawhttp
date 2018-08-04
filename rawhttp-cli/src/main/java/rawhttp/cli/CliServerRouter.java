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

        mimeByFileExtension = Collections.unmodifiableMap(_mimeMapping);
    }

    private final FileLocator fileLocator;

    CliServerRouter(File rootDir) {
        this.fileLocator = new FileLocator(rootDir, mimeByFileExtension);
    }

    CliServerRouter(File rootDir, Properties mediaTypes) {
        Map<String, String> mimeMapping = new HashMap<>(mimeByFileExtension);
        mediaTypes.forEach((ext, mime) -> mimeMapping.put(ext.toString(), mime.toString()));
        this.fileLocator = new FileLocator(rootDir, mimeMapping);
    }

    @Override
    public Optional<RawHttpResponse<?>> route(RawHttpRequest request) {
        final Optional<RawHttpResponse<?>> response;
        if (request.getMethod().equals("GET")) {
            String path = request.getStartLine().getUri()
                    .normalize().getPath()
                    .replaceAll(DIR_BACK_PATTERN, "");

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

}

