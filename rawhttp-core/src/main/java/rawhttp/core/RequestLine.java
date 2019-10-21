package rawhttp.core;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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
     * <p>
     * This constructor does not validate the method name. If validation is required,
     * use the {@link HttpMetadataParser#parseRequestLine(java.io.InputStream)} method.
     *
     * @param method      name of the HTTP method
     * @param uri         URI of the request target
     * @param httpVersion HTTP version of the message
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
            URI hostURI = new URI(host);
            StringBuilder sb = new StringBuilder(hostURI.toString());
            if (uri.getRawPath() != null)
                sb.append(uri.getRawPath());
            if (uri.getRawQuery() != null) {
                sb.append('?');
                sb.append(uri.getRawQuery());
            }
            if (uri.getRawFragment() != null) {
                sb.append('#');
                sb.append(uri.getRawFragment());
            }
            URI newURI = new URI(sb.toString());
            return new RequestLine(method, newURI, httpVersion);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid host format" + Optional.ofNullable(
                    e.getMessage()).map(s -> ": " + s).orElse(""));
        }
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException {
        byte[] bytes = toString().getBytes(StandardCharsets.US_ASCII);
        outputStream.write(bytes);
        outputStream.write('\r');
        outputStream.write('\n');
    }

    /**
     * @return the start-line for this method line.
     */
    @Override
    public String toString() {
        String path = uri.getRawPath();
        if (path == null || path.trim().isEmpty()) {
            path = "/";
        }
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }

        return method + " " + path + " " + httpVersion;
    }
}