package rawhttp.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * A {@link RawHttpRequest}'s start-line.
 */
public class RequestLine implements StartLine {

    private final String method;
    private final URI uri;
    private final HttpVersion httpVersion;

    /**
     * Create a new {@link RequestLine}.
     *
     * This constructor does not validate the method name. If validation is required,
     * use the {@link HttpMetadataParser#parseRequestLine(java.io.InputStream)} method.
     * @param method
     * @param uri
     * @param httpVersion
     */
    public RequestLine(String method, URI uri, HttpVersion httpVersion) {
        this.method = method;
        this.uri = uri;
        this.httpVersion = httpVersion;
    }

    /**
     * @return HTTP request's method name.
     */
    public String getMethod() {
        return method;
    }

    /**
     * @return the URI associated with this method line.
     */
    public URI getUri() {
        return uri;
    }

    @Override
    public HttpVersion getHttpVersion() {
        return httpVersion;
    }

    /**
     * @param host the host to be used in the method line's URI.
     * @return a copy of this method line, but with the given host
     */
    public RequestLine withHost(String host) {
        try {
            if (!host.matches("[a-z]{1,6}://.*")) {
                host = "http://" + host;
            }
            URI hostURI = URI.create(host);
            URI newURI = new URI(hostURI.getScheme(),
                    hostURI.getUserInfo(),
                    hostURI.getHost(),
                    hostURI.getPort(),
                    uri.getPath(), uri.getQuery(), uri.getFragment());
            return new RequestLine(method, newURI, httpVersion);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid host format" + Optional.ofNullable(
                    e.getMessage()).map(s -> ": " + s).orElse(""));
        }
    }

    /**
     * @return the start-line for this method line.
     */
    @Override
    public String toString() {
        URI pathURI;
        String path = uri.getPath();
        if (path == null || path.trim().isEmpty()) {
            path = "/";
        }
        try {
            // only path and query are sent to the server
            pathURI = new URI(null, null, null, -1, path, uri.getQuery(), null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        return method + " " + pathURI + " " + httpVersion;
    }
}