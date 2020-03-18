package rawhttp.core.client;

import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.RequestLine;
import rawhttp.core.UriUtil;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

public class RedirectingRawHttpClient<Response> implements RawHttpClient<Response> {
    private final RawHttpClient<Response> delegate;
    private final int maxRedirects;

    public RedirectingRawHttpClient(RawHttpClient<Response> delegate) {
        this(delegate, 4);
    }

    public RedirectingRawHttpClient(RawHttpClient<Response> delegate, int maxRedirects) {
        if (maxRedirects < 1) {
            throw new IllegalArgumentException("maxRedirects must be at least 1");
        }
        this.delegate = delegate;
        this.maxRedirects = maxRedirects;
    }

    @Override
    public RawHttpResponse<Response> send(RawHttpRequest request) throws IOException {
        int totalRedirects = 0;
        Set<String> locations = new LinkedHashSet<>(maxRedirects);
        RawHttpResponse<Response> response;
        while (true) {
            response = delegate.send(request);
            if (isRedirectCode(response.getStatusCode())) {
                response = response.eagerly(); // consume body
                totalRedirects++;
                if (totalRedirects >= maxRedirects) {
                    throw new IllegalStateException("Too many redirects");
                }
                String location = response.getHeaders().getFirst("Location").orElse("");
                if (location.isEmpty()) {
                    break; // cannot follow redirect automatically
                } else {
                    if (!locations.add(location)) {
                        throw new IllegalStateException("Redirect cycle detected. " +
                                "Visited locations: " + String.join(", ", locations) + ". Next location: " + location);
                    }
                    request = redirect(request, location);
                }
            } else {
                break; // not a redirect
            }
        }
        return response;
    }

    private RawHttpRequest redirect(RawHttpRequest request, String location) {
        URI newUri;
        if (location.matches("^http(s)?://.*")) {
            newUri = URI.create(location);
        } else {
            // the location starts from the path, which may be relative or absolute
            String path;
            if (location.startsWith("/")) {
                path = location;
            } else {
                path = UriUtil.concatPaths(request.getUri().getPath(), location);
            }
            newUri = UriUtil.withPath(request.getUri(), path);
        }
        return request.withRequestLine(new RequestLine(
                request.getMethod(), newUri, request.getStartLine().getHttpVersion()));
    }

    /**
     * Returns true if the given status code indicates a HTTP redirection.
     *
     * @param statusCode HTTP status code
     * @return true if this is a redirection status code, false otherwise
     */
    public static boolean isRedirectCode(int statusCode) {
        switch (statusCode) {
            case 300:
            case 301:
            case 302:
            case 303:
            case 307:
            case 308:
                return true;
            default:
                return false;
        }
    }

}
