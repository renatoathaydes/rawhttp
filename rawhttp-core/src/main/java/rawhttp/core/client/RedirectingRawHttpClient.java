package rawhttp.core.client;

import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.RequestLine;
import rawhttp.core.UriUtil;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link RawHttpClient} that wraps another {@link RawHttpClient}, enhancing it with the ability to follow redirects.
 *
 * @param <Response>
 */
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
            if (response.getStartLine().isRedirect()) {
                response = response.eagerly(); // consume body
                totalRedirects++;
                if (totalRedirects >= maxRedirects) {
                    throw new IllegalStateException("Too many redirects (" + totalRedirects + ")");
                }
                String location = response.getHeaders().getFirst("Location").orElse("");
                if (location.isEmpty()) {
                    break; // cannot follow redirect automatically
                } else {
                    if (!locations.add(location)) {
                        throw new IllegalStateException("Redirect cycle detected. " +
                                "Visited locations: " + String.join(", ", locations) + ". Next location: " + location);
                    }
                    request = redirect(request, location, response.getStatusCode());
                }
            } else {
                break; // not a redirect
            }
        }
        return response;
    }

    private RawHttpRequest redirect(RawHttpRequest request, String location, int statusCode) {
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
        String method;
        if (statusCode == 303 && !request.getMethod().equals("HEAD")) {
            method = "GET";
        } else {
            method = request.getMethod();
        }
        return request.withRequestLine(new RequestLine(
                method, newUri, request.getStartLine().getHttpVersion()));
    }

}
