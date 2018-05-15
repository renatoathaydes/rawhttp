package rawhttp.cli;

import java.io.File;
import java.util.Optional;
import rawhttp.cli.FileLocator.FileResult;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.body.FileBody;
import rawhttp.core.server.Router;

final class CliServerRouter implements Router {

    private final FileLocator fileLocator;

    CliServerRouter(File rootDir) {
        this.fileLocator = new FileLocator(rootDir);
    }

    @Override
    public Optional<RawHttpResponse<?>> route(RawHttpRequest request) {
        final Optional<RawHttpResponse<?>> response;
        if (request.getMethod().equals("GET")) {
            String path = request.getStartLine().getUri().normalize().getPath().replaceAll("../", "");

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

