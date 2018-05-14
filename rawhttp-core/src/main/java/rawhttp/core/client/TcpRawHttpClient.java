package rawhttp.core.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;
import rawhttp.core.HttpVersion;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpOptions;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

/**
 * Simple implementation of {@link RawHttpClient} based on TCP {@link Socket}s.
 */
public class TcpRawHttpClient implements RawHttpClient<Void>, Closeable {

    private final TcpRawHttpClientOptions options;
    private final RawHttp rawHttp;

    /**
     * Create a new {@link TcpRawHttpClient} using {@link DefaultOptions}.
     */
    public TcpRawHttpClient() {
        this(null);
    }

    /**
     * Create a new {@link TcpRawHttpClient} using the given options, which can give a custom
     * socketProvider and onClose callback.
     * <p>
     * If no options are given, {@link DefaultOptions} is used.
     *
     * @param options configuration for this client
     */
    public TcpRawHttpClient(@Nullable TcpRawHttpClientOptions options) {
        this(options == null ? new DefaultOptions() : options,
                new RawHttp(RawHttpOptions.newBuilder()
                        .doNotAllowNewLineWithoutReturn()
                        .build()));
    }

    /**
     * Create a new {@link TcpRawHttpClient} using the given options, which can give a custom
     * socketProvider and onClose callback.
     * <p>
     * If no options are given, {@link DefaultOptions} is used.
     *
     * @param options configuration for this client
     * @param rawHttp http instance to use to send out requests
     */
    public TcpRawHttpClient(@Nullable TcpRawHttpClientOptions options,
                            RawHttp rawHttp) {
        this.options = options == null ? new DefaultOptions() : options;
        this.rawHttp = rawHttp;
    }

    @Override
    public RawHttpResponse<Void> send(RawHttpRequest request) throws IOException {
        Socket socket;
        try {
            socket = options.getSocket(request.getUri());
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw e;
        }
        request.writeTo(socket.getOutputStream());
        return options.onResponse(socket, request.getUri(),
                rawHttp.parseResponse(socket.getInputStream()));
    }

    @Override
    public void close() throws IOException {
        options.close();
    }

    /**
     * Configuration options for {@link TcpRawHttpClient}.
     */
    public interface TcpRawHttpClientOptions extends Closeable {

        /**
         * @param uri the URI to connect the Socket to
         * @return socket provider function that, given a request's {@link URI},
         * provides a {@link Socket} that can be used to send out the request.
         */
        Socket getSocket(URI uri);

        /**
         * Callback that will be called every time the HTTP client receives a response.
         * <p>
         * It may be used to perform maintenance (such as calling {@link RawHttpResponse#eagerly()}
         * then closing the connection), or transform the provided HTTP response, returning a modified one.
         *
         * @param socket       the socket used to send out a HTTP request. This socket is always
         *                     the same as provided by a previous call to {@link #getSocket(URI)}.
         * @param uri          used to make the HTTP request
         * @param httpResponse the HTTP response received from the server
         * @return a possibly transformed httpResponse
         * @throws IOException if any communication problem occurs
         */
        RawHttpResponse<Void> onResponse(
                Socket socket, URI uri, RawHttpResponse<Void> httpResponse) throws IOException;

        @Override
        default void close() throws IOException {
        }

    }

    /**
     * The default Socket provider used by {@link TcpRawHttpClient}.
     * <p>
     * When resolving a {@link Socket} for a {@link URI}, unless the port is present
     * in the {@link URI}, port 80 will be used for "http" requests,
     * and port 43 for "https" requests.
     * <p>
     * Sockets are re-used if possible (following the {@code Connection} header as described
     * in <a href="https://tools.ietf.org/html/rfc7230#section-6.3">Section 6.3</a> of RFC-7230).
     */
    public static class DefaultOptions implements TcpRawHttpClientOptions {

        private final Map<String, Socket> socketByHost = new HashMap<>(4);

        @Override
        public Socket getSocket(URI uri) {
            String host = Optional.ofNullable(uri.getHost()).orElseThrow(() ->
                    new RuntimeException("Host is not available in the URI"));

            @Nullable Socket socket = socketByHost.get(host);

            if (socket == null || socket.isClosed() || !socket.isConnected()) {
                boolean useHttps = "https".equalsIgnoreCase(uri.getScheme());
                int port = uri.getPort();
                if (port < 1) {
                    port = useHttps ? 443 : 80;
                }
                try {
                    socket = useHttps
                            ? SSLSocketFactory.getDefault().createSocket(host, port)
                            : new Socket(host, port);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                socketByHost.put(host, socket);
            }

            return socket;
        }

        @Override
        public RawHttpResponse<Void> onResponse(Socket socket,
                                                URI uri,
                                                RawHttpResponse<Void> httpResponse) throws IOException {
            if (httpResponse.getHeaders()
                    .getFirst("Connection")
                    .orElse("")
                    .equalsIgnoreCase("close") ||
                    httpResponse.getStartLine().getHttpVersion().isOlderThan(HttpVersion.HTTP_1_1)) {

                socketByHost.remove(uri.getHost());

                // resolve the full response before closing the socket
                return httpResponse.eagerly(false);
            }

            return httpResponse;
        }

        @Override
        public void close() {
            for (Socket socket : socketByHost.values()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}
